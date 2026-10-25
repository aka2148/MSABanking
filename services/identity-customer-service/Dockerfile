# syntax=docker/dockerfile:1.7
FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /workspace/services/identity-customer-service
COPY services/identity-customer-service/pom.xml ./pom.xml
COPY services/identity-customer-service/src ./src
COPY BACKEND/src/main/resources/db/service-migration /workspace/BACKEND/src/main/resources/db/service-migration
RUN --mount=type=secret,id=norton-root-ca,target=/run/secrets/norton-root.cer,required=false \
    if [ -s /run/secrets/norton-root.cer ]; then \
      keytool -importcert -noprompt -trustcacerts -alias norton-webmail-shield-root \
        -file /run/secrets/norton-root.cer -cacerts -storepass changeit; \
    fi && \
    mvn -B -DskipTests package

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /workspace/services/identity-customer-service/target/identity-customer-service-0.1.0-SNAPSHOT.jar /app/app.jar
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
