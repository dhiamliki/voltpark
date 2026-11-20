FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build
COPY pom.xml .
COPY src ./src
RUN mvn -q -DskipTests package

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
RUN addgroup -S voltpark && adduser -S voltpark -G voltpark
COPY --from=build /build/target/*.jar app.jar
USER voltpark
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
