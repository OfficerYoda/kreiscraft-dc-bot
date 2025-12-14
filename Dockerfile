# Stage 1: Build with Gradle
FROM gradle:8.7-jdk21-alpine AS build
WORKDIR /home/gradle/project
COPY . .
RUN gradle shadowJar

# Stage 2: Create the final image
FROM eclipse-temurin:21
WORKDIR /app
COPY --from=build /home/gradle/project/build/libs/kreiscraft-dc-bot.jar .
RUN mkdir data
CMD ["java", "-jar", "kreiscraft-dc-bot.jar"]
