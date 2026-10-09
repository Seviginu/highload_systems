# Трекер задач — ЛР №1 / подготовка к ЛР №2

Java 21, Spring Boot, Maven, PostgreSQL, Liquibase и Redis.
План декомпозиции: [IMPLEMENTATION_PLAN_LAB2.md](IMPLEMENTATION_PLAN_LAB2.md).

## Текущее состояние

Репозиторий переведён на Maven multi-module. Корневой `pom.xml` — parent и
агрегатор; `tracker-service` содержит весь монолит ЛР №1, включая пользователей,
миграции и тесты. Пользователи ещё не выделены в отдельное приложение.
Добавлены Config Server, Eureka и Gateway на Spring Cloud 2025.0.3.
Gateway работает на WebFlux; реактивный стек бизнес-сервисов будет добавлен
следующими этапами. Общий Swagger UI размещён на Gateway, а tracker-service
публикует только спецификацию OpenAPI.

```text
pom.xml
config-server/
discovery-server/
api-gateway/
config-repository/        # общие и индивидуальные настройки приложений
tracker-service/
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
работающий Docker. Отчёты находятся в `tracker-service/target/surefire-reports`,
`tracker-service/target/failsafe-reports` и `tracker-service/target/site/jacoco`.
JaCoCo проверяет минимум 70% покрытия строк текущего бизнес-модуля.
При появлении других сервисов будет добавлен агрегированный отчёт.

Исполняемый jar: `tracker-service/target/tracker-service-0.0.1-SNAPSHOT.jar`.
В IntelliJ IDEA импортировать корневой `pom.xml` как Maven-проект.

## Запуск

```sh
./scripts/docker-up.sh
```

Скрипт использует host-порты 58081 для Gateway, 58080 для трекера, 55432 для PostgreSQL, 56379
для Redis, 58888 для Config Server и 58761 для Eureka; их можно переопределить
переменными среды. Прямой
`docker compose up --build -d` использует порты из `.env` либо значения Compose
по умолчанию. Общий Dockerfile выбирает Maven-модуль через build argument
`SERVICE_MODULE` (по умолчанию `tracker-service`). Имя сервиса Compose для
трекера пока остаётся `app`, имена существующих volumes сохраняются.

Порядок запуска: Config Server → Eureka → tracker-service → Gateway; PostgreSQL должен
пройти healthcheck до старта трекера. Config Server стартует независимо от
Eureka и регистрируется в ней после её появления. В реестре имена приложений —
`CONFIG-SERVER`, `TRACKER-SERVICE` и `API-GATEWAY`; standalone Eureka не регистрируется в себе.

При запуске через скрипт:

- Единый API: `http://localhost:58081/api/v1/...`.
- Общий Swagger UI: `http://localhost:58081/swagger-ui/index.html`.
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

- `/api/v1/users/**`, `/api/v1/projects/**` (включая участников),
  `/api/v1/tasks/**` и `/api/v1/labels/**` → `lb://tracker-service`.
- `/v3/api-docs/tracker-service` → `/v3/api-docs` того же сервиса.

Gateway получает адрес tracker-service через Eureka и Spring Cloud LoadBalancer.
Автоматическая публикация всех найденных сервисов не включена: маршруты заданы
явно. Gateway публикует собственные health/info endpoints; actuator бизнес-сервиса
через него не проксируется.

Swagger UI читает список спецификаций из `/v3/api-docs/swagger-config` Gateway.
Сейчас в списке один tracker-service, содержащий все сущности монолита.
После выделения user-service будет добавлена его спецификация и отдельный маршрут.
В OpenAPI указан относительный server URL `/`: запросы Try it out используют
тот же адрес Gateway, с которого загружена спецификация. PreserveHostHeader
сохраняет исходный Host в запросах к трекеру, чтобы Location для текущего
HTTP-развёртывания возвращал внешний адрес Gateway.

## Централизованная конфигурация

Config Server использует native backend: каталог `config-repository`
монтируется в контейнер только для чтения. Общие настройки находятся в
`application.yml`, настройки трекера — в `tracker-service.yml`, Eureka — в
`discovery-server.yml`, Gateway — в `api-gateway.yml`. YAML содержит ссылки на
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

Liquibase changelog остаётся в `tracker-service/src/main/resources`.
Integration-тесты используют профиль `test`: Config Client и Eureka отключены,
а те же центральные YAML копируются Maven в test classpath. Тесты бизнес-логики
не требуют запущенных инфраструктурных приложений.

## Сквозная проверка инфраструктуры

```sh
./scripts/smoke-config-discovery.sh
```

Скрипт собирает образы и запускает отдельный Compose-проект с новыми volumes
и свободными host-портами. Проверяет ответы Config Server, применение удалённых
настроек через `/actuator/info`, регистрацию трёх клиентов в Eureka со статусом
UP и существующий HTTP API трекера. Дополнительно проверяет маршрутизацию
через Gateway, общий Swagger UI и его ресурсы, OpenAPI, создание и удаление
данных, вложенные маршруты, пагинацию, feed, заголовок Location и ошибки.
При завершении удаляет только созданные им
контейнеры, сеть и volumes; текущий рабочий Compose-проект не перезапускается.
