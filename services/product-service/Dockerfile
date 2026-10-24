FROM maven:3-eclipse-temurin-25 AS builder

WORKDIR /build

# Copy reactor POMs first (needed to resolve the multi-module reactor + leverage layer caching)
COPY pom.xml .
COPY buildingblocks/pom.xml buildingblocks/pom.xml
COPY api-gateway/pom.xml api-gateway/pom.xml
COPY services/product-service/pom.xml services/product-service/pom.xml
COPY services/cart-service/pom.xml services/cart-service/pom.xml
COPY services/user-service/pom.xml services/user-service/pom.xml
RUN mvn -q -pl services/product-service -am dependency:go-offline

# Copy sources for this module and its reactor dependency (buildingblocks)
COPY buildingblocks/src buildingblocks/src
COPY services/product-service/src services/product-service/src
RUN mvn -q -pl services/product-service -am clean package -DskipTests

FROM eclipse-temurin:25-jre-alpine

WORKDIR /app
COPY --from=builder /build/services/product-service/target/*.jar app.jar

EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar"]
