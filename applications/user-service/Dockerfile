FROM maven:3.9.9-eclipse-temurin-21 AS builder
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline
COPY src ./src
RUN mvn package -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=builder /app/target/user-service-0.0.1-SNAPSHOT.jar ./user-service.jar
EXPOSE 8080 
ENTRYPOINT ["java", "-jar", "user-service.jar"]

