#!/usr/bin/env bash
# Only backend images are updated automatically; infrastructure is maintained explicitly.
set -euo pipefail
umask 077

deploy_dir=${OKO_DEPLOY_DIR:-/opt/oko}
cd "$deploy_dir"
mkdir -p state
exec 9>state/pull.lock
flock -n 9 || { echo 'Another update is in progress.'; exit 0; }

compose() {
    docker compose --project-directory "$deploy_dir" --env-file "$deploy_dir/.env" \
        -f "$deploy_dir/compose.yml" "$@"
}

# Parse Compose interpolation rather than sourcing .env as shell code.
channel=$(compose config --format json | python3 -c \
    'import json,sys; print(json.load(sys.stdin)["services"]["backend"]["image"])')

# A failed pull leaves running containers untouched. No build or git pull on the VPS.
docker pull "$channel"
candidate=$(docker image inspect --format '{{index .RepoDigests 0}}' "$channel")
[[ "$candidate" == *@sha256:* ]] || { echo 'Image has no registry digest.' >&2; exit 1; }

current=''
if [[ -f state/current-image ]]; then
    current=$(cat state/current-image)
fi

# Pin the update to the downloaded digest even if the channel changes during deployment.
export OKO_BACKEND_IMAGE="$candidate"

apply_image() {
    local before after
    local nginx_options=()
    before=$(compose ps -q backend) || return 1
    compose up -d --no-build --pull never --wait --wait-timeout 180 db backend || return 1
    after=$(compose ps -q backend) || return 1
    # Recreate nginx to resolve the replacement backend container's new IP.
    # Also handles changes to .env with an unchanged image digest.
    if [[ "$before" != "$after" || "$OKO_BACKEND_IMAGE" != "$current" ]]; then
        nginx_options+=(--force-recreate)
    fi
    compose up -d --no-build --pull never --no-deps "${nginx_options[@]}" --wait --wait-timeout 60 nginx || return 1
    compose up -d --no-build --pull never --no-deps --wait --wait-timeout 60 caddy || return 1
    # Exercise nginx -> backend, not only the JVM's local healthcheck.
    compose exec -T nginx wget -q -O /dev/null http://127.0.0.1/healthz || return 1
}

if apply_image; then
    if [[ -n "$current" && "$current" != "$candidate" ]]; then
        printf '%s\n' "$current" > state/previous-image.tmp
        mv state/previous-image.tmp state/previous-image
    fi
    printf '%s\n' "$candidate" > state/current-image.tmp
    mv state/current-image.tmp state/current-image
    echo "Deployed: $candidate"
else
    echo "Update failed: $candidate" >&2
    if [[ -n "$current" && "$current" != "$candidate" ]]; then
        export OKO_BACKEND_IMAGE="$current"
        if apply_image; then
            echo "Previous application image restored: $current" >&2
        else
            echo 'Rollback failed; operator intervention required.' >&2
        fi
    fi
    # Database migrations are NOT reverted. Releases must use backward-compatible migrations.
    exit 1
fi
