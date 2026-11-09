# CI на GitHub Actions и pull-деплой на VPS

Репозиторий переехал с кодлаба ТПУ на GitHub (`oko-tpu/oko-tpu`): Registry кодлаба
был недоступен ни раннерам, ни снаружи (порт 5050 закрыт), публикация образа не работала.

## Схема

```text
push / PR → GitHub Actions
              ├─ backend-verify: Maven verify, unit / integration (Testcontainers) / contract tests
              ├─ deploy-verify: shellcheck, тесты pull.sh, docker compose config
              ├─ frontend-verify: типы, тесты, артефакт frontend-static (панель + виджет)
              ├─ widget-e2e: виджет в Chromium на HTML / PHP / React
              └─ backend-image: Jib без Docker-демона
                   └─ push в main / dev → ghcr.io/<owner>/oko-tpu/backend
                                      ↑ исходящее соединение с VPS
                              systemd timer, каждые 5 минут
                                      ↓
                         PostgreSQL + backend + nginx + Caddy
```

CI не подключается к VPS и не хранит его SSH-ключи. VPS скачивает готовый образ,
проверяет его здоровье и сохраняет digest текущей и предыдущей версии.
По умолчанию он следит за `dev`; `main` выбирается заменой тега в `.env`.
Исходники на VPS не компилируются. Изменения Compose, nginx и скрипта ставятся
администратором отдельно, автоматически обновляется только backend.

Инструкция для VPS рассчитана на чистую Ubuntu 24.04 LTS x86_64 с systemd и sudo.
Runner кодлаба и VPS должны иметь одну архитектуру: CI пока не собирает multi-platform образ.
Для приложения — 2 vCPU / 4 ГБ RAM / 50+ ГБ SSD.

## 1. Docker на VPS

Выполнить на VPS. Команды предназначены для чистой Ubuntu, существующую
установку Docker не удалять. Нужны исходящий HTTPS, DNS, доступ к Docker Hub,
кодлабу и его Registry.

```bash
sudo apt-get update
sudo apt-get install -y ca-certificates curl python3 openssl
sudo install -m 0755 -d /etc/apt/keyrings
sudo curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc
sudo chmod a+r /etc/apt/keyrings/docker.asc
. /etc/os-release
sudo tee /etc/apt/sources.list.d/docker.sources >/dev/null <<EOF
Types: deb
URIs: https://download.docker.com/linux/ubuntu
Suites: ${UBUNTU_CODENAME:-$VERSION_CODENAME}
Components: stable
Architectures: $(dpkg --print-architecture)
Signed-By: /etc/apt/keyrings/docker.asc
EOF
sudo apt-get update
sudo apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
sudo systemctl enable --now docker
sudo docker version
sudo docker compose version
```

