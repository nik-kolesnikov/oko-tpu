-- ОКО ТПУ: начальная схема. Источник — docs/db.md.
-- Отличия от db.md:
--   * app_user.is_admin не создаём: админы задаются конфигом (OKO_ADMIN_LOGINS), иначе два источника правды;
--   * добавлен индекс product_owner_user_idx — список «моих продуктов» для владельца.
-- Применённую миграцию не правим, изменения — новой V{n+1}.

-- ── Пользователи и владельцы ──
CREATE TABLE app_user (
    id          bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    login       text NOT NULL UNIQUE,          -- логин ТПУ
    full_name   text NOT NULL,
    email       text,
    created_at  timestamptz NOT NULL DEFAULT now()
);

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
-- Уникальность URL только у неархивных продуктов: после архивации можно завести такой же заново.
CREATE UNIQUE INDEX product_url_uq ON product (lower(url)) WHERE status <> 'archived';

CREATE TABLE product_owner (
    product_id  bigint NOT NULL REFERENCES product(id),
    user_id     bigint NOT NULL REFERENCES app_user(id),
    added_at    timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (product_id, user_id)
);
CREATE INDEX product_owner_user_idx ON product_owner (user_id);

-- ── Сервисы ──
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
-- Одна проверка мониторинга не может относиться к двум сервисам.
CREATE UNIQUE INDEX service_monitoring_uq ON service (monitoring_ref)
    WHERE monitoring_ref IS NOT NULL;
CREATE INDEX service_product_idx ON service (product_id);

-- ── Ключи и разрешённые сайты ──
CREATE TABLE api_key (
    id          bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    product_id  bigint NOT NULL REFERENCES product(id),
    key_type    text NOT NULL CHECK (key_type IN ('public', 'secret')),
    key_prefix  text NOT NULL,                  -- начало ключа для узнавания в списке
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

-- ── Настройки виджета ──
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

-- ── Оценки ──
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

-- ── Агрегат ──
-- service_id = 0 — строка «весь продукт». При каждой оценке обновляется она и, если указан сервис, строка сервиса.
CREATE TABLE rating_daily (
    product_id   bigint NOT NULL REFERENCES product(id),
    service_id   bigint NOT NULL DEFAULT 0,
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

-- ── Релизы ──
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

-- ── Мониторинг ──
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

-- ── Служебное ──
CREATE TABLE audit_log (                 -- кто что менял (ключи, владельцы, архивация)
    id          bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id     bigint REFERENCES app_user(id),
    action      text NOT NULL,
    object_type text NOT NULL,
    object_id   bigint,
    details     jsonb,
    created_at  timestamptz NOT NULL DEFAULT now()
);
