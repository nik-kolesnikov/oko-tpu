# ОКО ТПУ: структура БД (PostgreSQL, MVP)

Структура следует разделу 5 архитектуры, с уточнениями, которые появились при проектировании таблиц.

## Общие принципы

- Все идентификаторы `bigint` (`generated always as identity`), даты и время `timestamptz`, кроме дня агрегата: там `date`.
- Перечисления делаем через `text` с `CHECK`, а не через `ENUM` PostgreSQL. Так новый статус добавляется миграцией без пересоздания типа.
- Внешние ключи с `ON DELETE RESTRICT`. Удаление продукта мягкое: статус `archived`, физически данные не стираем.
- Схема меняется только миграциями Flyway: `V1__init.sql`, `V2__...` и так далее.

## Таблицы

### Пользователи и владельцы

```sql
CREATE TABLE app_user (
    id          bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    login       text NOT NULL UNIQUE,          -- логин ТПУ
    full_name   text NOT NULL,
    email       text,
    is_admin    boolean NOT NULL DEFAULT false, -- дублирует конфиг, см. ниже
    created_at  timestamptz NOT NULL DEFAULT now()
);
```

Админы ЦЭУ задаются конфигом, поэтому поле `is_admin` здесь нужно только если хотим видеть их в таблице. Если нет, убираем. Иначе будет два источника правды.

```sql
CREATE TABLE product (
    id           bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name         text NOT NULL CHECK (length(name) BETWEEN 2 AND 200),
    description  text,
    url          text NOT NULL,
    status       text NOT NULL DEFAULT 'active'
                 CHECK (status IN ('active', 'paused', 'archived')),
    created_by   bigint NOT NULL REFERENCES app_user(id),
    created_at   timestamptz NOT NULL DEFAULT now(),
    updated_at   timestamptz NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX product_url_uq ON product (lower(url)) WHERE status <> 'archived';
```

Уникальность URL только у неархивных продуктов: после архивации можно завести такой же заново.

```sql
CREATE TABLE product_owner (
    product_id  bigint NOT NULL REFERENCES product(id),
    user_id     bigint NOT NULL REFERENCES app_user(id),
    added_at    timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (product_id, user_id)
);
```

Правило «минимум один владелец» проверяем в сервисе. На уровне БД его сделать сложно (нужен триггер), а в MVP достаточно проверки в коде плюс тест.

### Сервисы

```sql
CREATE TABLE service (
    id              bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    product_id      bigint NOT NULL REFERENCES product(id),
    name            text NOT NULL,
    url             text,
    monitoring_ref  text,                       -- идентификатор в системе мониторинга
    status          text NOT NULL DEFAULT 'active'
                    CHECK (status IN ('active', 'archived')),
    created_at      timestamptz NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX service_monitoring_uq ON service (monitoring_ref)
    WHERE monitoring_ref IS NOT NULL;
CREATE INDEX service_product_idx ON service (product_id);
```

`monitoring_ref` уникален глобально, потому что одна проверка мониторинга не может относиться к двум сервисам. Если у них будет справочник, поле станет внешним ключом на их ID.

### Ключи и разрешённые сайты

```sql
CREATE TABLE api_key (
    id          bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    product_id  bigint NOT NULL REFERENCES product(id),
    key_type    text NOT NULL CHECK (key_type IN ('public', 'secret')),
    key_prefix  text NOT NULL,                  -- первые 8 символов для отображения
    key_hash    bytea NOT NULL UNIQUE,          -- SHA-256 от ключа, сам ключ не хранится
    created_by  bigint NOT NULL REFERENCES app_user(id),
    created_at  timestamptz NOT NULL DEFAULT now(),
    revoked_at  timestamptz
);
CREATE INDEX api_key_product_idx ON api_key (product_id) WHERE revoked_at IS NULL;

CREATE TABLE allowed_origin (
    id          bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    product_id  bigint NOT NULL REFERENCES product(id),
    origin      text NOT NULL,                  -- например https://lk.tpu.ru
    UNIQUE (product_id, origin)
);
```

