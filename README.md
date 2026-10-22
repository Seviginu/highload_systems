# Task tracker — лабораторные работы по JVM

Java 21, Maven, Spring Boot. После ЛР2 проект состоит из `user-service` (WebFlux/R2DBC), `tracker-service` (WebFlux/JPA), Gateway, Eureka и Config Server. PostgreSQL и Redis запускаются через Docker Compose.

План ЛР3: [IMPLEMENTATION_PLAN_LAB3.md](IMPLEMENTATION_PLAN_LAB3.md).

## Текущий этап ЛР3: хранение паролей и bootstrap

В `POST /api/v1/users` добавлено обязательное поле `password`: минимум 8 символов, максимум 72 байта UTF-8. В БД сохраняется BCrypt-хэш с индивидуальной солью. Ответы пользователя и внутреннего API не содержат пароль или хэш. Обычное обновление пользователя пароль не меняет.

Миграция сохраняет существующих пользователей и их ID; у старых записей `password_hash` остается NULL. Такие записи пока не имеют учетных данных. Назначение пароля существующим пользователям будет добавлено вместе с ограничением операции ролью ADMIN.

Для создания первого супервайзера скопировать `.env.example` в `.env` и задать:

```dotenv
BOOTSTRAP_ADMIN_ENABLED=true
BOOTSTRAP_ADMIN_NAME=Supervisor
BOOTSTRAP_ADMIN_EMAIL=supervisor@example.com
BOOTSTRAP_ADMIN_PASSWORD=<ваш пароль>
```

`ADMIN` — супервайзер из задания. Bootstrap включается явно и создает его только при отсутствии активного ADMIN с заданным хэшем пароля. Если указанный email занят любой существующей записью, создание завершится ошибкой: bootstrap не повышает ее роль и не сбрасывает пароль. При повторном запуске с существующим ADMIN с паролем ничего не меняется, даже если изменить bootstrap-параметры. После первичной настройки bootstrap можно отключить.

Запуск:

```bash
./scripts/docker-up.sh
```

Скрипт использует `GATEWAY_PORT=58081` по умолчанию: общий Swagger — `http://localhost:58081/swagger-ui/index.html`. При запуске непосредственно через `docker compose up --build -d` без переопределений используется порт 8081.

JWT и ограничения доступа будут реализованы следующими этапами. Сейчас API сохраняет открытый доступ ЛР2; создание первого ADMIN само по себе не включает авторизацию. Нового HTTP-метода сброса пароля на этом этапе нет.

Проверка на JDK 21 с доступным Docker:

```bash
mvn clean verify
```

Интеграционные тесты используют отдельные PostgreSQL/Redis Testcontainers. Рабочие Compose volumes для тестов не требуются.
