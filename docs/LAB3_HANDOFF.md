# ЛР3: соответствие заданию и сценарий сдачи

Состояние реализации: этапы 1–6 в `main`, включая merge-коммит `7416c28`. Этот документ завершает этап 7. Порядок запуска и матрица прав приведены в [README](../README.md).

## Требования ЛР3

| Требование | Реализация | Подтверждение |
|---|---|---|
| Spring Security и JWT в Authorization | Login в user-service; RS256; независимая проверка JWT в Gateway и обоих сервисах | [JwtSecurityConfiguration](../security-common/src/main/java/itmo/security/JwtSecurityConfiguration.java), [GatewaySecurityTest](../api-gateway/src/test/java/itmo/gateway/GatewaySecurityTest.java), [UserIT](../user-service/src/test/java/itmo/user/UserIT.java) |
| Ролевая модель | ADMIN — супервайзер, TEAM_LEAD — тимлид, DEVELOPER — разработчик | [TrackerAccess](../tracker-service/src/main/java/itmo/infrastructure/security/TrackerAccess.java), матрица в README |
| Логика зависит от текущего пользователя | Автор задачи из JWT; тимлид управляет проектами с активным членством; разработчик меняет статус назначенной ему задачи | [CurrentUser](../tracker-service/src/main/java/itmo/infrastructure/security/CurrentUser.java), [TaskServiceImpl](../tracker-service/src/main/java/itmo/task/service/TaskServiceImpl.java), [PermissionsIT](../tracker-service/src/test/java/itmo/security/PermissionsIT.java) |
| Пароль отсутствует в ответах пользователя | Отдельные UserResponse и UserReference; хэш скрыт при сериализации Entity | [UserResponse](../user-service/src/main/java/itmo/user/dto/UserResponse.java), [UserIT](../user-service/src/test/java/itmo/user/UserIT.java): `shouldHashPasswordAndNeverReturnCredentials` |
| Хэшированное хранение | BCrypt, поле password_hash; существующие записи остаются без учетных данных до назначения пароля | [PasswordService](../user-service/src/main/java/itmo/user/service/PasswordService.java), [миграция](../user-service/src/main/resources/db/changelog/002-add-password-hash.sql), [UserMigrationIT](../user-service/src/test/java/itmo/user/UserMigrationIT.java) |
| Создание пользователей только супервайзером | ADMIN-only маршруты и сервисные проверки; первый ADMIN создается явно включаемым bootstrap | [UserServiceImpl](../user-service/src/main/java/itmo/user/service/UserServiceImpl.java), [AdminBootstrap](../user-service/src/main/java/itmo/infrastructure/security/AdminBootstrap.java), [UserIT](../user-service/src/test/java/itmo/user/UserIT.java): `shouldRestrictUserManagementToSupervisorIncludingPasswordAssignment` |

Пароль передается во входном JSON login, создания пользователя и назначения пароля. В ответах с моделями пользователя, внутренних ссылках и JWT пароля и хэша нет.

## Сохранение требований ЛР1–2