Ключ ищется по хэшу, поэтому `key_hash` индексирован через `UNIQUE`. Публичный ключ тоже хранится как хэш: он не секрет, но так единообразнее, а перевыпуск работает одинаково для обоих типов. Для `key_prefix` подойдёт ровно префикс, по которому разработчик узнает нужный ключ в списке.

### Настройки виджета

```sql
CREATE TABLE widget_settings (
    product_id          bigint PRIMARY KEY REFERENCES product(id),
    question            text NOT NULL DEFAULT 'Как вам сервис?' CHECK (length(question) <= 200),
    ask_interval_days   smallint NOT NULL DEFAULT 14 CHECK (ask_interval_days BETWEEN 1 AND 90),
    comment_threshold   smallint NOT NULL DEFAULT 3 CHECK (comment_threshold BETWEEN 1 AND 5),
    position            text NOT NULL DEFAULT 'bottom-right'
                        CHECK (position IN ('bottom-right', 'bottom-left')),
    widget_enabled      boolean NOT NULL DEFAULT true,
    updated_at          timestamptz NOT NULL DEFAULT now()
);
```

Настройки отдельной таблицей, а не колонками в `product`: виджет читает их часто, а у продукта они меняются редко.

### Оценки

```sql
CREATE TABLE rating (
    id           bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    product_id   bigint NOT NULL REFERENCES product(id),
    service_id   bigint REFERENCES service(id),          -- может быть пусто
    score        smallint NOT NULL CHECK (score BETWEEN 1 AND 5),
    scale_max    smallint NOT NULL DEFAULT 5 CHECK (scale_max >= score),
    comment      text CHECK (length(comment) <= 500),
    rater_hash   bytea NOT NULL,                         -- HMAC от идентификатора, не ФИО
    rater_role   text CHECK (rater_role IN ('student', 'staff', 'unknown')),
    page_path    text CHECK (length(page_path) <= 500),
    version      text CHECK (length(version) <= 50),
    source       text NOT NULL CHECK (source IN ('widget', 'api')),
    created_at   timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX rating_product_time_idx ON rating (product_id, created_at DESC);
CREATE INDEX rating_service_time_idx ON rating (service_id, created_at DESC)
    WHERE service_id IS NOT NULL;
CREATE INDEX rating_dedup_idx ON rating (product_id, rater_hash, created_at DESC);
```

Пояснения к полям:
- `scale_max` нужен, чтобы NPS 0–10 можно было добавить без переделки. Сейчас всегда 5.
- `rater_hash` — `bytea` фиксированной длины. Индекс `rating_dedup_idx` нужен для проверки «одна оценка за 14 дней».
- `comment` ограничен 500 символами. Экранирование делаем при выводе, в БД текст хранится как есть.
- Таблица растёт медленно (десятки тысяч строк в год). Партиционирование по годам не нужно в MVP, но стоит заложить, если объём вырастет.

### Агрегат

```sql
CREATE TABLE rating_daily (
    product_id   bigint NOT NULL REFERENCES product(id),
    service_id   bigint NOT NULL DEFAULT 0,   -- 0 = весь продукт
    day          date   NOT NULL,             -- день по Томску
    cnt          integer  NOT NULL DEFAULT 0,
    sum_score    integer  NOT NULL DEFAULT 0,
    c1 integer NOT NULL DEFAULT 0,
    c2 integer NOT NULL DEFAULT 0,
    c3 integer NOT NULL DEFAULT 0,
    c4 integer NOT NULL DEFAULT 0,
    c5 integer NOT NULL DEFAULT 0,
    PRIMARY KEY (product_id, service_id, day)
);
```

Здесь важная деталь. В архитектуре `service_id` nullable, но в первичном ключе нулевые значения недопустимы. Поэтому используем `0` как «весь продукт». Тогда при каждой оценке обновляются две строки: одна для сервиса (если он указан) и одна с `service_id = 0`. Это даёт быстрые графики и по продукту, и по сервису.

Обновление делаем через `INSERT ... ON CONFLICT DO UPDATE` в той же транзакции, что и вставка оценки.

