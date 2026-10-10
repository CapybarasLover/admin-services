FROM maven:3.9.12-eclipse-temurin-17-alpine AS build

WORKDIR /app
COPY pom.xml .

RUN ["mvn", "dependency:go-offline"]

COPY src ./src
RUN ["mvn", "clean", "package", "-DskipTests"]

FROM eclipse-temurin:17-jre-alpine AS run

WORKDIR /app

RUN addgroup -g 1001 -S appuser && \
adduser -u 1001 -S appuser -G appuser
USER appuser

COPY --from=build /app/target/*.jar ./app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-Dspring.profiles.active=prod", "-jar", "app.jar"]