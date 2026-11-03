# ---------- Build ----------
FROM maven:3.9-eclipse-temurin-25 AS build

WORKDIR /build

COPY pom.xml .
RUN mvn -B -q quarkus:go-offline

COPY src ./src

RUN mvn -B -q package -DskipTests


# ---------- Runtime ----------
FROM eclipse-temurin:25-jre

RUN useradd --system --uid 10001 --no-create-home jobrail

WORKDIR /app

COPY --from=build --chown=jobrail:jobrail /build/target/quarkus-app/lib/ ./lib/
COPY --from=build --chown=jobrail:jobrail /build/target/quarkus-app/*.jar ./
COPY --from=build --chown=jobrail:jobrail /build/target/quarkus-app/app/ ./app/
COPY --from=build --chown=jobrail:jobrail /build/target/quarkus-app/quarkus/ ./quarkus/

USER jobrail

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "quarkus-run.jar"]
