# syntax=docker/dockerfile:1.6
# ---- Build stage: compile the fat JAR inside Docker so `docker compose up`
#      works from a fresh clone with no host-side prerequisites. ----
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build

# Prime the dependency cache first so source changes don't invalidate it.
COPY pom.xml ./
RUN mvn -B -e -DskipTests dependency:go-offline || true

# Build the application JAR (tests are skipped here; run_tests.sh owns testing).
COPY src ./src
RUN mvn -B -e -DskipTests package

# ---- Runtime stage: slim JRE image with only the artifacts the app needs. ----
FROM eclipse-temurin:17-jre-jammy
WORKDIR /app

RUN apt-get update && apt-get install -y --no-install-recommends \
        default-mysql-client openssl \
    && rm -rf /var/lib/apt/lists/*

COPY --from=build /build/target/reclaim-portal-*.jar app.jar
COPY infra/app/entrypoint.sh infra/app/bootstrap-secrets.sh ./
RUN chmod +x entrypoint.sh bootstrap-secrets.sh

EXPOSE 8080
ENTRYPOINT ["./entrypoint.sh"]
