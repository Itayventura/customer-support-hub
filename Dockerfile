# syntax=docker/dockerfile:1.7

# --- Build stage ------------------------------------------------------------
FROM eclipse-temurin:21-jdk AS builder
WORKDIR /workspace

COPY gradlew gradlew.bat ./
COPY gradle gradle
COPY build.gradle.kts settings.gradle.kts ./
RUN chmod +x gradlew && ./gradlew --no-daemon --version

COPY src src
RUN ./gradlew --no-daemon bootJar

# --- Runtime stage ----------------------------------------------------------
FROM eclipse-temurin:21-jre
WORKDIR /app

RUN groupadd --system app && useradd --system --gid app --home-dir /app --shell /sbin/nologin app

COPY --from=builder --chown=app:app /workspace/build/libs/*-SNAPSHOT.jar /app/app.jar

USER app
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
