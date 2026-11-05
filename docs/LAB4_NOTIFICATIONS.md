# ЛР4, этапы 4–5: события трекера и уведомления

Сквозной путь: `tracker-service` сохраняет задачу и outbox в одной транзакции → publisher отправляет `TaskAssigned` в `tracker.task.events.v1` → `notification-service` сохраняет уведомление и обработанный `eventId` → пользователь читает уведомление через Gateway.

## Границы сервисов

`event-contracts` — небольшая Maven-библиотека с Java-записями `TaskAssignedEvent`, `TaskStatusChangedEvent`, `ProjectMemberAddedEvent`. В ней нет Spring, JPA, KafkaTemplate или бизнес-сервисов. Контракт содержит eventId, eventType, schemaVersion, occurredAt, aggregateId, aggregateVersion, actorId, recipientIds и краткий payload соответствующего типа (ключ задачи, статусы или ID участника/название проекта). Пароль, хэш, JWT, Authorization и текст задачи в событие не входят.

Трекер зависит от портов `TaskAssignments`, `TaskStatusChanges`, `ProjectMemberships` и внутренних записей application. Преобразование в транспортный контракт, UUID/время, JSON и запись outbox находятся во внешнем адаптере. Событие создается после получения ID/версии сохраненной задачи, до commit той же транзакции.

`notification-service` имеет собственную PostgreSQL, Liquibase и слои domain/application/adapter/configuration. Выбран WebFlux/JPA, чтобы использовать существующий реактивный `security-common` с RS256, issuer/audience и проверкой claims. JPA в HTTP выполняется на ограниченном `notification-blocking` scheduler. Kafka listener выполняет синхронный сценарий на consumer thread и возвращается после commit БД.

Новый сервис зарегистрирован в Eureka, получает конфигурацию из Config Server, доступен через Gateway. OpenAPI включен в общий Swagger как третий сервис. Приватный RSA-ключ остается только в user-service; уведомлениям нужен публичный ключ.

## Когда создается TaskAssigned

- Создание задачи с исполнителем.
- Реальное изменение исполнителя через PUT или перенос задачи.
- При прежнем исполнителе, снятии исполнителя и назначении самому инициатору событие не создается.
- При отказе доступа, конфликте версии или rollback событие не должно оставаться в outbox. Ошибка вставки outbox откатывает и задачу.

Права на назначение и проверка активного членства сохранены. Feign-проверка пользователей выполняется до локальной транзакции.

## Остальные события

`TaskStatusChanged` создается при реальной смене статуса через PATCH и PUT. Получатель — автор задачи, если он отличается от инициатора. Начальный статус при создании задачи и прежнее значение события не создают. Payload содержит taskKey, previousStatus, status; aggregateVersion — версия сохраненной задачи.

`ProjectMemberAdded` уведомляет добавленного или повторно активированного участника. Автоматическое членство тимлида при создании проекта также создает событие. Для членства уведомление самому инициатору сохраняется. Активный дубликат дает 409; деактивация события не создает. Payload содержит memberId и projectName; aggregateId — projectId, aggregateVersion=null (проект не версионируется).

Все три события записываются в outbox в транзакции бизнес-операции. Consumer проверяет тип, версию схемы и соответствие исходному топику. Для двух типов задач используется `tracker.task.events.v1`, для членства — `tracker.project.events.v1`. Group-id `notifications-task-assigned-v1` сохранен для продолжения обработки со старых offsets.

## Outbox и обработка

Миграция `004-create-outbox.sql` добавляет `outbox_events` без изменения старых changeset. У записи есть UUID события, topic, ключ агрегата, JSON, время, publishedAt, attempts, nextAttemptAt и lastError. Частичный индекс ускоряет выборку ожидающих записей.

Publisher берет до 50 ожидающих записей, отправляет их последовательно с ключом ID задачи/проекта и помечает опубликованными только после подтверждения Kafka. Сетевой вызов выполняется вне транзакции БД. При ошибке отправки или фиксации результата запись остается ожидающей, попытка откладывается на 5 секунд; batch останавливается. Отложенная ранняя запись не пропускается. Предполагается один publisher-процесс трекера.

Остановка процесса между отправкой и фиксацией publishedAt может привести к повторной доставке: гарантия — at-least-once. В notification-db `INSERT ... ON CONFLICT DO NOTHING` в `processed_events` атомарно определяет первый прием eventId. Маркер и все уведомления сохраняются одной транзакцией; ошибка уведомления откатывает и маркер. Уникальность `(event_id, recipient_id)` дополнительно защищает от дублей.

Consumer использует StringDeserializer и проверяемый Java-контракт, auto commit выключен, ack-mode=record. Offset подтверждается после успешного возврата сценария, уже после commit БД. В этапе 5 настроены первоначальная попытка и два повтора с задержкой 1000 мс, затем публикация исходного сообщения в `<source-topic>.dlt` в ту же партицию. Публикация ожидает Kafka ack; при ошибке recoverer выбрасывает исключение и исходный offset не подтверждается. Следующий цикл снова пытается обработать сообщение. Все ошибки, включая неподдерживаемый тип/версию и временную ошибку БД, проходят этот ограниченный цикл. DLT не потребляется автоматически: после устранения причины требуется контролируемый повтор с сохранением eventId. DLT сохраняет исходный payload/key и координаты сообщения; message/stack trace исключения не включаются в заголовки. KafkaTemplate имеет ограниченные max.block/request/delivery таймауты.

Настройки Kafka наследуются из общего Config Server. Outbox scheduler можно отключить свойством `app.outbox.enabled=false`; именно так обычные интеграционные тесты изолируются от фоновой отправки. По умолчанию publisher включен, интервал опроса — 1000 мс, batch — 50. Журнал publisher сохраняет ID события и класс ошибки, без тела сообщения или токена.

