FROM nginx:1.27-alpine

COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 8080

HEALTHCHECK --interval=5s --timeout=3s --retries=12 --start-period=5s \
  CMD wget -q -O - http://127.0.0.1:8080/gateway/health | grep -q UP || exit 1

CMD ["nginx", "-g", "daemon off;"]
