FROM maven:3.9.16-eclipse-temurin-25-alpine AS build
WORKDIR /workspace
COPY . .
RUN mvn -q -pl services/inventory-service -am -DskipTests package
FROM eclipse-temurin:25-jre-alpine
RUN apk upgrade --no-cache
RUN addgroup -S -g 10001 marketflow && adduser -S -D -u 10001 -G marketflow marketflow \
    && mkdir -p /app \
    && chown -R 10001:10001 /app
WORKDIR /app
COPY --from=build /workspace/services/inventory-service/target/inventory-service-*.jar app.jar
USER 10001:10001
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0 -Djava.security.egd=file:/dev/./urandom"
EXPOSE 8084
ENTRYPOINT ["java","-jar","/app/app.jar"]
