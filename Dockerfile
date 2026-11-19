# The server and the web app it serves. Gradle runs on the host, not in here, because configuring the
# project needs the Android SDK. Build first:
#   ./gradlew :server:installDist :composeApp:wasmJsBrowserDistribution
FROM eclipse-temurin:21-jre

WORKDIR /app
COPY server/build/install/server/ /app/
COPY composeApp/build/dist/wasmJs/productionExecutable/ /app/web/

# The start script defaults to Ktor's development mode, which would accept the built-in JWT secret; a later -D wins.
ENV JAVA_OPTS="-Dio.ktor.development=false" \
    DATABASE_URL="jdbc:sqlite:/data/drinkwater.db" \
    WEB_APP_DIR="/app/web"

VOLUME /data
EXPOSE 8080
# Through sh, because files copied from Windows can lose their executable bit.
CMD ["sh", "/app/bin/server"]
