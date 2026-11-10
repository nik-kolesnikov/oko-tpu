# Переход VPS на автоматическое обновление backend, frontend и Compose

Эта инструкция применяется **один раз** к уже работающему VPS ОКО. После перехода
таймер `oko-pull.timer` будет забирать из GHCR единый выпуск: образ backend,
образ панели и виджета, `compose.yml`, `Caddyfile` и новую версию
`pull.sh`. Доступ VPS к GitHub через Git и ручная загрузка `frontend-static.zip`
больше не нужны.

Команды с `<...>` требуют ваших значений. Пароли и содержимое `/opt/oko/.env`
никому не отправляйте. Если у вас сохранены прежние инструкции по ручной
загрузке `frontend-static.zip`, после перехода используйте эту инструкцию.

## 1. Дождитесь выпуска в GitHub

Сначала PR с этими изменениями должен быть смёржен в `dev`. Откройте GitHub →
Actions → последний запуск `ci` для `dev`. Он должен завершиться успешно,
включая задачи `backend-image`, `frontend-image` и `release-bundle`.
Именно `release-bundle` публикует тег
`ghcr.io/oko-tpu/oko-tpu/release:dev` после обеих сборок и проверок.

Не переходите к следующему шагу, если `release-bundle` ещё нет или он упал:
старый работающий сервер лучше оставить как есть.

## 2. Скопируйте новый скрипт на VPS

На **своём компьютере** в обновлённой копии репозитория проверьте, что вы
используете тот же коммит `dev`, для которого прошёл CI:

```bash
git fetch origin
git show origin/dev:deploy/vps/pull.sh > /tmp/oko-pull-new
git show origin/dev:deploy/vps/oko-pull.service > /tmp/oko-pull-new.service
scp -P <SSH-порт> /tmp/oko-pull-new /tmp/oko-pull-new.service <SSH-пользователь>@<VPS-IP>:~/
```

Если локальный `dev` содержит незакоммиченные файлы, `git show origin/dev`
берёт нужные версии без переключения ветки и без изменения этих файлов.

На **VPS** остановите только таймер и сохраните текущие файлы. Контейнеры при
этом продолжают работать:

```bash
sudo systemctl stop oko-pull.timer
sudo systemctl status oko-pull.service --no-pager
sudo install -d -m 0700 /opt/oko/backup-before-auto-update
sudo cp -p /opt/oko/compose.yml /opt/oko/backup-before-auto-update/
sudo cp -p /opt/oko/Caddyfile /opt/oko/backup-before-auto-update/
sudo cp -p /usr/local/sbin/oko-pull /opt/oko/backup-before-auto-update/pull.sh
sudo install -m 0755 ~/oko-pull-new /usr/local/sbin/oko-pull
sudo install -m 0644 ~/oko-pull-new.service /etc/systemd/system/oko-pull.service
sudo systemctl daemon-reload
```

Если `oko-pull.service` ещё выполняется, дождитесь его завершения перед
заменой скрипта. `compose.yml` вручную копировать не нужно: новый скрипт
получит его из выпуска.

## 3. Добавьте адреса двух новых пакетов

Откройте на VPS `sudoedit /opt/oko/.env` и добавьте строки:

```dotenv
OKO_FRONTEND_IMAGE=ghcr.io/oko-tpu/oko-tpu/frontend:dev
OKO_RELEASE_IMAGE=ghcr.io/oko-tpu/oko-tpu/release:dev
```

Существующие `OKO_BACKEND_IMAGE`, `OKO_DB_PASSWORD`,
`OKO_RATER_HMAC_SECRET`, домен и настройки входа оставьте со своими
текущими значениями. Скрипт закрепляет оба образа по digest во время запуска;
строка `OKO_FRONTEND_IMAGE` нужна также для ручной проверки Compose.

Проверьте доступ root к новому пакету:

```bash
sudo docker pull ghcr.io/oko-tpu/oko-tpu/release:dev
```

Если получаете `denied` или `unauthorized`, настройте для root вход в GHCR
токеном с `read:packages` и правом читать пакеты `release`, `frontend` и
`backend`; после этого повторите `docker pull`. При `manifest unknown`
проверьте адрес образа и успешность `release-bundle` из шага 1.

