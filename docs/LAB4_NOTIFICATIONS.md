# ЛР4, этап 4: назначение задачи и уведомление

Сквозной путь: `tracker-service` сохраняет задачу и outbox в одной транзакции → publisher отправляет `TaskAssigned` в `tracker.task.events.v1` → `notification-service` сохраняет уведомление и обработанный `eventId` → пользователь читает уведомление через Gateway.

## Границы сервисов

`event-contracts` — небольшая Maven-библиотека только с Java-записью `TaskAssignedEvent`. В ней нет Spring, JPA, KafkaTemplate или бизнес-сервисов. Контракт содержит eventId, eventType, schemaVersion, occurredAt, aggregateId, aggregateVersion, actorId, recipientIds и payload с taskKey/projectId. Пароль, хэш, JWT, Authorization и текст задачи в событие не входят.

Трекер зависит от порта `TaskAssignments` и внутреннего `TaskAssignment`. Преобразование в транспортный контракт, UUID/время, JSON и запись outbox находятся во внешнем адаптере. Событие создается после получения ID/версии сохраненной задачи, до commit той же транзакции.

`notification-service` имеет собственную PostgreSQL, Liquibase и слои domain/application/adapter/configuration. Выбран WebFlux/JPA, чтобы использовать существующий реактивный `security-common` с RS256, issuer/audience и проверкой claims. JPA в HTTP выполняется на ограниченном `notification-blocking` scheduler. Kafka listener выполняет синхронный сценарий на consumer thread и возвращается после commit БД.

Новый сервис зарегистрирован в Eureka, получает конфигурацию из Config Server, доступен через Gateway. OpenAPI включен в общий Swagger как третий сервис. Приватный RSA-ключ остается только в user-service; уведомлениям нужен публичный ключ.

## Когда создается событие

- Создание задачи с исполнителем.
- Реальное изменение исполнителя через PUT или перенос задачи.
- При прежнем исполнителе, снятии исполнителя и назначении самому инициатору событие не создается.
- При отказе доступа, конфликте версии или rollback событие не должно оставаться в outbox. Ошибка вставки outbox откатывает и задачу.

Права на назначение и проверка активного членства сохранены. Feign-проверка пользователей выполняется до локальной транзакции.

## Outbox и обработка

Миграция `004-create-outbox.sql` добавляет `outbox_events` без изменения старых changeset. У записи есть UUID события, topic, ключ агрегата, JSON, время, publishedAt, attempts, nextAttemptAt и lastError. Частичный индекс ускоряет выборку ожидающих записей.

Publisher берет до 50 ожидающих записей, отправляет их последовательно с ключом taskId и помечает опубликованными только после подтверждения Kafka. Сетевой вызов выполняется вне транзакции БД. При ошибке отправки или фиксации результата запись остается ожидающей, попытка откладывается на 5 секунд; batch останавливается. Отложенная ранняя запись не пропускается. Предполагается один publisher-процесс трекера.

Остановка процесса между отправкой и фиксацией publishedAt может привести к повторной доставке: гарантия — at-least-once. В notification-db `INSERT ... ON CONFLICT DO NOTHING` в `processed_events` атомарно определяет первый прием eventId. Маркер и все уведомления сохраняются одной транзакцией; ошибка уведомления откатывает и маркер. Уникальность `(event_id, recipient_id)` дополнительно защищает от дублей.

Consumer использует StringDeserializer и проверяемый Java-контракт, auto commit выключен, ack-mode=record. Offset подтверждается после успешного возврата сценария, уже после commit БД. Пока в этапе 4 ошибка повторяется с задержкой без пропуска сообщения; DLT и ограниченные повторы запланированы на этап 5. Некорректное сообщение до этого этапа удерживает свою партицию.

Настройки Kafka наследуются из общего Config Server. Outbox scheduler можно отключить свойством `app.outbox.enabled=false`; именно так обычные интеграционные тесты изолируются от фоновой отправки. По умолчанию publisher включен, интервал опроса — 1000 мс, batch — 50. Журнал сохраняет ID события и класс ошибки, без тела сообщения или токена.

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

Уведомление содержит id, eventId, recipientId, type=`TaskAssigned`, message, resourceType=`Task`, строковый resourceId, createdAt и readAt. Строковый resourceId позволяет использовать тот же API для будущих UUID файлов. JPA-модели уведомления и outbox имеют Bean Validation; ограничения БД сохраняются дополнительно. Непрочитанное уведомление имеет readAt=null. Публичного API создания уведомления нет.

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

Результаты 06.11.2026:

- Полный `mvn clean verify`: **237 тестов, 0 failures/errors/skipped**; все настроенные JaCoCo gates ≥70% пройдены.
- notification-service: 9 тестов, покрытие строк **97,14%**; tracker-service: 167 тестов, **94,85%**; event-contracts: 2 теста, **100%**.
- `scripts/notifications-smoke.py`: **PASS** на реальном Compose-стенде с тремя Kafka-брокерами, Gateway, Eureka, Config Server, user-service, tracker-service, notification-service и отдельными БД. Проверены все перечисленные сценарии, включая восстановление доставки после рестарта consumer.
- Временные контейнеры, сеть, volumes, ключи и образы удалены. Рабочий стенд не использовался.

В сквозном тесте также проверен порядок запуска одноразового kafka-init: создание сервисов, ожидание exit(0), затем `--wait` только для долгоживущих сервисов. Gateway явно зависит от healthy notification-service, поэтому при запуске через Gateway Compose включает новый backend.

Текущее состояние и следующие шаги — в [плане](../IMPLEMENTATION_PLAN_LAB4.md).

Обработка TaskStatusChanged, ProjectMemberAdded, FileUploaded, DLT, очистка старых опубликованных outbox-записей и масштабирование publisher относятся к следующим этапам. Сервис сейчас формирует уведомления о назначении задач и предоставляет их через REST.

Технические источники: [Spring Kafka 3.3: обработка ошибок и retries](https://docs.spring.io/spring-kafka/reference/3.3/kafka/annotation-error-handling.html), [Kafka Testcontainers](https://java.testcontainers.org/modules/kafka/).
