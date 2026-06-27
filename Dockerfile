# -------- Build stage --------
FROM eclipse-temurin:17-jdk AS build
WORKDIR /app/server

COPY server/gradlew ./gradlew
COPY server/gradle ./gradle
COPY server/build.gradle.kts server/settings.gradle.kts server/gradle.properties ./
RUN chmod +x ./gradlew

RUN ./gradlew dependencies --no-daemon || true

COPY server/src ./src

RUN ./gradlew build -x test --no-daemon

# -------- Runtime stage --------
FROM eclipse-temurin:17-jre
WORKDIR /app

COPY --from=build /app/server/build/libs/*all*.jar app.jar

COPY server/uploads ./uploads

EXPOSE 8080
CMD ["java", "-jar", "app.jar"]