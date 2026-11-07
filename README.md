# ОКО ТПУ — оценка качества цифровых продуктов

Сервис собирает оценки пользователей цифровых продуктов ТПУ через встраиваемый виджет и API. Владельцы продуктов видят статистику и эффект релизов, ЦЭУ видит сводку по всем продуктам с состоянием сервисов.

Правила для Claude и решения команды — в `CLAUDE.md`.

## Что где лежит

| Путь | Что это | Владелец |
|---|---|---|
| `contracts/oko-openapi.yaml` | спецификация API, источник правды для формата обмена | Николай |
| `docs/architecture.md` | модули, потоки данных, инфраструктура | Данил |
| `docs/db.md` | таблицы PostgreSQL, Flyway | Данил |
| `docs/team/` | ТЗ, протоколы, план, SSO, правила работы, расхождения в документах | Алина, Николай |
| `backend/` | Spring Boot, API, приём оценок | Данил |
| `frontend/` | панель (React) и виджет | Таир |
| `deploy/` | docker-compose, nginx, `.env.example` | Данил |
| `tests/` | e2e и приёмка | Николай |

Макеты ведутся в Figma, см. `docs/team/Макеты.md`.

## Документы команды (`docs/team/`)

| Файл | Что это |
|---|---|
| ТехническоеЗадание.md | ТЗ заказчика |
| Вопросы заказчику и ответы.md | таблица вопросов и ответов со встречи |
| Анализ встречи.md | что решили на встрече и что открыто |
| План на неделю.md | план 3–10 ноября |
| Исследование и требования.md | ресерч: дизайн страниц, правила подсчёта |
| Распределение задач и контракт взаимодействия.md | роли, обмен между фронтом и backend, риски |
| Документация SSO ТПУ.md | документация единой авторизации ТПУ (OAuth 2.0) |
| Выжимка SSO для ОКО.md | что из SSO важно для ОКО |
| Как работаем с Claude.md | правила работы команды с Claude |
| Расхождения в документах.md | противоречия между документами, что выбрано в коде, что решить |

## Быстрый старт

Нужны Java 21 и Docker.

```bash
# тесты backend (Testcontainers поднимет PostgreSQL сам)
cd backend && ./mvnw verify

# backend локально с заглушкой входа
docker run -d --name oko-pg -e POSTGRES_DB=oko -e POSTGRES_USER=oko -e POSTGRES_PASSWORD=oko -p 5432:5432 postgres:17-alpine
cd backend && ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev

# панель и виджет против этого backend (Vite на :5173 проксирует /api на :8080)
cd frontend && npm install && npm run dev:backend

# всё вместе: nginx + backend + PostgreSQL
cp deploy/.env.example deploy/.env   # заполнить значения
docker compose -f deploy/docker-compose.yml --env-file deploy/.env up --build
```

Подробности — в `backend/CLAUDE.md`.

CI на GitHub Actions, образ в GitHub Container Registry и VPS с автоматическим pull-деплоем
из `dev` (ветка настраивается) — [инструкция](deploy/README.md). Репозиторий на кодлабе ТПУ заморожен.

## Что не кладём в репозиторий

- `.env`, ключи, `client_secret`, HMAC-секрет.
- Выгрузки с персональными данными. Для примеров — выдуманные данные.