Почему не `count(*)` по `rating`: при росте данных панель будет тормозить. Агрегат даёт ответ за миллисекунды.

### Релизы

```sql
CREATE TABLE release_mark (
    id           bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    product_id   bigint NOT NULL REFERENCES product(id),
    service_id   bigint REFERENCES service(id),
    version      text NOT NULL CHECK (length(version) <= 50),
    title        text NOT NULL CHECK (length(title) <= 200),
    description  text,
    released_at  timestamptz NOT NULL,
    created_by   bigint NOT NULL REFERENCES app_user(id),
    created_at   timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX release_product_time_idx ON release_mark (product_id, released_at);
```

Окна «до/после» не хранятся, их считаем на лету из `rating_daily` по `released_at`. Так правило обрезки окна (следующий релиз) меняется без миграции данных.

### Мониторинг

```sql
CREATE TABLE service_status (            -- последнее известное состояние
    service_id   bigint PRIMARY KEY REFERENCES service(id),
    status       text NOT NULL CHECK (status IN ('up', 'down', 'unknown')),
    response_ms  integer CHECK (response_ms >= 0),
    checked_at   timestamptz NOT NULL,
    received_at  timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE service_status_log (        -- история для процента доступности
    id           bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    service_id   bigint NOT NULL REFERENCES service(id),
    status       text NOT NULL CHECK (status IN ('up', 'down', 'unknown')),
    response_ms  integer,
    checked_at   timestamptz NOT NULL
);
CREATE INDEX service_status_log_idx ON service_status_log (service_id, checked_at DESC);

Для push-интеграции мониторинга Flyway `V2__monitoring_events.sql` добавляет журнал
`monitoring_event(event_id PK, resource_id, payload jsonb, checked_at, event_type,
service_id nullable, received_at)`. Журнал хранит все принятые события, включая
непривязанные к сервису; уникальный `event_id` обеспечивает идемпотентность.
В `service_status` добавлено поле `event_type`, чтобы при одинаковом `checked_at`
переход `STATUS_CHANGED` не мог быть отменён `HEARTBEAT`.
```

Две таблицы, потому что у них разные задачи. `service_status` отвечает на вопрос «жив ли сейчас», это одна строка на сервис. `service_status_log` хранит историю для процента доступности и графика отклика. Её придётся периодически чистить: например, держать 90 дней, а старше удалять. Если их API сам отдаёт историю, таблицу лога можно не делать.

Если мониторинг отдаёт только текущий статус, `service_status_log` надо отложить, и процент доступности убрать из MVP.

### Служебное

```sql
CREATE TABLE audit_log (                 -- кто что менял (ключи, владельцы, архивация)
    id          bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id     bigint REFERENCES app_user(id),
    action      text NOT NULL,
    object_type text NOT NULL,
    object_id   bigint,
    details     jsonb,
    created_at  timestamptz NOT NULL DEFAULT now()
);
```

Не в `architecture.md`, но полезно: через месяц спросят, кто перевыпустил ключ или добавил владельца. В `details` не пишем текст оценок и персональные данные.

## Что вынести в код, а не в БД

- Лимит «одна оценка за 14 дней» проверяется запросом по `rating_dedup_idx`, отдельной таблицы не нужно.
- Лимиты запросов (частота на ключ и IP) храним в памяти приложения. В MVP на одном сервере этого достаточно. Если понадобится общий лимит на несколько экземпляров, тогда Redis.
- Сессии ОКО: либо `spring_session` в PostgreSQL, либо подписанные cookie. Таблицу сессий сами не пишем.

## Открытые вопросы к структуре

1. **Нужен ли `is_admin` в `app_user`**, если админы задаются конфигом? Предлагаю убрать.
2. **Есть ли у мониторинга история?** От этого зависит `service_status_log`.
3. **Срок хранения `rating` и `audit_log`.** Пока ничего не удаляем. Нужно решение с заказчиком.
4. **Дедупликация через `rater_hash`** работает только если идентификатор стабилен. До SSO это cookie браузера, поэтому повторы возможны. Это уже описано в архитектуре.
