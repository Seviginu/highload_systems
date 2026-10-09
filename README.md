# Трекер задач — ЛР №1 / подготовка к ЛР №2

Java 21, Spring Boot, Maven, PostgreSQL, Liquibase и Redis.
План декомпозиции: [IMPLEMENTATION_PLAN_LAB2.md](IMPLEMENTATION_PLAN_LAB2.md).

## Текущее состояние

Репозиторий переведён на Maven multi-module. Корневой `pom.xml` — parent и
агрегатор; `tracker-service` содержит весь монолит ЛР №1, включая пользователей,
миграции и тесты. Пользователи ещё не выделены в отдельное приложение.
Spring Cloud и реактивный стек будут добавлены следующими этапами.

```text
pom.xml
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

Скрипт использует host-порты 58080 для приложения, 55432 для PostgreSQL и 56379
для Redis; их можно переопределить переменными среды. Прямой
`docker compose up --build -d` использует порты из `.env` либо значения Compose
по умолчанию. Dockerfile собирает модуль `tracker-service`; имя сервиса Compose
пока остаётся `app`, имена существующих volumes сохраняются.

При запуске через скрипт:

- API: `http://localhost:58080/api/v1/...`.
- Swagger UI: `http://localhost:58080/swagger-ui/index.html`.
- Readiness: `http://localhost:58080/actuator/health/readiness`.

Настройки приложения и Liquibase changelog перенесены в
`tracker-service/src/main/resources`.
