FROM alpine:3.20
COPY deploy/vps/compose.yml /release/compose.yml
COPY deploy/vps/Caddyfile /release/Caddyfile
COPY deploy/vps/pull.sh /release/pull.sh
COPY release.json /release/release.json
CMD ["true"]
