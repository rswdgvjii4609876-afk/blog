# Build and verify the application first: bash mvnw -B -ntp verify
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -g 10001 blog && adduser -D -H -u 10001 -G blog blog \
    && mkdir -p /app/data && chown 10001:10001 /app/data
COPY --chown=10001:10001 target/blog-0.0.1-SNAPSHOT.jar /app/app.jar
ENV SPRING_PROFILES_ACTIVE=public \
    BLOG_DATA_DIR=/app/data \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=70.0 -Djava.net.preferIPv4Stack=true"
USER 10001:10001
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD http_proxy= https_proxy= HTTP_PROXY= HTTPS_PROXY= wget -q -O /dev/null "http://127.0.0.1:${PORT:-8080}/healthz" || exit 1
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
