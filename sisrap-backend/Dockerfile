# build
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -B
COPY src ./src
RUN mvn clean package -DskipTests

# ejecución
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/target/sisrap-backend-*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-Xmx700m", "-jar", "app.jar"]