FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /workspace/services/fd-account-service
COPY services/fd-account-service/pom.xml ./pom.xml
COPY services/fd-account-service/src ./src
COPY BACKEND/src/main/resources/db/service-migration /workspace/BACKEND/src/main/resources/db/service-migration
RUN mvn -B -DskipTests package

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /workspace/services/fd-account-service/target/*.jar /app/app.jar
EXPOSE 8083
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
