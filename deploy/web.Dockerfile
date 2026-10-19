# Contexto de build: raiz do repositorio (site/ e frontend/ juntos).
FROM node:22-alpine AS build
WORKDIR /src
COPY frontend/package.json frontend/package-lock.json frontend/.npmrc ./
RUN npm ci --no-audit --no-fund
COPY frontend/ .
RUN npm run build

FROM nginx:1.27-alpine
COPY deploy/nginx.conf /etc/nginx/conf.d/default.conf
# Site institucional em / (somente o publico; testeLogin e RAG ficam fora da imagem).
COPY site/index.html site/styles.css /usr/share/nginx/html/
COPY site/assets /usr/share/nginx/html/assets
COPY site/fotos /usr/share/nginx/html/fotos
# Sistema de gestao em /app.
COPY --from=build /src/dist /usr/share/nginx/html/app
EXPOSE 80
