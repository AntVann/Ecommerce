FROM maven:3.9.14-eclipse-temurin-25-alpine AS build
WORKDIR /workspace
COPY . .
RUN mvn -q -pl services/payment-service -am -DskipTests package

FROM eclipse-temurin:25-jre-alpine
RUN addgroup -S marketflow && adduser -S -G marketflow -u 10003 marketflow
WORKDIR /app
COPY --from=build /workspace/services/payment-service/target/payment-service-*.jar app.jar
USER 10003:10003
EXPOSE 8088
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/app.jar"]
