# ---- Build stage ----
FROM node:20-alpine AS build
WORKDIR /app
COPY package*.json ./
RUN npm install
COPY . .
RUN npm run build

# ---- Runtime stage: non-root nginx listening on 8080 ----
FROM nginxinc/nginx-unprivileged:1.30.6-alpine
# Patch an OS library that is fixed in Alpine but not yet in the base image (Trivy) – docs/Issues.md #10.
# Only this library: upgrading nginx itself via apk would replace the nginx.org build with Alpine's.
USER root
RUN apk upgrade --no-cache brotli-libs
USER 101
ENV API_GATEWAY_URL=http://api-gateway:8080
COPY nginx/default.conf.template /etc/nginx/templates/default.conf.template
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 8080