Очистка outbox выполняется раз в час: до 1000 опубликованных записей старше 7 дней. Свойства: `app.outbox.retention-days`, `cleanup-batch`, `cleanup-interval`. Pending-записи и processed_events не удаляются. Миграция 005 добавляет индекс для опубликованных записей. Повторы consumer настраиваются `app.notifications.max-retries` (по умолчанию 2, диапазон 0–10) и `retry-delay` (мс, по умолчанию 1000).

## API

Все запросы — через Gateway с `Authorization: Bearer <JWT>`.

```http
GET /api/v1/notifications?page=0&size=20
```

Возвращает JSON-массив своих уведомлений и `X-Total-Count`. Размер страницы — 1–50, сортировка — createdAt DESC, id DESC. Получатель определяется по sub JWT; query/body не задают другого пользователя. ADMIN также читает только свои уведомления.

```http
PATCH /api/v1/notifications/{id}/read
```

Успех — 204. Повторный PATCH сохраняет первое readAt. Отсутствующее или чужое уведомление — 404; без JWT — 401; неправильная пагинация/UUID — 400. Ошибки имеют общий формат timestamp/status/error/message/path/fieldErrors.

Уведомление содержит id, eventId, recipientId, type (`TaskAssigned`, `TaskStatusChanged`, `ProjectMemberAdded`), message, resourceType (`Task`/`Project`), строковый resourceId, createdAt и readAt. Строковый resourceId позволяет использовать тот же API для будущих UUID файлов. JPA-модели уведомления и outbox имеют Bean Validation; ограничения БД сохраняются дополнительно. Непрочитанное уведомление имеет readAt=null. Публичного API создания уведомления нет.

## Проверка

На JDK 21 с доступным Docker:

```bash
mvn clean verify
```

JUnit Jupiter/Testcontainers проверяют запись outbox вместе с задачей и rollback при ошибке outbox, правила переназначения/самоназначения, порядок и повтор publisher, реальное чтение Kafka consumer, дедупликацию до commit offset, владельца/JWT, readAt, пагинацию, rollback inbox и ArchUnit.

Изолированный сквозной тест реальных сервисов:

```bash
python3 scripts/notifications-smoke.py
```

Нужны Docker Compose с поддержкой `!override`/`!reset`, Python 3, OpenSSL и доступ к образам/Maven-зависимостям. Скрипт создает отдельный Compose-проект, временные RSA-ключи, БД и volumes, публикует только случайный localhost-порт Gateway. Строит Docker-образы и проверяет реальные login/JWT, Feign, Kafka, три OpenAPI-маршрута, назначение/переназначение, доступ владельца, повторный PATCH и восстановление доставки после остановки/запуска notification-service. Тестовые пользователи и токены принадлежат этому временному стенду. В finally проект, его volumes и локальные образы удаляются; путь диагностических логов печатается в конце.

Результаты этапа 4, 06.11.2026 (исторический прогон):

- Полный `mvn clean verify`: **237 тестов, 0 failures/errors/skipped**; все настроенные JaCoCo gates ≥70% пройдены.
- notification-service: 9 тестов, покрытие строк **97,14%**; tracker-service: 167 тестов, **94,85%**; event-contracts: 2 теста, **100%**.
- `scripts/notifications-smoke.py`: **PASS** на реальном Compose-стенде с тремя Kafka-брокерами, Gateway, Eureka, Config Server, user-service, tracker-service, notification-service и отдельными БД. Проверены все перечисленные сценарии, включая восстановление доставки после рестарта consumer.
- Временные контейнеры, сеть, volumes, ключи и образы удалены. Рабочий стенд не использовался.

В сквозном тесте также проверен порядок запуска одноразового kafka-init: создание сервисов, ожидание exit(0), затем `--wait` только для долгоживущих сервисов. Gateway явно зависит от healthy notification-service, поэтому при запуске через Gateway Compose включает новый backend.

Текущее состояние и следующие шаги — в [плане](../IMPLEMENTATION_PLAN_LAB4.md).

Результат этапа 5, 06.11.2026: полный `mvn clean verify` — **246 тестов, 0 failures/errors/skipped**, все gates ≥70% пройдены. notification-service — 11 тестов, 96,60% строк; tracker-service — 172 теста, 94,78%; event-contracts — 4 теста, 100%.

В этапе 5 добавлены проверки обоих новых типов через реальную Kafka, дедупликации, отказа публикации DLT с проверкой исходного offset, rollback членства и очистки только старых опубликованных записей. Smoke дополнительно проверяет смену статуса через PATCH/PUT, членство, полный простой Kafka, доставку после восстановления и реплицированный DLT.

Изолированный smoke этапа 5 — **PASS**: три типа событий, consumer restart, stop/start всех трех брокеров с сохранением pending outbox и автоматическим восстановлением доставки без рестарта приложений, DLT и следующее корректное событие. Тестовые ресурсы удалены. Проверка выявила проблему повторного использования Docker IP: [внутренние порты брокеров разделены](LAB4_KAFKA.md#уточнение-этапа-5-восстановление-после-полного-простоя). При обновлении существующего `.env` сверить KAFKA_BOOTSTRAP_SERVERS с `.env.example`.

FileUploaded и file-service остаются этапом 6. Масштабирование publisher не входит в минимальный объем.

Технические источники: [Spring Kafka 3.3: обработка ошибок и retries](https://docs.spring.io/spring-kafka/reference/3.3/kafka/annotation-error-handling.html), [Kafka Testcontainers](https://java.testcontainers.org/modules/kafka/).
