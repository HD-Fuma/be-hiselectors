FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app
COPY . .
RUN ./gradlew bootJar --no-daemon

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S app && adduser -S app -G app
COPY --chown=app:app --from=build /app/build/libs/hiselectors-0.0.1-SNAPSHOT.jar app.jar
USER app
EXPOSE 8080
HEALTHCHECK --interval=10s --timeout=3s --start-period=30s --retries=12 \
  CMD wget -qO- http://127.0.0.1:8080/actuator/health >/dev/null || exit 1
ENTRYPOINT ["java", "-jar", "app.jar"]
