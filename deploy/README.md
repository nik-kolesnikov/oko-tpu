# CI на кодлабе и pull-деплой на VPS

## Схема

```text
push / MR → GitLab Runner (сервер кодлаба)
              ├─ Maven verify: JAR, unit / integration / contract tests
              ├─ проверки сценария деплоя
              ├─ frontend: типы, тесты, артефакт frontend/dist-deploy (панель + виджет)
              └─ Jib: сборка Java-образа без Docker-демона
                   └─ main / dev → GitLab Container Registry
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

## 2. Container Registry на кодлабе

Сначала проверить в проекте `oko/tpu_rating` раздел **Deploy → Container Registry**
(в некоторых версиях **Packages & Registries**). Если доступен — скопировать оттуда
адрес Registry и путь образа. Git URL `https://codelab.tpu.ru/oko/tpu_rating.git`
не является адресом Registry.

Если Registry отключён, нужен администратор GitLab. Для установки Linux package
(Omnibus) возможен Registry на том же домене и отдельном порту. В существующий
`/etc/gitlab/gitlab.rb` добавить/изменить, не заменяя весь файл:

```ruby
registry_external_url 'https://codelab.tpu.ru:5050'
# Указать фактические файлы действующего сертификата codelab.tpu.ru:
registry_nginx['ssl_certificate'] = '/etc/gitlab/ssl/codelab.tpu.ru.crt'
registry_nginx['ssl_certificate_key'] = '/etc/gitlab/ssl/codelab.tpu.ru.key'
```

Применить `sudo gitlab-ctl reconfigure`, проверить `sudo gitlab-ctl status registry`.
Открыть TCP 5050 для runner и VPS. Если GitLab в Docker, также пробросить этот порт
в его Compose и править его постоянный `gitlab.rb`. Если GitLab установлен через
Helm или внешний reverse proxy, конфигурация другая — не выполнять Omnibus-команды.
Существующий HTTPS кодлаба не перенастраивать; сертификат должен обновляться и для
Registry. Включить Container Registry в настройках видимости/функций проекта.

Проверка с runner и VPS:

```bash
curl -I https://codelab.tpu.ru:5050/v2/
```

