# ЛР4, этап 7: общая интеграция

В одном Compose-проекте запускаются семь Spring Boot приложений: четыре бизнес-сервиса, Gateway, Eureka и Config Server; четыре PostgreSQL, Redis, три Kafka-брокера, одноразовый kafka-init, pgAdmin и RedisInsight. Maven содержит также две общие библиотеки security-common и event-contracts. Dockerfile собирает каждое приложение через SERVICE_MODULE, общие библиотеки собираются перед ним.

## Запуск

Скопировать `.env.example` в `.env`, задать свои пароли БД и bootstrap ADMIN при необходимости. Bootstrap по умолчанию выключен. Затем:

```bash
./scripts/docker-up.sh
```

Можно явно выбрать конфигурацию/проект:

```bash
./scripts/docker-up.sh --env-file ./my.env -p lab4
```

Аргументы после имени скрипта передаются как глобальные параметры `docker compose`; пути разрешаются из корня репозитория. Compose сам разрешает переменные, env-файл не исполняется через source. Shell имеет приоритет над env-файлом, далее используются значения из модели Compose. Скрипт не экспортирует свои порты и bootstrap-реквизиты поверх пользовательских. [Правила интерполяции Docker Compose](https://docs.docker.com/compose/how-tos/environment-variables/variable-interpolation/).

JWT_KEY_DIR (default ./.local/jwt) применяется и генератором, и всеми bind mounts. Генератор создает пару только при отсутствии приватного ключа, публичный восстанавливает из него. Приватный ключ получает только user-service, публичный — Gateway и четыре бизнес-сервиса. При повторном старте ключ и данные сохраняются. Для одновременных проектов нужны собственные JWT_KEY_DIR, порты и project name; smoke задает их автоматически.

Скрипт проверяет Compose config, создает ключи, собирает и запускает весь стенд, отдельно ожидает успешного exit(0) kafka-init. Затем `--wait` применяется только к долгоживущим сервисам, после чего проверяются Swagger UI, swagger-config и четыре схемы. STARTUP_TIMEOUT_SECONDS (default 180) применяется к каждой фазе ожидания; положительное целое значение можно задать в env-файле. Сборка Docker и начальный up имеют собственное время выполнения. При ошибке рабочий стенд автоматически не удаляется, доступны обычные compose logs.

Адрес Gateway берется через compose port, поэтому проверки работают также со случайным портом и overrides. По умолчанию Swagger: http://localhost:8081/swagger-ui/index.html. Для прежнего порта 58081: `GATEWAY_PORT=58081 ./scripts/docker-up.sh`. При прямом `docker compose up --build -d` нужно предварительно создать ключи в том же каталоге; startup делает это сам.

## Связи и маршруты

| Сервис | Внутренний порт | Через Gateway |
|---|---:|---|
| user-service | 8080 | /api/v1/users/**, /api/v1/auth/** |
| tracker-service (Compose app) | 8080 | /api/v1/projects/**, /api/v1/tasks/**, /api/v1/labels/** |
| notification-service | 8080 | /api/v1/notifications/** |
| file-service | 8080 | /api/v1/files/** |

Каждому бизнес-сервису соответствует `/v3/api-docs/{service}`; общий selector находится в `/v3/api-docs/swagger-config`. Схемы используют bearerAuth и относительный server `/`, чтобы Try it out работал через Gateway. Все бизнес-сервисы проверяют JWT самостоятельно. Только user-service владеет приватным ключом. Gateway не определяет предметные права владельца файла/уведомления; их проверяют соответствующие application-сценарии.

Eureka получает регистрации Gateway, Config Server и четырех бизнес-сервисов. Gateway использует lb:// маршруты; Feign tracker → user остается синхронным для проверок существования/роли. Для асинхронных уведомлений tracker/file → Kafka → notification используются три основных топика и три DLT с RF=3/minISR=2. Подробности: [Kafka](LAB4_KAFKA.md), [уведомления](LAB4_NOTIFICATIONS.md), [файлы](LAB4_FILES.md).

Пять клиентов (Gateway и четыре бизнес-сервиса) читают собственную конфигурацию и общую application.yml из Config Server. Eureka/Config Server содержат инфраструктурную конфигурацию; пустые domain/application для них не создаются. Для Spring-контейнеров задан stop_grace_period=45s, чтобы обычный Docker stop/recreate оставлял время на graceful shutdown и deregistration. В Eureka могут временно оставаться DOWN/истекшие записи; готовность проверяется по активным UP-экземплярам, а не по числу всех записей. Внутренние слои четырех бизнес-сервисов защищены ArchUnit; инфраструктурные приложения содержат routing/security/configuration.

По умолчанию host-порты: Gateway 8081, tracker 8080, user 8082, Config Server 8888, Eureka 8761, tracker PostgreSQL 5432, Redis 6379. Notification/file и остальные PostgreSQL не публикуют host-порты. Kafka использует внутренние broker 9092/9094/9096 и controller 9093/9095/9097. Служебные UI привязаны к localhost: pgAdmin 5050, RedisInsight 5540. Порты переопределяются окружением/Compose override.

В pgAdmin при новом volume импортируются соединения ко всем четырем PostgreSQL. Старый pgAdmin volume сохраняет ранее созданные настройки. Пароли БД вводятся отдельно; в servers.json они не записываются. RedisInsight настроен на Redis этого Compose-проекта.

## Воспроизводимая проверка

```bash
python3 scripts/notifications-smoke.py
```

Это общий интеграционный smoke, имя сохранено с этапа 4. Нужны Docker/Compose с !reset/!override и config --environment, Bash, curl, OpenSSL, Python 3. Скрипт создает изолированный проект, временный env-файл/ключи, свои БД/volumes и случайные localhost-порты Gateway/UI. Остальные host-порты сбрасываются. Рабочая `.env` не используется.

Проверка вызывает реальный startup дважды: первая загрузка создает ADMIN/ключ, вторая передает BOOTSTRAP_ADMIN_ENABLED=false, проверяет значение в контейнере и неизменность приватного ключа; существующий ADMIN продолжает входить. Далее проверяются UI/selector/четыре защищенные схемы, регистрации Eureka и ровно один активный UP-экземпляр каждого приложения, выдача общей/собственной конфигурации Config Server. Следом выполняются прежние сценарии JWT/Feign, задач/членства/уведомлений, consumer restart, Kafka stop/start, DLT и файлы ровно 10 MiB с сохранностью после пересоздания контейнеров.

В finally удаляются только временные проект/volumes/ключи и его локальные образы; путь логов печатается без токенов/паролей. Проверка JSON конфигурации выполняется в памяти, содержимое Config Server с настройками не печатается.

Результаты 06.11.2026, JDK 21.0.10:

- Полный `mvn clean verify` — BUILD SUCCESS: **265 тестов, 0 failures/errors/skipped**. Все настроенные gates ≥70% пройдены; покрытие security-common 94,87%, event-contracts 100%, user 96,69%, tracker 94,78%, Gateway 92,42%, notification 96,76%, file 92,56%.
- Итоговый изолированный smoke — **PASS, exit 0**. Все перечисленные проверки запуска и бизнес-сценарии прошли: startup дважды, bootstrap=false в контейнере и неизменный ключ, четыре схемы/selector и оба UI, четыре pgAdmin presets, шесть приложений с одним UP-экземпляром, пять собственных конфигураций плюс common.
- Kafka recovery/DLT, consumer restart, четыре типа уведомлений, файл ровно 10 MiB chunked, побайтовое скачивание, права/ошибки/лимиты и сохранность после пересоздания files-db/file-service также прошли.
- Временные контейнеры, сеть, ключи, volumes и образы удалены. Рабочий стенд не использовался. Compose config, Bash/Python syntax, ссылки документов и git diff --check — без ошибок.

Граница проверки: HTTP-проверки UI подтверждают доступность страниц и конфигурации; ручное визуальное прохождение интерфейса не выполнялось. Преднастройки pgAdmin проверены в mounted servers.json для нового volume. Итоговая приемка ЛР1–4, HTTP-сценарии и матрица требований относятся к этапу 8. [Актуальный план](../IMPLEMENTATION_PLAN_LAB4.md).
