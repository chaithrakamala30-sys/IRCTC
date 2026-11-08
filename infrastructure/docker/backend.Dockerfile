FROM maven:3.9.15-eclipse-temurin-25 AS build
WORKDIR /workspace
COPY backend/railconnect-core/pom.xml ./pom.xml
COPY backend/railconnect-core/src ./src
RUN mvn --batch-mode --no-transfer-progress -DskipTests package

FROM eclipse-temurin:25-jre-alpine
RUN addgroup -S railconnect && adduser -S railconnect -G railconnect
WORKDIR /app
COPY --from=build --chown=railconnect:railconnect \
  /workspace/target/railconnect-core-0.1.0-SNAPSHOT.jar /app/railconnect.jar
USER railconnect
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=3s --start-period=45s --retries=3 \
  CMD wget -q --spider http://127.0.0.1:8080/actuator/health/liveness || exit 1
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=70.0", "-jar", "/app/railconnect.jar"]
