FROM eclipse-temurin:21-jdk AS build

WORKDIR /workspace
COPY . .
RUN ./gradlew --no-daemon bootJar

FROM eclipse-temurin:21-jre@sha256:0b99b3376fbb50dbf9c0af8535fdd5b8eb0d3fdfc8b1e4967f383b6919f6901a

WORKDIR /app
COPY --from=build --chown=65534:65534 /workspace/build/libs/apisysanimais-0.1.0.jar /app/app.jar
USER 65534:65534
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
