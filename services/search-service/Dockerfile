FROM maven:3.9.14-eclipse-temurin-25-alpine AS build
WORKDIR /workspace
COPY . .
RUN mvn -q -pl services/search-service -am -DskipTests package
FROM eclipse-temurin:25-jre-alpine
RUN addgroup -S marketflow && adduser -S -G marketflow marketflow
WORKDIR /app
COPY --from=build /workspace/services/search-service/target/search-service-*.jar app.jar
USER marketflow
EXPOSE 8085
ENTRYPOINT ["java","-jar","/app/app.jar"]