| Требование | Что сохранено |
|---|---|
| JVM, Spring Boot, Maven, разделение слоев | Java 21; контроллеры, сервисы, репозитории, Entity и DTO; [Maven-модули](../pom.xml) |
| REST CRUD, статусы, валидация и ошибки | API `/api/v1/users`, `/projects`, `/tasks`, `/labels` и участников; 201/204/400/401/403/404/409/503 по ситуации; Bean Validation и ApiError |
| Валидация Entity | JPA-валидация в tracker; [BeforeConvertCallback](../user-service/src/main/java/itmo/user/persistence/UserBeforeConvertCallback.java) в R2DBC; соответствующие тесты Entity в UserIT, TaskIT, ProjectIT и LabelIT |
| Миграции, БД и конфигурация | Liquibase в обоих бизнес-сервисах; две PostgreSQL; параметры окружения и Config Server; [Compose](../docker-compose.yml) |
| Пагинация до 50 | Публичные списки ограничены `@Max(50)`; `X-Total-Count` содержит общее число записей |
| Бесконечная прокрутка без общего количества | `GET /api/v1/tasks/feed?limit=20`; Slice и `X-Next-Cursor`, без count query; [TaskIT](../tracker-service/src/test/java/itmo/task/TaskIT.java): `shouldReadTaskFeedByCursorWithoutCountQuery` |
| Транзакции сложных операций | Создание проекта с участником и перенос задачи выполняются атомарно; обоснование ниже |
| Связи и строковые enums | Project–Task — OneToMany/ManyToOne; Task–Label — ManyToMany; ProjectMember хранит связь проекта с пользователем и поля active/joined_at. JPA enums — EnumType.STRING, роль пользователя — строка в БД |
| Декомпозиция и инфраструктура | user-service, tracker-service, Eureka, Config Server и Gateway сохранены; security-common — библиотека, отдельного процесса у нее нет |
| Feign и Circuit Breaker | [UserClient](../tracker-service/src/main/java/itmo/integration/user/UserClient.java) передает JWT; [UserCircuitBreakerIT](../tracker-service/src/test/java/itmo/integration/user/UserCircuitBreakerIT.java) проверяет CLOSED/OPEN/HALF_OPEN и исключение 401/403 из сбоев доступности |
| Reactor + R2DBC | user-service: Mono/Flux и реактивные репозитории |
| Reactor + JPA | tracker-service: WebFlux с JPA/Feign/Redis на worker scheduler; [BlockingBoundaryIT](../tracker-service/src/test/java/itmo/infrastructure/reactor/BlockingBoundaryIT.java) проверяет границу потоков и транзакций |
| Общий Swagger/OpenAPI | Единый Swagger через Gateway; две схемы с Bearer JWT; скрипт запуска ожидает их доступность |
| Тесты и покрытие | JUnit Jupiter, PostgreSQL/Redis Testcontainers, порог JaCoCo 70%; фактические результаты ниже |
| Feature branching и conventional commits | Изменения ЛР3 вносились feature-ветками и merge-коммитами в main |

После декомпозиции пользовательские ссылки в tracker хранят ID и проверяются через user-service. SQL foreign key между разными БД нет; это изменение ЛР2 сохранено. Согласование архитектуры БД с преподавателем — организационный пункт ранее сданных ЛР1–2, отдельным техническим тестом не подтверждается.

### Зачем нужны две транзакции

1. `ProjectServiceImpl.create`: создание проекта и добавление тимлида в project_members должны завершиться вместе. При ошибке добавления участника проект откатывается. Это проверяет `ProjectIT.shouldRollbackProjectWhenLocalMemberInsertFails`.
2. `TaskServiceImpl.move`: изменение проекта, исполнителя и меток задачи должно быть атомарным. Ошибка членства или версии не должна оставлять частично перенесенную задачу. Это проверяют `TaskIT.shouldMoveTaskAtomicallyAndRejectNonMemberAssignee` и PermissionsIT.

Feign-проверки пользователей выполняются до локальных транзакций записи. Эти транзакции относятся к tracker-db; распределенная транзакция с user-service не используется.

## Результаты проверки

Последний полный прогон: **22.10.2026**, JDK 21, `mvn clean verify`, BUILD SUCCESS. После него менялась только документация и выполнялись Git-слияния; повторный запуск без изменения кода не выполнялся.

| Модуль | Тестов | Покрытие строк JaCoCo |
|---|---:|---:|
| security-common | 2 | 94,87% |
| user-service | 51 | 97,08% |
| tracker-service | 156 | 91,85% |
| api-gateway | 2 | 90,48% |
| Всего | 211 | Все настроенные проверки ≥70% прошли |

Ошибок, падений и пропусков — 0. Покрытие указано для перечисленных модулей; config-server и discovery-server не входят в эти отчеты JaCoCo. После локального прогона отчеты находятся в `<module>/target/site/jacoco/index.html`, результаты тестов — в target/surefire-reports и target/failsafe-reports.

Docker-сборка и запуск через `scripts/docker-up.sh` проверены в отдельном Compose-проекте с собственными портами и volumes. API smoke подтвердил:

- bootstrap и login через Gateway, запреты 401/403 и ADMIN-only создание пользователей;
- автора из JWT и его сохранение после PUT;
- смену статуса исполнителем, отказ другому разработчику и конфликт устаревшей версии;
- запрет переноса без членства в обоих проектах и успешный перенос после выдачи членства;
- отказ после деактивации участника, сохранение статуса и версии в кэше;
- работу реального Feign с JWT и доступность обеих OpenAPI-схем.

