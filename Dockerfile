FROM maven:3.9.11-eclipse-temurin-17 AS build
WORKDIR /build
COPY pom.xml .
COPY src ./src
RUN mvn -B -ntp verify

FROM eclipse-temurin:17-jre-jammy
RUN groupadd --gid 10001 campusflow && useradd --uid 10001 --gid campusflow --create-home campusflow \
    && mkdir -p /app /data && chown campusflow:campusflow /app /data
WORKDIR /app
COPY --from=build --chown=campusflow:campusflow /build/target/campusflow-1.0.0.jar /app/campusflow.jar
ENV H2_DATA_PATH=/data/campusflow
USER campusflow
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/campusflow.jar"]
