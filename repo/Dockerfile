FROM eclipse-temurin:17-jre-jammy
WORKDIR /app
RUN apt-get update && apt-get install -y --no-install-recommends \
    default-mysql-client openssl && rm -rf /var/lib/apt/lists/*
# Copy the pre-built fat JAR (built locally via ./mvnw clean package -DskipTests)
COPY target/reclaim-portal-*.jar app.jar
COPY infra/app/entrypoint.sh infra/app/bootstrap-secrets.sh ./
RUN chmod +x entrypoint.sh bootstrap-secrets.sh
EXPOSE 8080
ENTRYPOINT ["./entrypoint.sh"]