## 4. Примените и проверьте

```bash
sudo systemctl start oko-pull.service
sudo systemctl status oko-pull.service --no-pager
sudo journalctl -u oko-pull.service -n 100 --no-pager
sudo cat /opt/oko/state/current-release
sudo cat /opt/oko/state/current-image
sudo cat /opt/oko/state/current-frontend-image
sudo docker compose --project-directory /opt/oko --env-file /opt/oko/.env -f /opt/oko/compose.yml ps
```

Сервис должен завершиться успешно; `db`, `backend`, `nginx` и `caddy`
должны работать. Адреса в файлах `state/current-*` заканчиваются на
`@sha256:...`. У `nginx` теперь образ `.../frontend@sha256:...`, а не
том `/opt/oko/static`. Файл `/opt/oko/compose.yml` уже новая версия.

С другого компьютера откройте панель и проверьте код виджета:

```bash
curl -I https://<домен>/
curl -I https://<домен>/widget/v1/loader.js
```

В браузере дополнительно проверьте вход, создание продукта и страницу
статистики. `curl -I` подтверждает доставку статики и HTTPS, но не вход.

Если всё работает:

```bash
sudo systemctl enable --now oko-pull.timer
sudo systemctl list-timers oko-pull.timer
```

На следующих тиках таймер обновит все части только после успешного
`release-bundle`. Если GHCR недоступен или загрузка не удалась, работающие
контейнеры остаются на прежнем выпуске.

## При ошибке и откат

Скрипт сам возвращает прежний `compose.yml`, `Caddyfile` и образы, если
новый комплект не запустился. Он завершится с ошибкой, а подробности будут в
`journalctl -u oko-pull.service`. Миграции Flyway не откатываются; перед
будущими несовместимыми изменениями БД нужна отдельная резервная копия.

Если выпуск уже успешно применён, но позже обнаружена проблема:

1. `sudo systemctl stop oko-pull.timer`.
2. Посмотрите предыдущий digest: `sudo cat /opt/oko/state/previous-release`.
3. В `sudoedit /opt/oko/.env` временно замените
   `OKO_RELEASE_IMAGE=...:dev` на полную строку из `previous-release`
   (формат `ghcr.io/.../release@sha256:...`).
4. Выполните `sudo systemctl start oko-pull.service` и проверьте
   `sudo journalctl -u oko-pull.service -n 100 --no-pager`.
5. После решения проблемы верните `OKO_RELEASE_IMAGE=...:dev` и только затем
   включите таймер.

Если это **самый первый переход** и предыдущего release digest ещё нет,
остановите таймер и восстановите файлы из
`/opt/oko/backup-before-auto-update/`. Старую статику в `/opt/oko/static`
скрипт не удаляет. Выполните в root-shell:

```bash
sudo -i
systemctl stop oko-pull.timer
cp -p /opt/oko/backup-before-auto-update/compose.yml /opt/oko/compose.yml
cp -p /opt/oko/backup-before-auto-update/Caddyfile /opt/oko/Caddyfile
install -m 0755 /opt/oko/backup-before-auto-update/pull.sh /usr/local/sbin/oko-pull
export OKO_BACKEND_IMAGE="$(cat /opt/oko/state/previous-image)"
docker compose --project-directory /opt/oko --env-file /opt/oko/.env -f /opt/oko/compose.yml up -d --no-build --pull never --wait db backend
docker compose --project-directory /opt/oko --env-file /opt/oko/.env -f /opt/oko/compose.yml up -d --no-build --pull never --no-deps --force-recreate nginx caddy
docker compose --project-directory /opt/oko --env-file /opt/oko/.env -f /opt/oko/compose.yml ps
exit
```

Если `previous-image` отсутствует, найдите сохранённый до перехода digest
backend в записях запуска или в выводе `docker image ls`.
До исправления причины оставьте таймер выключенным.
Не запускайте `docker compose down -v`: эта команда удаляет тома БД и
сертификатов.
