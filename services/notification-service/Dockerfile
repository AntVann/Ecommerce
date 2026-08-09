FROM maven:3.9.16-eclipse-temurin-21 AS build
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
RUN chmod +x mvnw && ./mvnw -B -pl services/notification-service -am dependency:go-offline
COPY services/notification-service/src services/notification-service/src
RUN ./mvnw -B -pl services/notification-service -am -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/services/notification-service/target/notification-service-0.1.0-SNAPSHOT.jar app.jar
EXPOSE 8089
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
