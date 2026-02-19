# -------- Build stage --------
FROM eclipse-temurin:17-jdk AS build
WORKDIR /app/server

# Gradle wrapper + config (cache-friendly)
COPY server/gradlew ./gradlew
COPY server/gradle ./gradle
COPY server/build.gradle.kts server/settings.gradle.kts server/gradle.properties ./
RUN chmod +x ./gradlew

# Dependencies (optional cache layer)
RUN ./gradlew dependencies --no-daemon || true

# Source
COPY server/src ./src

# Build
RUN ./gradlew build -x test --no-daemon

# -------- Runtime stage --------
FROM eclipse-temurin:17-jre
WORKDIR /app

# Copy fat jar (robust)
COPY --from=build /app/server/build/libs/*all*.jar app.jar

EXPOSE 8080
CMD ["java", "-jar", "app.jar"]
