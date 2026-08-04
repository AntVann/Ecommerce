FROM maven:3.9.14-eclipse-temurin-25-alpine AS build
WORKDIR /workspace
COPY . .
RUN mvn -q -pl services/inventory-service -am -DskipTests package
FROM eclipse-temurin:25-jre-alpine
RUN addgroup -S marketflow && adduser -S -G marketflow marketflow
WORKDIR /app
COPY --from=build /workspace/services/inventory-service/target/inventory-service-*.jar app.jar
USER marketflow
EXPOSE 8084
ENTRYPOINT ["java","-jar","/app/app.jar"]