Ожидается `401 Unauthorized` и заголовок `Docker-Distribution-Api-Version`:
это рабочий Registry, требующий входа. Ошибка TLS или timeout — не норма.
Адрес `:5050` — выбранный вариант настройки, а не уже подтверждённая конфигурация
ТПУ. Подробнее: [администрирование Registry](https://docs.gitlab.com/administration/packages/container_registry/).

## 3. Runner кодлаба

В проекте **Settings → CI/CD → Runners → Instance** доступны общие runners
`#52 codelab-runner` и `#48 runner.tpu.ru`. Нужно оставить включённым
**Turn on instance runners for this project**. Свой project runner и доступ по SSH
к серверу кодлаба не требуются. Задания CI оставлены без тега, чтобы GitLab
мог назначить их на любой доступный runner с `run_untagged`.

Первый запуск на #52 показал, что его контейнер не может соединиться с
`https://codelab.tpu.ru` для получения исходников, а DinD там не получил
необходимые права. Если проблема повторится и на #48, её исправляет
администратор общих runners.

Runner #48 получает исходники, но оба общих runner отказали в запуске DinD
из-за ограничений прав. Поэтому CI запускает PostgreSQL как GitLab service и
собирает образ Maven-плагином Jib без Docker-демона.
Если job останется pending, проверить доступность instance runners. Runner #52
не смог скачать исходники из GitLab; для стабильной работы администратору
кодлаба нужно исправить его сетевой доступ или выделить runner #48 отдельным
тегом и закрепить этот тег в CI.

## 4. Настройки проекта и первый pipeline

`.gitlab-ci.yml` — основной CI. Старый `.github/workflows/backend.yml` сохранён
как историческая конфигурация; GitLab его не исполняет.

- Защитить `main` и `dev`: прямой push запрещён, слияние через MR.
- Включить **Pipelines must succeed** перед слиянием.
- Включить instance runners для проекта; задания не требуют специального тега.
- Включить Registry. Переменные `CI_REGISTRY`, `CI_REGISTRY_IMAGE`,
  `CI_REGISTRY_USER`, `CI_REGISTRY_PASSWORD` GitLab предоставляет сам.
- Не добавлять в CI пароль БД, HMAC-секрет или SSH-ключ VPS.

В MR запускаются тесты и сборка образа, но образ для VPS не публикуется.
После merge в `dev` успешный pipeline публикует
`$CI_REGISTRY_IMAGE/backend:<полный SHA коммита>` и `:dev`; для `main` аналогично.
Публикация в каждой ветке сериализована, перед обновлением тега ветки проверяется,
что коммит остаётся её вершиной. Старый pipeline не заменяет канал устаревшим коммитом.
Не удалять из Registry текущий и предыдущий SHA/digest при настройке очистки.

`backend-verify` запускает также `*IT` через Surefire: PostgreSQL запускается
как GitLab service. Локально те же тесты используют Testcontainers. JUnit-отчёты
и JAR сохраняются на 7 дней. Образ собирается из тех же исходников и зависимостей
после успешных тестов при помощи [Jib](https://github.com/GoogleContainerTools/jib/tree/master/jib-maven-plugin).
На MR Jib создаёт tar-архив для проверки сборки; в `dev`/`main` отправляет образ
непосредственно в Registry. Пароль Registry читается из переменных GitLab в
временном Docker config внутри job и не передаётся в аргументах процесса.

## 5. Подготовить VPS

Создать DNS A-запись домена приложения на IP VPS. AAAA добавлять только при рабочем
IPv6. На firewall провайдера разрешить входящие TCP 80/443 и SSH с адреса
администратора. PostgreSQL, backend и nginx наружу портов не публикуют.

HTTPS завершает Caddy: он получает и продлевает сертификат. nginx доступен только
во внутренней Docker-сети и передаёт backend `X-Forwarded-Proto: https`.
Не добавлять ему публичный `ports`: конфигурация доверяет этой топологии.

Создать в GitLab **Settings → Repository → Deploy tokens** токен `oko-vps-pull`
только со scope `read_registry`. Дать срок жизни и запланировать продление.
Пароль CI job для VPS не подходит — он временный.
Право чтения Registry и способ входа описаны в [GitLab Registry authentication](https://docs.gitlab.com/user/packages/container_registry/authenticate_with_container_registry/).

На VPS:

```bash
sudo install -d -m 0700 /opt/oko
sudo install -d -m 0755 /opt/oko/static
sudo docker login codelab.tpu.ru:5050 --username <имя-deploy-token>
```

Ввести token в запросе пароля. Использовать фактический адрес Registry из раздела 2.
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
| `OKO_BACKEND_IMAGE` | Например `codelab.tpu.ru:5050/oko/tpu_rating/backend:dev`; взять реальный Registry path |
| `OKO_DOMAIN` | Домен VPS, без схемы и пути |
| `OKO_ACME_EMAIL` | Email администратора для TLS-сертификата |
| `OKO_DB_PASSWORD` | Случайный пароль, можно сгенерировать `openssl rand -hex 32` |
| `OKO_RATER_HMAC_SECRET` | Отдельный постоянный секрет: `openssl rand -hex 48` |
| `OKO_ADMIN_LOGINS` | Логины ТПУ через запятую, либо пусто |

HMAC-секрет сохранять при обновлениях. Пароль PostgreSQL применяется к новому тому:
его изменение в `.env` не меняет пароль уже созданной БД.

В текущем backend ещё нет реального SSO: профиль `prod` запускается, но вход
в панель вернёт 503. Не включать открытую dev-заглушку на публичном VPS.
Панель и виджет — статика из `frontend/`; как её обновлять, см. ниже.

### Панель и виджет (статика)

Timer обновляет только backend. Статику CI собирает в джобе `frontend-verify`
(артефакт `frontend/dist-deploy`: `panel/` и `widget/v1/`). Обновление вручную:

1. Кодлаб → Build → Pipelines → последний пайплайн `dev` → джоба `frontend-verify` → Download artifacts.
2. На VPS:

```bash
unzip -q artifacts.zip -d /tmp/oko-static
sudo cp -a /tmp/oko-static/frontend/dist-deploy/. /opt/oko/static/
rm -rf /tmp/oko-static artifacts.zip
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
