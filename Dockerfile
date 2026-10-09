FROM maven:3.9.11-eclipse-temurin-21-alpine AS build
WORKDIR /workspace
ARG SERVICE_MODULE=tracker-service

COPY pom.xml .
COPY tracker-service/pom.xml ./tracker-service/pom.xml
COPY config-server/pom.xml ./config-server/pom.xml
COPY discovery-server/pom.xml ./discovery-server/pom.xml
COPY api-gateway/pom.xml ./api-gateway/pom.xml
RUN mvn -B -pl ${SERVICE_MODULE} -am -DskipTests dependency:go-offline

COPY tracker-service/src ./tracker-service/src
COPY config-server/src ./config-server/src
COPY discovery-server/src ./discovery-server/src
COPY api-gateway/src ./api-gateway/src
COPY config-repository ./config-repository
RUN mvn -B -pl ${SERVICE_MODULE} -am -DskipTests package

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
ARG SERVICE_MODULE=tracker-service

RUN addgroup -S app && adduser -S app -G app
COPY --from=build --chown=app:app /workspace/${SERVICE_MODULE}/target/${SERVICE_MODULE}-*.jar application.jar

EXPOSE 8080
USER app
ENTRYPOINT ["java", "-jar", "/app/application.jar"]