Используется актуальный Compose plugin с `up --wait`; старый `docker-compose` v1
не подходит. Основа команд — [инструкция Docker для Ubuntu](https://docs.docker.com/engine/install/ubuntu/).

## 2. GitHub Container Registry

Образы публикуются в GHCR того же владельца, что и репозиторий:
`ghcr.io/<owner>/oko-tpu/backend` (имя в нижнем регистре): для `oko-tpu/oko-tpu` — `ghcr.io/oko-tpu/oko-tpu/backend`.
Настраивать Registry не нужно: workflow входит в GHCR встроенным `GITHUB_TOKEN`
с правом `packages: write`. После первой публикации пакет виден в профиле владельца →
Packages. Для приватного репозитория пакет тоже приватный.

Проверка с VPS (без входа ожидается `401`/`UNAUTHORIZED` — Registry отвечает, нужен токен):

```bash
curl -sI https://ghcr.io/v2/ | head -1
```

## 3. GitHub Actions

Workflow — `.github/workflows/ci.yml`, раннеры `ubuntu-latest` (Docker есть,
поэтому интеграционные тесты используют Testcontainers). Для приватного
репозитория на бесплатном плане — 2000 минут Actions в месяц; полный прогон
занимает несколько минут.

## 4. Настройки репозитория и первый pipeline

- Settings → Branches: защитить `main` и `dev` — слияние только через PR,
  обязательные проверки `backend-verify`, `deploy-verify`, `frontend-verify`, `widget-e2e`, `backend-image`.
- Settings → General: ветка по умолчанию — `dev` (PR открываются в неё).
- Settings → Collaborators: добавить команду.
- Не добавлять в Actions пароль БД, HMAC-секрет или SSH-ключ VPS.

В PR запускаются тесты и сборка образа в tar, образ не публикуется.
После merge в `dev` успешный pipeline публикует
`ghcr.io/<owner>/oko-tpu/backend:<полный SHA коммита>` и `:dev`; для `main` аналогично.
Публикация в каждой ветке сериализована (`concurrency`), перед публикацией проверяется,
что коммит остаётся вершиной ветки. Старый pipeline не заменяет канал устаревшим коммитом.
Не удалять из GHCR текущий и предыдущий SHA/digest при настройке очистки.

Образ собирается из тех же исходников после успешных тестов при помощи
[Jib](https://github.com/GoogleContainerTools/jib/tree/master/jib-maven-plugin).
Учётные данные Registry Jib берёт из Docker config, который пишет `docker/login-action`;
пароль не передаётся в аргументах процесса.

## 5. Подготовить VPS

Создать DNS A-запись домена приложения на IP VPS. AAAA добавлять только при рабочем
IPv6. На firewall провайдера разрешить входящие TCP 80/443 и SSH с адреса
администратора. PostgreSQL, backend и nginx наружу портов не публикуют.

HTTPS завершает Caddy: он получает и продлевает сертификат. nginx доступен только
во внутренней Docker-сети и передаёт backend `X-Forwarded-Proto: https`.
Не добавлять ему публичный `ports`: конфигурация доверяет этой топологии.

Для скачивания образа VPS нужен токен GitHub: владелец репозитория создаёт
**Settings → Developer settings → Personal access tokens → Tokens (classic)** с одним
scope `read:packages`, со сроком жизни и напоминанием о продлении.
`GITHUB_TOKEN` из Actions для VPS не подходит — он временный.
Подробнее: [Working with the Container registry](https://docs.github.com/en/packages/working-with-a-github-packages-registry/working-with-the-container-registry).

На VPS:

```bash
sudo install -d -m 0700 /opt/oko
sudo install -d -m 0755 /opt/oko/static
sudo docker login ghcr.io --username <github-логин владельца токена>
```

Ввести token в запросе пароля. 
Учётные данные сохраняются у root, от которого работает timer. Не вводить пароль
в аргумент `-p`, shell history или `.env` проекта.

Передать каталог `deploy/` на VPS, например через `scp` в `~/oko-deploy`, затем:

```bash
sudo install -m 0644 ~/oko-deploy/vps/compose.yml /opt/oko/compose.yml
sudo install -m 0644 ~/oko-deploy/vps/nginx.conf /opt/oko/nginx.conf
sudo install -m 0644 ~/oko-deploy/vps/Caddyfile /opt/oko/Caddyfile
sudo cp -a ~/oko-deploy/static/. /opt/oko/static/
sudo install -m 0600 ~/oko-deploy/vps/.env.example /opt/oko/.env
sudo install -m 0755 ~/oko-deploy/vps/pull.sh /usr/local/sbin/oko-pull
sudo install -m 0644 ~/oko-deploy/vps/oko-pull.service /etc/systemd/system/
sudo install -m 0644 ~/oko-deploy/vps/oko-pull.timer /etc/systemd/system/
sudoedit /opt/oko/.env
```

Заполнить:

| Переменная | Значение |
|---|---|
| `OKO_BACKEND_IMAGE` | `ghcr.io/oko-tpu/oko-tpu/backend:dev` (имя в нижнем регистре) |
| `OKO_DOMAIN` | Домен VPS, без схемы и пути |
| `OKO_ACME_EMAIL` | Email администратора для TLS-сертификата |
| `OKO_DB_PASSWORD` | Случайный пароль, можно сгенерировать `openssl rand -hex 32` |
| `OKO_RATER_HMAC_SECRET` | Отдельный постоянный секрет: `openssl rand -hex 48` |
| `OKO_MONITORING_EVENT_TOKEN` | Общий токен отправителя мониторинга: `openssl rand -hex 32`; тот же секрет передать как `MONITORING_EVENT_TOKEN` |
| `OKO_ADMIN_LOGINS` | Логины ТПУ через запятую, либо пусто |
| `OKO_ADMIN_LOGIN` | Вход админа по паролю до SSO: логин (например `oko-admin`), либо пусто |
| `OKO_ADMIN_PASSWORD` | Пароль к нему, не короче 16 символов: `openssl rand -base64 24`, либо пусто |

HMAC-секрет сохранять при обновлениях. Пароль PostgreSQL применяется к новому тому:
его изменение в `.env` не меняет пароль уже созданной БД.

В текущем backend ещё нет реального SSO. Без `OKO_ADMIN_LOGIN`/`OKO_ADMIN_PASSWORD`
профиль `prod` запускается, но вход в панель вернёт 503. С ними кнопка «Войти через ТПУ»
ведёт на страницу входа админа по паролю: один логин, он же админ, лимит 5 попыток в минуту
с IP и 20 всего. Задать обе переменные или ни одной: если задана одна или пароль короче
16 символов, backend не стартует. Открытую dev-заглушку (`OKO_PROFILE=dev`) на публичном
VPS не включать: она пускает кого угодно под любым логином.

Публиковать ли панель до SSO — пункт 3 «Предложений», решает Алина. Если не публикуем,
эти две переменные на публичном VPS не задаются.
Панель и виджет — статика из `frontend/`; как её обновлять, см. ниже.

### Панель и виджет (статика)

Timer обновляет только backend. Статику CI собирает в джобе `frontend-verify`
(артефакт `frontend-static`: `panel/` и `widget/v1/`). Обновление вручную:

1. GitHub → Actions → последний успешный запуск `ci` на `dev` → Artifacts → `frontend-static` (zip).
2. Скопировать zip на VPS и:

```bash
unzip -q frontend-static.zip -d /tmp/oko-static
sudo cp -a /tmp/oko-static/. /opt/oko/static/
rm -rf /tmp/oko-static frontend-static.zip
```

nginx читает `/opt/oko/static` напрямую, перезапуск не нужен. Если `panel/` и
`widget/v1/` обновились одновременно со сменой API, сначала дождаться нового backend.

## 6. Первый запуск и таймер

Сначала дождаться успешного pipeline ветки `dev`, чтобы тег `:dev` существовал.
На VPS выполнить:

```bash
cd /opt/oko
sudo docker compose --env-file .env -f compose.yml config --quiet
# Инфраструктурные образы ставятся отдельно, timer обновляет только backend.
sudo docker compose --env-file .env -f compose.yml pull db nginx caddy
sudo systemctl daemon-reload
sudo systemctl start oko-pull.service
sudo journalctl -u oko-pull.service -n 100 --no-pager
sudo docker compose --env-file .env -f compose.yml ps
curl -I https://<домен-приложения>/
sudo systemctl enable --now oko-pull.timer
sudo systemctl list-timers oko-pull.timer
```

Timer проверяет обновления через 2 минуты после загрузки и каждые 5 минут после
завершения предыдущей проверки, с разбросом до 30 секунд. `flock` исключает
одновременные ручные и автоматические обновления.

Если Registry недоступен, работающий стек не изменяется. После скачивания образ
фиксируется по digest. При неизменном образе работающие контейнеры не перезапускаются.
При обновлении проверяется здоровье backend, nginx пересоздаётся для нового IP
backend, затем проверяется цепочка nginx → backend. TLS/DNS снаружи надо проверить
командой `curl` выше; внутренний healthcheck не подтверждает выдачу сертификата.

Для изменения интервала:

```bash
sudo systemctl edit oko-pull.timer
```

```ini
[Timer]
OnUnitInactiveSec=
OnUnitInactiveSec=10min
```

Применить `sudo systemctl daemon-reload` и `sudo systemctl restart oko-pull.timer`.

## 7. Смена ветки, остановка обновлений и откат

Для перехода на `main`: дождаться её успешного CI, изменить в `/opt/oko/.env`
только `OKO_BACKEND_IMAGE` с `:dev` на `:main`, запустить
`sudo systemctl start oko-pull.service`. Автопубликация настроена для `dev` и `main`.
Произвольную ветку нужно сначала добавить в правила публикации CI;
на конкретный SHA/digest можно переключиться без изменения CI.

Остановить автоматические обновления:

```bash
sudo systemctl stop oko-pull.timer
# Если обновление уже выполняется, дождаться его окончания перед ручным откатом.
sudo systemctl status oko-pull.service
sudo cat /opt/oko/state/current-image
sudo cat /opt/oko/state/previous-image
```

Для ручного отката записать digest из `previous-image` в `OKO_BACKEND_IMAGE`, затем
`sudo systemctl start oko-pull.service`. Тег ветки вернёт новые версии, поэтому
оставить digest до исправления проблемы. После исправления вернуть `:dev` или
`:main` и запустить timer.

Если новый backend не проходит healthcheck, скрипт пытается восстановить предыдущий
успешный образ и возвращает ошибку в systemd. При первом запуске предыдущей версии
нет. При недоступном Registry ручной откат выполнять локальным Compose с
`OKO_BACKEND_IMAGE=<сохранённый digest>` и `--pull never`, повторив этапы `apply_image`
из `vps/pull.sh`; автоматический скрипт всегда начинает с проверки Registry.

**Миграции Flyway автоматически не откатываются.** Автообновления допустимы только
с обратно совместимыми миграциями. Перед несовместимой миграцией остановить timer,
сделать бэкап и провести плановое обновление. Перезапуск backend прерывает сессии
и может дать короткую недоступность — это один экземпляр, не zero-downtime deploy.

## 8. Эксплуатация

```bash
sudo journalctl -u oko-pull.service --since today
cd /opt/oko
sudo docker compose logs --tail=100 backend
sudo docker compose logs --tail=100 caddy
sudo docker system df
```

Не выполнять `docker compose down -v`: это удалит данные PostgreSQL и сертификаты.
Не удалять текущий/предыдущий образы до окончания периода отката. Инфраструктурные
образы PostgreSQL/nginx/Caddy обновлять вручную в плановое окно.

Настроить ежедневный бэкап БД с хранением 14 дней и копией вне VPS. Команда дампа
из root-shell (предварительно создать защищённый каталог `/var/backups/oko`):

```bash
cd /opt/oko
umask 077
docker compose exec -T db pg_dump -U oko -Fc oko > /var/backups/oko/oko-$(date -u +%F-%H%M%S).dump
```

Дамп также содержит отзывы и данные пользователей; не добавлять его в Git.
Проверить восстановление в отдельной БД до пилотного запуска. Мониторинг ошибок
`oko-pull.service`, свободного места и внешнего HTTPS подключить к принятому каналу
уведомлений администратора.
