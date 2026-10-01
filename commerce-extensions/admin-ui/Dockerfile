# Build stage: install exact dependencies, run tests, build the Angular app.
FROM node:24-alpine AS build
WORKDIR /src
COPY package.json package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY . .
RUN npx ng build

# Run stage: unprivileged nginx serving the static files and proxying /api to the core.
FROM nginxinc/nginx-unprivileged:1.27-alpine
ENV CORE_URL=http://core:8080
COPY nginx/default.conf.template /etc/nginx/templates/default.conf.template
COPY --from=build /src/dist/admin-ui/browser /usr/share/nginx/html
EXPOSE 8080
