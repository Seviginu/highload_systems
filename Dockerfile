FROM maven:3.9.11-eclipse-temurin-21-alpine AS build
WORKDIR /workspace
ARG SERVICE_MODULE=tracker-service

COPY pom.xml .
COPY ${SERVICE_MODULE}/pom.xml ./${SERVICE_MODULE}/pom.xml

COPY ${SERVICE_MODULE}/src ./${SERVICE_MODULE}/src
COPY config-repository ./config-repository
RUN --mount=type=cache,target=/root/.m2,sharing=locked \
    mvn -B -f ${SERVICE_MODULE}/pom.xml -DskipTests package

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
ARG SERVICE_MODULE=tracker-service

RUN addgroup -S app && adduser -S app -G app
COPY --from=build --chown=app:app /workspace/${SERVICE_MODULE}/target/${SERVICE_MODULE}-*.jar application.jar

EXPOSE 8080
USER app
ENTRYPOINT ["java", "-jar", "/app/application.jar"]
