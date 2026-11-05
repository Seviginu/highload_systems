# ЛР4, этап 6: file-service

Сервис хранит файл, метаданные и событие загрузки в одной PostgreSQL-транзакции. `FileUploaded` доставляется через `files.events.v1` в notification-service; подтверждение получает загрузивший пользователь. Повторная доставка eventId не создает второго уведомления.

## Архитектура и хранение

WebFlux/JPA выбран для переиспользования общего JWT security-common. HTTP-адаптер передает простого Actor в независимый application; блокирующие JPA-операции выполняются на file-blocking (4 worker, очередь 100). Domain/application зависят только от Java и собственных внутренних слоев; ArchUnit проверяет направление зависимостей.

Собственная БД files: `files` хранит UUID, owner_id, name, content_type, size, created_at; `file_contents` — UUID/FK и bytes (`bytea`). Метаданные выбираются без binary. Это небольшой файловый сервис для учебной нагрузки, с ограниченной буферизацией, а не потоковое хранилище больших файлов.

Upload выполняется через UnitOfWork: метаданные → байты → outbox, затем commit. Ошибка любого шага откатывает все записи. Outbox publisher работает вне HTTP-запроса, помечает опубликованным после Kafka ack и повторяет при сбое. Гарантия at-least-once, один publisher на сервис, сохранен порядок; старые опубликованные записи чистятся, pending сохраняется. Политика повторов/DLT/inbox описана в [уведомлениях](LAB4_NOTIFICATIONS.md).

Event-contracts содержит FileUploadedEvent v1. AggregateId — UUID файла, aggregateVersion=null, actorId/recipientIds — владелец, payload — только размер. Имя, содержимое, токены и пароли в Kafka не передаются. Notification resourceType=File, resourceId — строковый UUID; уведомление самому загрузившему сохраняется.

## API

Все операции доступны через Gateway и требуют Bearer JWT. Любая роль может загружать файл; получать метаданные и содержимое может владелец либо ADMIN. OwnerId определяется по sub JWT. Другой пользователь получает 403; неизвестный UUID — 404; отсутствие/ошибка JWT — 401.

```bash
BASE_URL=http://localhost:8081
curl -i -H "Authorization: Bearer $TOKEN" -F 'file=@./sample.bin' $BASE_URL/api/v1/files
curl -H "Authorization: Bearer $TOKEN" $BASE_URL/api/v1/files/$FILE_ID
curl -H "Authorization: Bearer $TOKEN" $BASE_URL/api/v1/files/$FILE_ID/content -o downloaded.bin
cmp sample.bin downloaded.bin
```

POST возвращает 201, Location=/api/v1/files/{UUID} и JSON метаданных. GET /{id} возвращает только метаданные. GET /{id}/content возвращает точные байты, размер и Content-Type, `Content-Disposition: attachment` с UTF-8 filename, `X-Content-Type-Options: nosniff`. Из имени убирается путь и управляющие символы, оно ограничено 255 символами UTF-16 без разрыва surrogate pair; пустое имя заменяется на file. Неопределенный/wildcard MIME заменяется application/octet-stream.

Пустой файл/отсутствующий part/неправильный UUID/multipart — 400, превышение лимита — 413. Ошибки имеют общий JSON-формат timestamp/status/error/message/path/fieldErrors; ошибки БД не раскрывают SQL в ответе. Обновление, удаление и список файлов в API этапа 6 не входят.

## Лимиты и конфигурация

FILE_MAX_BYTES — максимальный размер бинарного файла, по умолчанию 10485760 (10 MiB). Допустимо 1–52428800 байт. Одна переменная задает лимит в Gateway, file-service, multipart decoder и application. У raw multipart-запроса есть дополнительные 65536 байт на оболочку; проверяется и Content-Length, и фактически прочитанный поток. Chunked-запросы не обходят ограничение. Backend отдельно проверяет размер binary; файл, превышающий лимит даже на один байт, не сохраняется.

