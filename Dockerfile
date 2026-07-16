# Build сборка
FROM gradle:8-jdk21-alpine AS build
WORKDIR /app
COPY . .
RUN gradle bootJar --no-daemon

# Легковесная сборка (то что остаётся)
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java","-jar", "app.jar"]
