FROM maven:3.9-eclipse-temurin-25-alpine AS builder
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -B
COPY src/ ./src/
RUN mvn package -DskipTests -B

# AOT training run — record method profiles for faster startup
RUN java -XX:AOTMode=record -XX:AOTConfiguration=app.aotconf \
    -Dspring.context.exit=onRefresh \
    -jar target/flash-card-io-1.0.0.jar 2>/dev/null || true
# Create AOT cache from the profile
RUN java -XX:AOTMode=create -XX:AOTConfiguration=app.aotconf \
    -XX:AOTCache=app.aot \
    -jar target/flash-card-io-1.0.0.jar 2>/dev/null || true

FROM eclipse-temurin:25-jre-alpine
WORKDIR /app

RUN apk add --no-cache ffmpeg tini \
    && addgroup -S appuser && adduser -S appuser -G appuser -u 1000

COPY --from=builder /app/target/flash-card-io-1.0.0.jar app.jar
COPY --from=builder /app/app.aot app.aot

# Cache directory for TTS/Unsplash/Ekilex disk cache + prebuild queue
RUN mkdir -p /app/cache && chown 1000:1000 /app/cache
ENV CACHE_DIR=/app/cache

USER appuser

EXPOSE 8080

ENTRYPOINT ["tini", "--"]
CMD ["java", \
  "-XX:+UseZGC", \
  "-XX:+UseCompactObjectHeaders", \
  "-XX:AOTCache=app.aot", \
  "-XX:MaxRAMPercentage=75.0", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-jar", "app.jar"]
