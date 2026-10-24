FROM maven:3-eclipse-temurin-25 AS builder

WORKDIR /build

# Copy reactor POMs first (needed to resolve the multi-module reactor + leverage layer caching)
COPY pom.xml .
COPY buildingblocks/pom.xml buildingblocks/pom.xml
COPY api-gateway/pom.xml api-gateway/pom.xml
COPY services/product-service/pom.xml services/product-service/pom.xml
COPY services/cart-service/pom.xml services/cart-service/pom.xml
COPY services/user-service/pom.xml services/user-service/pom.xml
RUN mvn -q -pl api-gateway -am dependency:go-offline

# api-gateway has no dependency on buildingblocks (deliberate, see WU6 apply notes) — only its own sources are needed
COPY api-gateway/src api-gateway/src
RUN mvn -q -pl api-gateway -am clean package -DskipTests

FROM eclipse-temurin:25-jre-alpine

WORKDIR /app
COPY --from=builder /build/api-gateway/target/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
