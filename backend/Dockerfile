# Сборка образа backend. Тесты здесь не запускаются — их гоняет CI (./mvnw verify с Testcontainers).
FROM eclipse-temurin:21-jdk AS build
WORKDIR /src
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN ./mvnw -q -B dependency:go-offline
COPY src src
RUN ./mvnw -q -B -DskipTests package && cp target/oko-backend-*.jar /src/app.jar

FROM eclipse-temurin:21-jre
RUN useradd --system --uid 10001 --no-create-home oko
WORKDIR /app
COPY --from=build /src/app.jar app.jar
USER oko
EXPOSE 8080
# Ориентир из architecture.md: heap 1–2 ГБ на сервере с 4 ГБ RAM. Переопределяется через JAVA_OPTS.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=50 -XX:+ExitOnOutOfMemoryError"
HEALTHCHECK --interval=15s --timeout=3s --start-period=60s --retries=3 \
    CMD curl -fsS http://localhost:8080/actuator/health || exit 1
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
