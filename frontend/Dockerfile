FROM node:22-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
# installation reproductible, exactement les versions de package-lock.json
RUN npm ci --no-audit --no-fund
COPY . .
# URL de l'API (gateway) vue par le navigateur, figee au moment du build
ARG VITE_API_BASE_URL=http://localhost:8080/api/v1
ENV VITE_API_BASE_URL=$VITE_API_BASE_URL
RUN npm run build

FROM nginx:alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 3000
