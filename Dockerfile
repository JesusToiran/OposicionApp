# syntax=docker/dockerfile:1
FROM eclipse-temurin:25.0.5_8-jdk-noble@sha256:95e350ad1eb738f943e141640f7e3d797e483359a3b33c3864255bfd1ad71f8a AS build

WORKDIR /workspace
COPY .mvn/wrapper/maven-wrapper.properties .mvn/wrapper/maven-wrapper.properties
COPY mvnw pom.xml ./
RUN sed -i 's/\r$//' mvnw && chmod +x mvnw
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -ntp dependency:go-offline

COPY src/main/ src/main/
# Las pruebas con Testcontainers se ejecutan fuera de la construcción de imagen.
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -ntp -Dmaven.test.skip=true package \
    && cp target/oposicionapp-*.jar /workspace/application.jar

FROM eclipse-temurin:25.0.5_8-jre-noble@sha256:a98128a57de944a72bd31fbc79b898edd5a5864d3652071d5bbd2160ab7d7dce AS runtime

RUN groupadd --gid 10001 app && useradd --uid 10001 --gid app --no-create-home --shell /usr/sbin/nologin app
WORKDIR /app
COPY --from=build --chown=root:root /workspace/application.jar ./application.jar
USER 10001:10001
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/application.jar"]
