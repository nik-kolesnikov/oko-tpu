FROM nginx:1.28-alpine
COPY deploy/vps/nginx.conf /etc/nginx/conf.d/default.conf
COPY frontend/dist-deploy/ /usr/share/nginx/html/
