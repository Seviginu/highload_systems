# ЛР4, этап 3: Kafka-кластер

В Compose добавлены `kafka-1`, `kafka-2`, `kafka-3` и одноразовый `kafka-init`. Каждый Kafka-узел совмещает роли broker/controller в KRaft; quorum состоит из трех контроллеров. У узлов отдельные именованные volumes `kafka-1-data`, `kafka-2-data`, `kafka-3-data`.

Используется официальный образ `apache/kafka:3.9.2`, проверенный на arm64. При проверке получен digest `sha256:05b4616e0702ef2729327705d54ad6b50ea70b271c4b730fabd2320789fb7b02`. Версия образа закреплена тегом в Compose. [Официальные образы и версии](https://kafka.apache.org/downloads/), [пример combined KRaft от Apache](https://github.com/apache/kafka/blob/3.9.2/docker/examples/docker-compose-files/cluster/combined/plaintext/docker-compose.yml).

## Топология и настройки

| Топик | Partitions | Replication factor | min.insync.replicas |
|---|---:|---:|---:|
| `tracker.task.events.v1` | 3 | 3 | 2 |
| `tracker.project.events.v1` | 3 | 3 | 2 |
| `files.events.v1` | 3 | 3 | 2 |
| `tracker.task.events.v1.dlt` | 3 | 3 | 2 |
| `tracker.project.events.v1.dlt` | 3 | 3 | 2 |
| `files.events.v1.dlt` | 3 | 3 | 2 |

Автоматическое создание топиков и unclean leader election выключены. Внутренние offset/transaction-топики настроены с RF=3, для transaction log задан min ISR=2. В smoke создавался и проверялся `__consumer_offsets`; transaction log еще не используется приложением.

`infra/kafka/init-topics.sh` создает все шесть топиков после healthy-состояния трех брокеров. Повторный запуск использует `--if-not-exists` и проверяет фактические partitions/RF/min ISR. При несовпадении конфигурации существующего топика init завершается ошибкой. Топики и данные автоматически не удаляются и не переразмечаются.

В общей конфигурации Config Server подготовлены:

- bootstrap servers `kafka-1:9092,kafka-2:9092,kafka-3:9092`;
- producer `acks=all`, `enable.idempotence=true`;
- consumer `enable-auto-commit=false`, `auto-offset-reset=earliest`;
- listener `ack-mode=record`, admin `fail-fast=false`.

Эти настройки начнут использоваться бизнес-сервисами после подключения Spring Kafka и обработчиков в следующих этапах. На текущем этапе приложение еще не публикует бизнес-события. `acks=all` совместно с min ISR=2 требует достаточного количества синхронных реплик для успешной записи; [описание настройки Apache Kafka](https://kafka.apache.org/39/configuration/broker-configs/).

Внутри Compose применяется PLAINTEXT. Kafka-порты на хост не публикуются. CLI доступен через `docker compose exec`; сервисы обращаются к брокерам через Compose network.

## Запуск

Полный запуск приложения:

```bash
./scripts/docker-up.sh
```

Скрипт дополнительно ожидает завершения `kafka-init` и проверяет его exit code. Для одноразового сервиса без healthcheck одного `up --wait` недостаточно, чтобы подтвердить завершение инициализации; это проверено на установленном Docker Compose v5.5.1.

Только Kafka, без сборки и запуска бизнес-сервисов:

```bash
docker compose up -d --wait --wait-timeout 180 kafka-1 kafka-2 kafka-3 kafka-init
docker wait "$(docker compose ps -aq kafka-init)"
docker compose logs kafka-init
```

`docker wait` должен вывести `0`. Проверить описание топиков:

```bash
docker compose exec kafka-1 /opt/kafka/bin/kafka-topics.sh \
  --bootstrap-server kafka-1:9092 --describe
```

Переменные `.env.example`: `KAFKA_CLUSTER_ID` — общий идентификатор кластера, `KAFKA_HEAP_OPTS` — heap каждого брокера (по умолчанию 256–512 MiB), `KAFKA_BOOTSTRAP_SERVERS` — адреса для бизнес-клиентов внутри Docker. При сохранении volumes идентификатор кластера должен оставаться прежним. Менять адреса брокеров, node IDs и quorum в работающем кластере этот этап не предусматривает.

## Изолированная проверка

```bash
./scripts/kafka-smoke.sh
```

Скрипт сам создает проект с уникальным именем `lab4-kafka-smoke-*`, использует `--env-file /dev/null`, запускает только Kafka и init. На выходе удаляет контейнеры, сеть и volumes этого проекта. Рабочий Compose-проект не используется. CLI-процессы и ожидание ISR имеют ограниченные таймауты; диагностические логи остаются во временном каталоге, путь печатается в конце.

Проверяемые шаги:

1. Запустить три брокера, дождаться init и успешно выполнить его повторно.
2. Подтвердить для всех шести топиков RF=3 и ISR=3.
3. Записать первое сообщение с `acks=all` и idempotence, прочитать через consumer group. Это также создает `__consumer_offsets` с тремя репликами.
4. Определить партицию записанного сообщения и ее фактического лидера, остановить этот брокер.
5. Дождаться доступных лидеров и ISR=2 у всех партиций, записать второе сообщение и прочитать оба сообщения через новую consumer group.
6. Пересоздать остановленный контейнер с сохраненным volume, дождаться ISR=3, записать третье сообщение.
7. Пересоздать все три брокера на сохраненных volumes, снова дождаться ISR=3 и прочитать все три сообщения в исходном порядке.

Результат прогона 06.11.2026:

| Проверка | Результат |
|---|---|
| Повторная инициализация | Успешно, все 6 топиков сохранили настройки |
| Здоровый кластер | RF=3, ISR=3 |
| Остановка лидера | Остановлен kafka-1, лидер task partition 1; ISR=2 |
| Работа на двух брокерах | Producer `acks=all` и grouped consume успешны |
| Возврат брокера | ISR=3 |
| Пересоздание всех Kafka-контейнеров | Все 3 сообщения прочитаны в исходном порядке |
| Очистка | Тестовые контейнеры и volumes удалены |

Итог скрипта: `PASS: six topics, repeated init, leader failover, grouped consume, ISR recovery and volume persistence`.

Проверка выполнена на одном Docker-host и использует остановку контейнера лидера. Отказ физического хоста и одновременная потеря двух брокеров в этом этапе не проверялись. Полный запуск бизнес-сервисов через Compose остается отдельной проверкой этапов 7–8.
