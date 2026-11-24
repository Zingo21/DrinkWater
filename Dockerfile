# The server and the web app it serves. CI builds it and publishes it from main (.github/workflows/build.yml).
# Gradle runs before this, not in here, because configuring the project needs the Android SDK. To build it yourself:
#   ./gradlew :server:installDist :composeApp:wasmJsBrowserDistribution && docker build .
FROM eclipse-temurin:21-jre

WORKDIR /app
COPY server/build/install/server/ /app/
COPY composeApp/build/dist/wasmJs/productionExecutable/ /app/web/

# The start script defaults to Ktor's development mode, which would accept the built-in JWT secret; a later -D wins.
# Out of memory, the JVM exits instead of limping on, and the restart policy in compose.yaml starts it again.
ENV JAVA_OPTS="-Dio.ktor.development=false -XX:+ExitOnOutOfMemoryError" \
    DATABASE_URL="jdbc:sqlite:/data/drinkwater.db" \
    WEB_APP_DIR="/app/web" \
    WEB_PUSH_KEYS_FILE="/data/vapid.keys"

# The version the server reports at /version and the apps show: 1.2.3 for a release, main (6ce0990) for a build of
# main. CI passes it (build.yml, release.yml); a build without it is "dev".
ARG DRINKWATER_VERSION=dev
ENV DRINKWATER_VERSION=$DRINKWATER_VERSION

VOLUME /data
EXPOSE 8080
# Shows as healthy or unhealthy in `docker ps`, and `docker compose up --wait` in update.sh waits for it. Docker itself doesn't
# restart an unhealthy container; an uptime monitor on /health is what tells you (README, "Hosting the server").
HEALTHCHECK --interval=10s --timeout=3s --start-period=60s --retries=3 \
    CMD curl -fsS -o /dev/null http://localhost:8080/health || exit 1
# Through sh, because files copied from Windows can lose their executable bit.
CMD ["sh", "/app/bin/server"]
