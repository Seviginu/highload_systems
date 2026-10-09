FROM maven:3.9.11-eclipse-temurin-21-alpine AS build
WORKDIR /workspace

COPY pom.xml .
COPY tracker-service/pom.xml ./tracker-service/pom.xml
RUN mvn -B -pl tracker-service -am -DskipTests dependency:go-offline

COPY tracker-service/src ./tracker-service/src
RUN mvn -B -pl tracker-service -am -DskipTests package

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN addgroup -S app && adduser -S app -G app
COPY --from=build --chown=app:app /workspace/tracker-service/target/tracker-service-*.jar application.jar

EXPOSE 8080
USER app
ENTRYPOINT ["java", "-jar", "/app/application.jar"]
