# Build сборка
FROM gradle:8-jdk21-alpine AS build
WORKDIR /app
# Только файлы сборки
COPY build.gradle.kts settings.gradle.kts ./
COPY gradle ./gradle

# Зависимости
RUN gradle dependencies --no-daemon

# Исходники
COPY src ./src

# Компиляция
RUN gradle bootJar --no-daemon

# Легковесная сборка
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java","-jar", "app.jar"]
