# Imagem do familhas-email para o compose local (ADR 0005).
# Build em dois estágios: Maven compila o jar; a imagem final só tem o JRE e o jar.

FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml .
RUN mvn -B -ntp -q dependency:go-offline
COPY src src
RUN mvn -B -ntp -q package -DskipTests && cp target/familhas-email-*.jar app.jar

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S familhas && adduser -S familhas -G familhas
WORKDIR /app
COPY --from=build /build/app.jar app.jar
USER familhas
EXPOSE 8081
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
