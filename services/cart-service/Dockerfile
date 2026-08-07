FROM maven:3.9.14-eclipse-temurin-25-alpine AS build
WORKDIR /workspace
COPY . .
RUN mvn -q -pl services/cart-service -am -DskipTests package
FROM eclipse-temurin:25-jre-alpine
RUN addgroup -S marketflow && adduser -S -G marketflow marketflow
WORKDIR /app
COPY --from=build /workspace/services/cart-service/target/cart-service-*.jar app.jar
USER marketflow
EXPOSE 8086
ENTRYPOINT ["java","-jar","/app/app.jar"]
