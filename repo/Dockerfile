FROM eclipse-temurin:17-jdk-jammy AS build
WORKDIR /build
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw dependency:go-offline -B
COPY src src
RUN ./mvnw package -DskipTests -B

FROM eclipse-temurin:17-jre-jammy
WORKDIR /app
RUN apt-get update && apt-get install -y --no-install-recommends \
    default-mysql-client openssl && rm -rf /var/lib/apt/lists/*
COPY --from=build /build/target/*.jar app.jar
COPY infra/app/entrypoint.sh infra/app/bootstrap-secrets.sh ./
RUN chmod +x entrypoint.sh bootstrap-secrets.sh
EXPOSE 8080
ENTRYPOINT ["./entrypoint.sh"]
