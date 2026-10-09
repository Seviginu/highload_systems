# Трекер задач — ЛР №1 / подготовка к ЛР №2

Java 21, Spring Boot, Maven, PostgreSQL, Liquibase и Redis.
План декомпозиции: [IMPLEMENTATION_PLAN_LAB2.md](IMPLEMENTATION_PLAN_LAB2.md).

## Текущее состояние

Репозиторий переведён на Maven multi-module. Корневой `pom.xml` — parent и
агрегатор; `tracker-service` содержит проекты, участников, задачи и метки на JPA.
Пользователи выделены в `user-service` на WebFlux + Reactor + R2DBC со своей БД.
Трекер проверяет активных пользователей и роли через Feign по имени user-service.
Добавлены Config Server, Eureka и Gateway на Spring Cloud 2025.0.3.
Gateway работает на WebFlux; перевод tracker-service на Reactor/WebFlux
и Circuit Breaker остаются следующими этапами. Общий Swagger UI размещён на Gateway, а tracker-service
публикует только спецификацию OpenAPI.

```text
pom.xml
config-server/
discovery-server/
api-gateway/
config-repository/        # общие и индивидуальные настройки приложений
user-service/            # Reactor + R2DBC, users-db
tracker-service/         # JPA, собственная БД и Redis
  pom.xml
  src/main/
  src/test/
Dockerfile
docker-compose.yml
scripts/docker-up.sh
```

## Сборка и проверки

Команды выполняются из корня репозитория с JDK 21:

```sh
mvn clean verify
mvn -pl tracker-service -am package -DskipTests
```

На macOS при нескольких установленных JDK:

```sh
JAVA_HOME="$(/usr/libexec/java_home -v 21)" mvn clean verify
```

`verify` запускает unit- и integration-тесты. Для Testcontainers нужен
работающий Docker. Отчёты обоих бизнес-модулей находятся в их
`target/surefire-reports`, `target/failsafe-reports` и `target/site/jacoco`.
JaCoCo проверяет минимум 70% покрытия строк каждого бизнес-модуля.
Агрегированный отчёт будет добавлен на итоговом этапе.

Исполняемый jar: `tracker-service/target/tracker-service-0.0.1-SNAPSHOT.jar`.
В IntelliJ IDEA импортировать корневой `pom.xml` как Maven-проект.

## Запуск

```sh
./scripts/docker-up.sh
```

При сохранении данных ЛР №1 сначала выполнить процедуру переноса пользователей ниже.

Скрипт использует host-порты 58081 для Gateway, 58080 для трекера, 58082 для пользователей,
55432 для PostgreSQL трекера, 56379
для Redis, 58888 для Config Server и 58761 для Eureka; их можно переопределить
переменными среды. Прямой
`docker compose up --build -d` использует порты из `.env` либо значения Compose
по умолчанию. Общий Dockerfile выбирает Maven-модуль через build argument
`SERVICE_MODULE` (по умолчанию `tracker-service`). Имя сервиса Compose для
трекера пока остаётся `app`, имена существующих volumes сохраняются.

Порядок запуска: Config Server → Eureka → бизнес-сервисы → Gateway; каждая БД
должна пройти healthcheck до старта своего сервиса. Config Server стартует независимо от
Eureka и регистрируется в ней после её появления. В реестре имена приложений —
`CONFIG-SERVER`, `TRACKER-SERVICE`, `USER-SERVICE` и `API-GATEWAY`; standalone Eureka не регистрируется в себе.

При запуске через скрипт:

- Единый API: `http://localhost:58081/api/v1/...`.
- Общий Swagger UI: `http://localhost:58081/swagger-ui/index.html`.
- OpenAPI пользователей: `http://localhost:58081/v3/api-docs/user-service`.
- Прямой API пользователей: `http://localhost:58082/api/v1/users`.
- OpenAPI трекера через Gateway: `http://localhost:58081/v3/api-docs/tracker-service`.
- Readiness Gateway: `http://localhost:58081/actuator/health/readiness`.
- Прямой API трекера для диагностики: `http://localhost:58080/api/v1/...`.
- Eureka UI: `http://localhost:58761/`.
- Конфигурация трекера: `http://localhost:58888/tracker-service/default`.
- Конфигурация Eureka: `http://localhost:58888/discovery-server/default`.
- Конфигурация Gateway: `http://localhost:58888/api-gateway/default`.

`GATEWAY_PORT` задаёт host-порт Gateway; прямой Compose-запуск по умолчанию
использует 8081. `SERVER_PORT` сохраняет прежнее назначение host-порта трекера.
Swagger UI непосредственно на tracker-service больше не публикуется.

## Маршруты Gateway и общий Swagger

Маршруты заданы в `config-repository/api-gateway.yml`:

- `/api/v1/users/**` → `lb://user-service`.
- `/api/v1/projects/**` (включая участников), `/api/v1/tasks/**`
  и `/api/v1/labels/**` → `lb://tracker-service`.
- `/v3/api-docs/user-service` и `/v3/api-docs/tracker-service` → `/v3/api-docs`
  соответствующего сервиса.
- `/internal/users/resolve` не публикуется через Gateway и скрыт из OpenAPI.

Gateway получает адрес tracker-service через Eureka и Spring Cloud LoadBalancer.
Автоматическая публикация всех найденных сервисов не включена: маршруты заданы
явно. Gateway публикует собственные health/info endpoints; actuator бизнес-сервиса
через него не проксируется.

Swagger UI читает список спецификаций из `/v3/api-docs/swagger-config` Gateway.
В общем UI доступны две спецификации: user-service и tracker-service.
В OpenAPI указан относительный server URL `/`: запросы Try it out используют
тот же адрес Gateway, с которого загружена спецификация. PreserveHostHeader
сохраняет исходный Host в запросах к бизнес-сервисам, чтобы Location для текущего
HTTP-развёртывания возвращал внешний адрес Gateway.