Gateway ограниченно собирает тело WebFilter-ом. В backend HttpHandlerDecoratorFactory делает это до создания ServerWebExchange: multipart decoder получает повторно читаемый ограниченный поток, вместо уже прочитанного Netty body. Затем decoder разбирает единственный part. Это требует памяти в пределах лимита на запрос. Parser имеет max-parts=1, max-headers-size=8KB, max-in-memory-size=64KB; более крупные части могут временно попасть на диск. FilePart удаляется через usingWhen после успеха/ошибки/отмены; DataBuffer освобождается после чтения. Лимит запроса не означает общий лимит памяти всех одновременных загрузок.

FILE_DB_URL/FILE_DB_USERNAME/FILE_DB_PASSWORD задают отдельную БД, в Compose используется files-db и постоянный files-data. JWT — только публичный RSA-ключ. Сервис регистрируется в Eureka, читает Config Server; общий Swagger содержит четвертую схему file-service. Dockerfile собирает модуль тем же SERVICE_MODULE механизмом. `scripts/docker-up.sh` проверяет все четыре OpenAPI-маршрута.

## Проверки

`mvn clean verify` на JDK 21 с Docker запускает ArchUnit, unit и PostgreSQL/Kafka Testcontainers. Проверяются bytes/metadata/outbox и rollback, роли/ADMIN/владелец, валидация, chunked и размер, схема OpenAPI, FileUploaded и дедупликация. Тесты file-service/Gateway используют настоящий Netty HTTP-сервер и разделенный на несколько буферов multipart; проверка только через mock HTTP недостаточна для повторного чтения тела.

`python3 scripts/notifications-smoke.py` создает отдельный Compose-проект и ключи, строит все бизнес-сервисы, проверяет реальные JWT/Feign/Kafka и четыре OpenAPI через Gateway. Затем загружает ровно 10 MiB chunked, сравнивает скачанное побайтово, проверяет владельца/ADMIN/чужого пользователя, пустой/слишком большой файл и полный запрос без Content-Length, уведомление и сохранность после пересоздания files-db/file-service на прежнем volume. Тестовые ресурсы удаляются в finally; пользовательский стенд не используется.

Результаты 06.11.2026, JDK 21.0.10:

- Полный `mvn clean verify` — **265 тестов, 0 failures/errors/skipped**, BUILD SUCCESS; все настроенные JaCoCo gates ≥70% пройдены. File-service — **15 тестов, 92,56% строк**; Gateway — 4 теста, 92,42%; notification-service — 12 тестов, 96,76%; event-contracts — 5 тестов, 100%.
- Реальный изолированный Docker smoke — **PASS**: четыре OpenAPI, четыре типа уведомлений, полный stop/start Kafka и DLT; файл ровно 10 MiB chunked загружен и скачан побайтово; владельцу/ADMIN разрешено, чужому пользователю запрещено; 400/401/403/413, file limit +1 байт с Content-Length и без него, raw request limit; FileUploaded дошел пользователю.
- Пересоздание files-db и file-service без удаления files-data сохранило точные байты файла. Временные контейнеры, сеть, ключи, volumes и образы удалены; рабочий стенд не использовался.
- При реализации выявлено повторное чтение исходного Netty body после WebFilter; исправлено переносом backend guard в HttpHandlerDecoratorFactory. Регрессионные тесты file/Gateway идут через реальные сокеты, а не только mock HTTP; поврежденный multipart возвращает 400.

Текущее выполнение — в [плане](../IMPLEMENTATION_PLAN_LAB4.md).

Источники: [Spring DataBufferUtils: bounded join и освобождение буферов](https://docs.spring.io/spring-framework/docs/6.0.11/javadoc-api/org/springframework/core/io/buffer/DataBufferUtils.html), [Spring Boot: ReactiveMultipartProperties](https://docs.spring.io/spring-boot/3.5/api/java/org/springframework/boot/autoconfigure/web/reactive/ReactiveMultipartProperties.html).
