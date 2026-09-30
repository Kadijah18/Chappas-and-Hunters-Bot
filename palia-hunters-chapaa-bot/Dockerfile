# Build the Java application with Gradle.
FROM gradle:8-jdk21 AS build
WORKDIR /home/gradle/src
COPY --chown=gradle:gradle . .
RUN gradle clean installDist --no-daemon

# Small Java runtime image for Railway/production.
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /home/gradle/src/build/install/palia-hunters-chapaa-bot/ /app/

# Railway persistent volume should be mounted at /data.
ENV PALIA_BOT_DB=/data/palia-hunters-chapaa.db

CMD ["/app/bin/palia-hunters-chapaa-bot"]
