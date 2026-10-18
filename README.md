# SharedNotes
SharedNotes – REST API для заметок с возможностью делиться ими с другими пользователями.

## Стек
- Java 21
- Gradle (Kotlin DSL)
- Spring Boot 4.0.3 – Web MVC, Data JPA, Security, Validation
- PostgreSQL + Flyway
- JWT-аутентификация (jjwt)
- JUnit 5, Mockito, Testcontainers

Точные версии зависимостей – в [`build.gradle.kts`](build.gradle.kts).

## Возможности
- Регистрация и вход
- Soft-delete пользователя
- Разделение ролей USER/ADMIN
- Ротация refresh-токенов
- Scheduled чистка soft-deleted пользователей и refresh-токенов

USER (свой аккаунт):
  - Просмотр
  - Изменение
  - Удаление

ADMIN:
  - Просмотр списка пользователей
  - Поиск пользователя по username и id
  - Создание и удаление пользователя

## Эндпоинты
### Auth
| Метод | Путь                    | Описание                               | Токен                          |
|-------|-------------------------|----------------------------------------|--------------------------------|
| POST  | `/auth/register`        | Регистрация                            | –                              |
| POST  | `/auth/login`           | Вход, выдача пары токенов              | –                              |
| POST  | `/auth/refresh`         | Выдача новых токенов (и отзыв старых)  | Refresh-токен (в теле запроса) |
| POST  | `/auth/logout`          | Выход из аккаунта, отзыв сессии        | Refresh-токен (в теле запроса) |
| POST  | `/auth/logout-all`      | Отзыв всех текущих сессий пользователя | Access-токен                   |
| POST  | `/auth/change-password` | Смена пароля                           | Access-токен                   |
| POST  | `/auth/restore`         | Восстановление soft-deleted аккаунта   | –                              |

### Users
| Метод  | Путь        | Описание                       | Токен        |
|--------|-------------|--------------------------------|--------------|
| GET    | `/users/me` | Просмотр текущего пользователя | Access-токен |
| PATCH  | `/users/me` | Обновление профиля             | Access-токен |
| DELETE | `/users/me` | Soft-delete аккаунта           | Access-токен |

### Admin
Все эндпоинты требуют access-токен и роль ADMIN.

| Метод  | Путь                               | Описание                                  |
|--------|------------------------------------|-------------------------------------------|
| GET    | `/admin/users`                     | Просмотр списка пользователей             |
| GET    | `/admin/users/{id}`                | Поиск и просмотр пользователя по id       |
| GET    | `/admin/users/username/{username}` | Поиск и просмотр пользователя по username |
| POST   | `/admin/users`                     | Создание нового пользователя              |
| DELETE | `/admin/users/{id}`                | Soft-delete аккаунта                      |

## Запуск
**Требования**

- JDK 21
- Docker

**Переменные окружения** (необходимо установить):

```bash
cp .env.example .env
```

База данных:
  - DB_NAME
  - DB_USER
  - DB_PASSWORD

Токены:
  - JWT_SECRET
  - ACCESS_EXPIRATION (default 15m)
  - REFRESH_EXPIRATION (default 7d)

Тестовый админ:
  - ADMIN_USERNAME
  - ADMIN_EMAIL
  - ADMIN_PASSWORD

ADMIN_PASSWORD: минимум 8 символов, цифра, заглавная буква, спецсимвол.

JWT_SECRET:
```bash
openssl rand -base64 32
```
Запуск БД и приложения:
```bash
docker compose up -d
```

## Тесты

Команда запуска:
```bash
./gradlew test
```

Покрытие пакетов:
- user
- auth
- common
- security
- exceptions

Юнит, слайс, интеграционные на Testcontainers (поднимают PostgreSQL).

## TODO
- Notes CRUD
- Search, filter, pagination
- Sharing
- Swagger
