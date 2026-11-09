#!/usr/bin/env bash
# Pull a tested release bundle, pin both images and apply its Compose config.
set -euo pipefail
umask 077

deploy_dir=${OKO_DEPLOY_DIR:-/opt/oko}
pull_install_path=${OKO_PULL_INSTALL_PATH:-/usr/local/sbin/oko-pull}
cd "$deploy_dir"
mkdir -p state
exec 9>state/pull.lock
flock -n 9 || { echo 'Another update is in progress.'; exit 0; }

compose() {
    docker compose --project-directory "$deploy_dir" --env-file "$deploy_dir/.env" \
        -f "$deploy_dir/compose.yml" "$@"
}

# Read one non-secret setting without executing the .env file as shell code.
release_channel=$(python3 - "$deploy_dir/.env" <<'PY'
import re, sys
from pathlib import Path
values = {}
for line in Path(sys.argv[1]).read_text().splitlines():
    line = line.strip()
    if line and not line.startswith("#") and "=" in line:
        key, value = line.split("=", 1)
        values[key.strip()] = value.strip().strip('"').strip("'")
channel = values.get("OKO_RELEASE_IMAGE", "")
if not re.fullmatch(r"ghcr.io/[a-z0-9_.-]+/[a-z0-9_.-]+/release(?::(?:dev|main)|@sha256:[0-9a-f]{64})", channel):
    sys.exit("Set OKO_RELEASE_IMAGE=ghcr.io/<owner>/oko-tpu/release:dev (or :main) in .env")
print(channel)
PY
)

docker pull "$release_channel"
release_digest=$(docker image inspect --format '{{index .RepoDigests 0}}' "$release_channel")
[[ "$release_digest" == *@sha256:* ]] || { echo 'Release has no registry digest.' >&2; exit 1; }

current_release=$(cat state/current-release 2>/dev/null || true)
current_backend=$(cat state/current-image 2>/dev/null || true)
current_frontend=$(cat state/current-frontend-image 2>/dev/null || true)

apply_stack() {
    local before after
    before=$(compose ps -q backend) || return 1
    compose up -d --no-build --pull never --wait --wait-timeout 180 db backend || return 1
    after=$(compose ps -q backend) || return 1
    if [[ "$before" != "$after" || "$release_digest" != "$current_release" ]]; then
        compose up -d --no-build --pull never --no-deps --force-recreate --wait --wait-timeout 60 nginx || return 1
        compose up -d --no-build --pull never --no-deps --force-recreate --wait --wait-timeout 60 caddy || return 1
    else
        compose up -d --no-build --pull never --no-deps --wait --wait-timeout 60 nginx || return 1
        compose up -d --no-build --pull never --no-deps --wait --wait-timeout 60 caddy || return 1
    fi
    compose exec -T nginx wget -q -O /dev/null http://127.0.0.1/healthz || return 1
}

if [[ "$release_digest" == "$current_release" ]]; then
    export OKO_BACKEND_IMAGE="$current_backend" OKO_FRONTEND_IMAGE="$current_frontend"
    compose config --quiet
    apply_stack
    echo "Already current: $release_digest"
    exit 0
fi

stage=$(mktemp -d "$deploy_dir/state/release.XXXXXXXX")
container=''
cleanup() {
    if [[ -n "$container" ]]; then docker rm "$container" >/dev/null 2>&1 || true; fi
    rm -rf "$stage"
}
trap cleanup EXIT

container=$(docker create "$release_digest")
docker cp "$container:/release/." "$stage/"
docker rm "$container" >/dev/null
container=''

manifest=$(python3 - "$stage/release.json" <<'PY'
import json, re, sys
from pathlib import Path
data = json.loads(Path(sys.argv[1]).read_text())
sha = data["sha"]
if not re.fullmatch(r"[0-9a-f]{40}", sha):
    sys.exit("Invalid release SHA")
for name in ("backend", "frontend"):
    image = data[name]
    if not re.fullmatch(r"ghcr.io/[a-z0-9_.-]+/[a-z0-9_.-]+/" + name + ":" + sha, image):
        sys.exit("Invalid " + name + " image")
    print(image)
PY
)
mapfile -t images <<< "$manifest"
backend_tag=${images[0]}
frontend_tag=${images[1]}

# Download everything before changing the running stack or its config.
docker pull "$backend_tag"
docker pull "$frontend_tag"
backend_digest=$(docker image inspect --format '{{index .RepoDigests 0}}' "$backend_tag")
frontend_digest=$(docker image inspect --format '{{index .RepoDigests 0}}' "$frontend_tag")
[[ "$backend_digest" == *@sha256:* && "$frontend_digest" == *@sha256:* ]] || {
    echo 'Application image has no registry digest.' >&2; exit 1;
}

mkdir "$stage/old"
for file in compose.yml Caddyfile; do
    [[ -f "$deploy_dir/$file" ]] && cp -p "$deploy_dir/$file" "$stage/old/$file"
    install -m 0644 "$stage/$file" "$deploy_dir/$file.next"
done
mv -f "$deploy_dir/compose.yml.next" "$deploy_dir/compose.yml"
mv -f "$deploy_dir/Caddyfile.next" "$deploy_dir/Caddyfile"
export OKO_BACKEND_IMAGE="$backend_digest" OKO_FRONTEND_IMAGE="$frontend_digest"

if compose config --quiet && apply_stack; then
    [[ -n "$current_release" ]] && printf '%s\n' "$current_release" > state/previous-release
    [[ -n "$current_backend" ]] && printf '%s\n' "$current_backend" > state/previous-image
    [[ -n "$current_frontend" ]] && printf '%s\n' "$current_frontend" > state/previous-frontend-image
    printf '%s\n' "$release_digest" > state/current-release.next
    printf '%s\n' "$backend_digest" > state/current-image.next
    printf '%s\n' "$frontend_digest" > state/current-frontend-image.next
    mv -f state/current-release.next state/current-release
    mv -f state/current-image.next state/current-image
    mv -f state/current-frontend-image.next state/current-frontend-image
    # Future changes to the updater itself arrive through the same tested release.
    install -m 0755 "$stage/pull.sh" "$pull_install_path.next"
    mv -f "$pull_install_path.next" "$pull_install_path"
    echo "Deployed release: $release_digest"
else
    echo "Update failed: $release_digest" >&2
    for file in compose.yml Caddyfile; do
        if [[ -f "$stage/old/$file" ]]; then
            cp -p "$stage/old/$file" "$deploy_dir/$file.next"
            mv -f "$deploy_dir/$file.next" "$deploy_dir/$file"
        fi
    done
    if [[ -n "$current_backend" ]]; then
        export OKO_BACKEND_IMAGE="$current_backend" OKO_FRONTEND_IMAGE="$current_frontend"
        if compose config --quiet && apply_stack; then
            echo 'Previous release restored; database migrations were not reverted.' >&2
        else
            echo 'Rollback failed; operator intervention required.' >&2
        fi
    fi
    exit 1
fi