Тестовые контейнеры и volumes удалены; рабочий стенд не изменялся. Браузерные проверки исключены по просьбе пользователя. OpenAPI проверен через API; работа кнопки Authorize в браузере отдельно не подтверждалась.

## Сценарий демонстрации

Использовать учебный стенд и API-клиент. Запуск и настройка первого супервайзера — по README. ID брать из ответов сервера, токены хранить раздельно для каждой роли. Примеры ID ниже условные: тимлид 2, разработчики 3 и 4, проект 1.

### 1. Вход супервайзера и создание пользователей

`POST /api/v1/auth/login` с email и паролем ADMIN → 200, accessToken, tokenType=Bearer, expiresIn. Дальнейшие запросы отправлять с `Authorization: Bearer <accessToken>`.

Под ADMIN создать тимлида и двух разработчиков через `POST /api/v1/users`, меняя name/email/role:

```json
{"name":"Lead","email":"lead-demo@example.com","role":"TEAM_LEAD","password":"Demo-password-123"}
```

Ожидание: 201 и Location. В JSON-ответе нет password/passwordHash. Для разработчиков использовать роль DEVELOPER и разные email. Выполнить login для каждого пользователя и сохранить его токен.

### 2. Проект, участники и задача

Под тимлидом выполнить `POST /api/v1/projects`:

```json
{"name":"Demo","code":"DEMO","status":"ACTIVE","teamLeadId":2}
```

Ожидание: 201. Под тем же тимлидом добавить обоих разработчиков через `POST /api/v1/projects/1/members` с `{"userId":3}` и `{"userId":4}` → 201.

Создать `POST /api/v1/tasks`:

```json
{"taskKey":"DEMO-1","title":"Demo task","status":"TODO","priority":"MEDIUM","assigneeId":3,"projectId":1,"labelIds":[]}
```

Ожидание: 201, authorId=2 и version=0. Автор берется из токена тимлида; поле authorId не требуется во входном DTO.

### 3. Показать разницу прав и контроль версии

Для фактического ID задачи выполнить `PATCH /api/v1/tasks/{id}/status`:

```json
{"status":"IN_PROGRESS","version":0}
```

| Действие | Ожидание |
|---|---|
| PATCH под назначенным разработчиком 3 | 200, статус IN_PROGRESS, новая version |
| PATCH под разработчиком 4 с актуальной version | 403, задача не меняется |
| Повтор PATCH под разработчиком 3 со старой version=0 | 409 |
| Полный PUT или DELETE задачи под разработчиком 3 | 403 |
| Создание пользователя с валидным телом под тимлидом | 403 |
| GET `/api/v1/tasks` без Authorization | 401, ApiError |
| GET `/api/v1/auth/me` под разработчиком | 200, собственный профиль без секретов |

После отказов получить задачу через GET: статус и версия должны совпадать с последней успешной операцией.

### 4. Показать пароли и сохраненные контракты

В users-db выполнить:

```sql
SELECT id, email, role,
       password_hash IS NOT NULL AS has_password,
       password_hash ~ '^[$]2[aby][$]' AS bcrypt,
       length(password_hash) AS hash_length
FROM users
ORDER BY id;
```

Для созданных пользователей ожидаются has_password=true, bcrypt=true, hash_length=60. Сам пароль и хэш выводить не требуется. У старых записей без назначенного пароля поле остается NULL.

Дополнительно показать `GET /api/v1/tasks?page=0&size=20` с X-Total-Count, `GET /api/v1/tasks/feed?limit=20` без общего количества и отказ 400 для size=51. Общая документация доступна через Gateway по `/swagger-ui/index.html`; схемы — `/v3/api-docs/user-service` и `/v3/api-docs/tracker-service`.

## Принятые границы реализации

- JWT живет 15 минут по умолчанию. Смена роли, пароля или удаление пользователя не отзывает уже выданный JWT немедленно; новый вход использует актуальную БД, `/auth/me` отклоняет удаленного пользователя.
- Чтение трекера доступно всем аутентифицированным ролям. Проверки членства ограничивают изменение данных.
- Первый ADMIN создается bootstrap; публичной регистрации нет. Существующим пользователям пароль назначает ADMIN.
- Старые записи сохраняются миграцией. Bootstrap не повышает роль записи с занятым email и не сбрасывает существующий пароль.
