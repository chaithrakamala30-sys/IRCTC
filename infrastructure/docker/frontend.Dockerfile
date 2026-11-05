FROM node:22-alpine AS build
WORKDIR /workspace
COPY package.json package-lock.json ./
COPY tsconfig.base.json ./
COPY apps ./apps
COPY packages ./packages
RUN npm ci

ARG APP_WORKSPACE
ARG APP_PATH
ARG NEXT_PUBLIC_API_BASE_URL=http://localhost:8080
ENV NEXT_PUBLIC_API_BASE_URL=${NEXT_PUBLIC_API_BASE_URL}
RUN npm run build --workspace="${APP_WORKSPACE}"

FROM nginxinc/nginx-unprivileged:1.28-alpine
COPY infrastructure/docker/nginx-static.conf /etc/nginx/conf.d/default.conf
ARG APP_PATH
COPY --from=build /workspace/${APP_PATH}/out /usr/share/nginx/html
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=3s --start-period=10s --retries=3 \
  CMD wget -q --spider http://127.0.0.1:8080/health || exit 1
