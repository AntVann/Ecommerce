# syntax=docker/dockerfile:1.7
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /workspace
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
COPY config/ config/
COPY services/sample-service/pom.xml services/sample-service/pom.xml
COPY services/identity-service/pom.xml services/identity-service/pom.xml
COPY services/seller-service/pom.xml services/seller-service/pom.xml
RUN chmod +x mvnw && ./mvnw -B -pl services/identity-service -am dependency:go-offline
COPY services/identity-service/src services/identity-service/src
RUN ./mvnw -B -pl services/identity-service -am package -DskipTests

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S marketflow && adduser -S -G marketflow -u 10001 marketflow
WORKDIR /app
COPY --from=build /workspace/services/identity-service/target/identity-service-*.jar app.jar
USER 10001:10001
EXPOSE 8081
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/app.jar"]