## Централизованная конфигурация

Config Server использует native backend: каталог `config-repository`
монтируется в контейнер только для чтения. Общие настройки находятся в
`application.yml`, настройки трекера — в `tracker-service.yml`, Eureka — в
`discovery-server.yml`, Gateway — в `api-gateway.yml`, пользователей — в
`user-service.yml`. Feign имеет connectTimeout 2 с и readTimeout 3 с; повторы
изменяющих операций не включены. YAML содержит ссылки на
переменные среды; параметры БД,
Redis и адрес Eureka задаются окружением соответствующего приложения.

Локальные `application.yml` клиентов содержат имя приложения и обязательный
`spring.config.import=configserver:...`. Без Config Server runtime-запуск
прерывается. Сам Config Server хранит bootstrap-настройки локально, чтобы
не зависеть от собственного HTTP API. Загрузка изменений из YAML выполняется
при запуске клиентов; автоматическое обновление уже работающих приложений
на этом этапе не включено.

При запуске jar из корня репозитория Config Server по умолчанию читает
`./config-repository` и слушает порт 8888, Eureka — 8761. При использовании
портов скрипта задавать клиентам `CONFIG_SERVER_URL=http://localhost:58888`,
а также `EUREKA_URL=http://localhost:58761/eureka/`. Для native backend другого
каталога используется `CONFIG_REPOSITORY_LOCATION=file:/absolute/path/`.

Каждый бизнес-сервис имеет свой Liquibase changelog в src/main/resources.
Прежние миграции трекера сохранены; новая снимает только три FK к users.
Таблица users в БД трекера остаётся для переноса прежних данных. Рабочий код
трекера больше её не использует и не подключается к users-db.
Integration-тесты используют профиль `test`: Config Client и Eureka отключены,
а те же центральные YAML копируются Maven в test classpath. Тесты бизнес-логики
не требуют запущенных инфраструктурных приложений.

## Сквозная проверка инфраструктуры

```sh
./scripts/smoke-config-discovery.sh
```

Скрипт собирает образы и запускает отдельный Compose-проект с новыми volumes
и свободными host-портами. Проверяет ответы Config Server, применение удалённых
настроек через `/actuator/info`, регистрацию четырёх клиентов в Eureka со статусом
UP и существующий HTTP API трекера. Дополнительно проверяет маршрутизацию
через Gateway, общий Swagger UI и его ресурсы, OpenAPI, создание и удаление
данных, вложенные маршруты, пагинацию, feed, заголовок Location и ошибки.
Также проверяет перенос прежних ID/дат, отказ повторного импорта, Feign,
остановку и восстановление user-service, отсутствие частичных записей,
перенос задачи и сохранение исторических ссылок после логического удаления.
При завершении удаляет только созданные им
контейнеры, сеть и volumes; текущий рабочий Compose-проект не перезапускается.

## User-service и межсервисные проверки

Новый модуль использует WebFlux, Reactor и PostgreSQL R2DBC. Он читает настройки
из Config Server, регистрируется в Eureka и имеет отдельный Liquibase changelog.
Для его проверки отдельно от трекера:

```sh
mvn -pl user-service -am clean verify
docker compose up --build user-service
```

Прямой API: `http://localhost:8082/api/v1/users`; порт задаётся USER_SERVICE_PORT.
При необходимости переопределить CONFIG_SERVER_PORT и EUREKA_SERVER_PORT,
чтобы не занимать порты других проектов. OpenAPI: `/v3/api-docs`.

DELETE выполняет логическое удаление: ID и запись сохраняются; публичные чтения
и внутренние проверки скрывают удалённого пользователя. Его email остаётся
занятым. Конкурентное обновление старой версии возвращает 409.

Создание проекта проверяет роль TEAM_LEAD. Создание задачи и добавление участника
проверяют активность пользователя. Проверка Feign предшествует локальной DB-транзакции.
Недоступность сервиса возвращает 503 без записи; отсутствующий/удалённый ID — 404,
неверная роль — 409. Чтение задач и участников сохраняет прежние ID.
Обычное обновление задачи проверяет только изменяемые пользовательские ссылки,
поэтому текст задачи можно изменить после удаления автора/исполнителя.
При переносе задачи выбранный исполнитель должен быть активным пользователем
и активным участником целевого проекта. Межсервисная ACID-транзакция не используется.

## Перенос пользователей из ЛР №1

Для нового окружения с пустыми БД перенос не нужен. Если сохраняется прежний
PostgreSQL volume, перенос выполнить до создания пользователей в новом сервисе:

1. Остановить запись через прежние приложения: `docker compose stop app api-gateway`.
2. Запустить `user-service`, чтобы Liquibase создал users-db:
   `docker compose up --build -d --wait user-service`.
3. Остановить его: `docker compose stop user-service`.
4. Выполнить `python3 scripts/migrate-users.py`.
5. Запустить полный стек: `./scripts/docker-up.sh`.

Для нестандартного Compose-проекта передавать один и тот же `-p` всем командам;
скрипту импорта — `--project NAME`. Сохранять прежние переменные среды/порты при
подготовительном запуске зависимостей, чтобы Compose не менял их конфигурацию.

Скрипт требует пустую целевую таблицу и остановленные app, user-service, api-gateway.
Он переносит ID, имя, email, роль и даты, проверяет исторические ссылки,
настраивает identity sequence и сверяет данные чтением после импорта.
Новые поля получают deleted=false и version=0. Исходные строки/volume сохраняются.
Повторный импорт в непустую таблицу отклоняется; автоматическое объединение БД
не выполняется. Перенос на рабочих данных в ходе разработки не запускался.
