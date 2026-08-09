# syntax=docker/dockerfile:1.7
FROM eclipse-temurin:25-jdk-alpine AS build
WORKDIR /workspace
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
COPY config/ config/
COPY services/sample-service/pom.xml services/sample-service/pom.xml
COPY services/identity-service/pom.xml services/identity-service/pom.xml
COPY services/seller-service/pom.xml services/seller-service/pom.xml
COPY services/catalog-service/pom.xml services/catalog-service/pom.xml
COPY services/inventory-service/pom.xml services/inventory-service/pom.xml
COPY services/search-service/pom.xml services/search-service/pom.xml
COPY services/cart-service/pom.xml services/cart-service/pom.xml
COPY services/order-service/pom.xml services/order-service/pom.xml
COPY services/payment-service/pom.xml services/payment-service/pom.xml
COPY services/notification-service/pom.xml services/notification-service/pom.xml
RUN chmod +x mvnw && ./mvnw -B -pl services/seller-service -am dependency:go-offline
COPY services/seller-service/src services/seller-service/src
RUN ./mvnw -B -pl services/seller-service -am package -DskipTests

FROM eclipse-temurin:25-jre-alpine
RUN addgroup -S marketflow && adduser -S -G marketflow -u 10002 marketflow
WORKDIR /app
COPY --from=build /workspace/services/seller-service/target/seller-service-*.jar app.jar
USER 10002:10002
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0 -Djava.security.egd=file:/dev/./urandom"
EXPOSE 8082
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/app.jar"]
