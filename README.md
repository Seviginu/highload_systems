# Трекер задач — ЛР №1 / подготовка к ЛР №2

Java 21, Spring Boot, Maven, PostgreSQL, Liquibase и Redis.
План декомпозиции: [IMPLEMENTATION_PLAN_LAB2.md](IMPLEMENTATION_PLAN_LAB2.md).

## Текущее состояние

Репозиторий переведён на Maven multi-module. Корневой `pom.xml` — parent и
агрегатор; `tracker-service` содержит весь монолит ЛР №1, включая пользователей,
миграции и тесты. Пользователи ещё не выделены в отдельное приложение.
Добавлены Config Server и Eureka на Spring Cloud 2025.0.3. Реактивный стек
и Gateway будут добавлены следующими этапами.

```text
pom.xml
config-server/
discovery-server/
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

Скрипт использует host-порты 58080 для приложения, 55432 для PostgreSQL, 56379
для Redis, 58888 для Config Server и 58761 для Eureka; их можно переопределить
переменными среды. Прямой
`docker compose up --build -d` использует порты из `.env` либо значения Compose
по умолчанию. Общий Dockerfile выбирает Maven-модуль через build argument
`SERVICE_MODULE` (по умолчанию `tracker-service`). Имя сервиса Compose для
трекера пока остаётся `app`, имена существующих volumes сохраняются.

Порядок запуска: Config Server → Eureka → tracker-service; PostgreSQL должен
пройти healthcheck до старта трекера. Config Server стартует независимо от
Eureka и регистрируется в ней после её появления. В реестре имена приложений —
`CONFIG-SERVER` и `TRACKER-SERVICE`; standalone Eureka не регистрируется в себе.

При запуске через скрипт:

- API: `http://localhost:58080/api/v1/...`.
- Swagger UI: `http://localhost:58080/swagger-ui/index.html`.
- Readiness: `http://localhost:58080/actuator/health/readiness`.
- Eureka UI: `http://localhost:58761/`.
- Конфигурация трекера: `http://localhost:58888/tracker-service/default`.
- Конфигурация Eureka: `http://localhost:58888/discovery-server/default`.

## Централизованная конфигурация

Config Server использует native backend: каталог `config-repository`
монтируется в контейнер только для чтения. Общие настройки находятся в
`application.yml`, настройки трекера — в `tracker-service.yml`, Eureka — в
`discovery-server.yml`. YAML содержит ссылки на переменные среды; параметры БД,
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

## Проверка Config Server и Eureka

```sh
./scripts/smoke-config-discovery.sh
```

Скрипт собирает образы и запускает отдельный Compose-проект с новыми volumes
и свободными host-портами. Проверяет ответы Config Server, применение удалённых
настроек через `/actuator/info`, регистрацию обоих клиентов в Eureka со статусом
UP и существующий HTTP API трекера. При завершении удаляет только созданные им
контейнеры, сеть и volumes; текущий рабочий Compose-проект не перезапускается.
