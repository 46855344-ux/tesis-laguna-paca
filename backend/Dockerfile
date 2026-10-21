# syntax=docker/dockerfile:1

# --- Etapa 1: compilar con el JDK 21 y Maven -------------------------------
FROM maven:3.9.9-eclipse-temurin-21 AS build

WORKDIR /build

# Primero solo el POM: si no cambian las dependencias, esta capa se reutiliza.
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src ./src
RUN mvn -B -DskipTests package

# --- Etapa 2: ejecutar con un JRE 21 mínimo --------------------------------
FROM eclipse-temurin:21-jre-jammy

# curl solo para el healthcheck de Docker Compose.
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && useradd --system --uid 1001 laguna

WORKDIR /app
COPY --from=build /build/target/laguna-paca-backend-0.1.0-SNAPSHOT.jar app.jar
RUN chown -R laguna:laguna /app

USER laguna
EXPOSE 8080

# MaxRAMPercentage en lugar de -Xmx fijo: el contenedor puede limitarse sin
# recompilar la imagen.
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
