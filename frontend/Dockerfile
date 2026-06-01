FROM node:22-alpine AS build
WORKDIR /app
ARG GIT_COMMIT=dev
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
RUN echo "{\"commit\":\"${GIT_COMMIT}\"}" > public/version.json && npm run build

FROM nginx:alpine
COPY --from=build /app/dist/rto-frontend/browser /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
