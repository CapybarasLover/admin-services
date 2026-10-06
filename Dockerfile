# Фронт собирается здесь же: src/main/resources/static в git не хранится.
# BASE_PATH — секретный префикс пути (например /k3j9x2abc), пусто — корень.
FROM node:22-alpine AS frontend
ARG BASE_PATH=""
WORKDIR /app/frontend
COPY frontend/package.json frontend/package-lock.json ./
RUN ["npm", "ci"]
COPY frontend ./
RUN VITE_BASE_PATH="$BASE_PATH" npm run build

FROM maven:3.9.12-eclipse-temurin-17-alpine AS build

WORKDIR /app
COPY pom.xml .

RUN ["mvn", "dependency:go-offline"]

COPY src ./src
COPY --from=frontend /app/src/main/resources/static ./src/main/resources/static
RUN ["mvn", "clean", "package", "-DskipTests"]

FROM eclipse-temurin:17-jre-alpine AS run

WORKDIR /app

RUN addgroup -g 1001 -S appuser && \
adduser -u 1001 -S appuser -G appuser
USER appuser

COPY --from=build /app/target/*.jar ./app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-Dspring.profiles.active=prod", "-jar", "app.jar"]
