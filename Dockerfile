FROM docker.io/library/eclipse-temurin:25-jdk-alpine AS build
WORKDIR /workspace

COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle gradle
COPY src src
RUN --mount=type=cache,target=/root/.gradle ./gradlew --no-daemon jar -x test \
    && cp build/libs/*.jar app.jar


FROM docker.io/library/eclipse-temurin:25-jre-alpine

RUN addgroup -S app && adduser -S -G app -H app
WORKDIR /app

# No runtime dependencies, so the application is this one jar.
COPY --from=build /workspace/app.jar ./

USER app
EXPOSE 8080

HEALTHCHECK --interval=10s --timeout=3s --start-period=300s --retries=3 \
    CMD wget -q -O /dev/null http://127.0.0.1:8080/health || exit 1

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
