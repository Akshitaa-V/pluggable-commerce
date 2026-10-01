# Build stage: compile and test with Maven, then package a plain jar.
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src
COPY pom.xml .
RUN mvn -q -B dependency:go-offline
COPY src ./src
RUN mvn -q -B package

# Run stage: JRE only, numeric non-root user (Kubernetes runAsNonRoot needs a numeric UID).
FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S -g 10001 app && adduser -S -u 10001 -G app app
USER 10001
WORKDIR /app
COPY --from=build /src/target/commerce-core.jar app.jar
EXPOSE 8080
HEALTHCHECK --interval=10s --timeout=2s --retries=3 \
  CMD wget -qO- http://localhost:8080/health || exit 1
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
