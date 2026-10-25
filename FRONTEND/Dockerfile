# Stage 1: Build Angular application
FROM node:20-alpine AS build
WORKDIR /app

# Copy dependency specifications and install cleanly
COPY package*.json ./
RUN npm ci

# Copy source code and build production bundle
COPY . .
RUN npm run build -- --configuration production

# Stage 2: Serve application via Nginx
FROM nginx:1.27-alpine
COPY --from=build /app/dist/frontend/browser /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80

HEALTHCHECK --interval=10s --timeout=3s --retries=5 --start-period=5s \
  CMD wget -q -O - http://127.0.0.1:80/index.html > /dev/null || exit 1

CMD ["nginx", "-g", "daemon off;"]
