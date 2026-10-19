
FROM maven:3.9.9-eclipse-temurin-21 AS build

WORKDIR /opt

COPY pom.xml pom.xml
RUN mvn -q dependency:go-offline

COPY src src
RUN mvn -q -DskipTests package

FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

COPY --from=build /opt/target/formatiomdevops-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 8081

ENTRYPOINT ["java", "-jar", "app.jar"]


