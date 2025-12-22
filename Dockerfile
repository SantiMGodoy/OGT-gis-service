# Multi-stage build para microservicio Spring Boot
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# Copiar dependencias primero (mejor cache)
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copiar código fuente
COPY src ./src

# Compilar
RUN mvn clean package -DskipTests -B

# Stage 2: Runtime
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Herramientas para health checks
RUN apk add --no-cache wget curl

# Usuario no-root
RUN addgroup -S spring && adduser -S spring -G spring

# Copiar JAR
COPY --from=build /build/target/*.jar app.jar

USER spring:spring

# Puerto del servicio
EXPOSE 8082

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8082/actuator/health || exit 1

ENV JAVA_OPTS="-Xms256m -Xmx512m -XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
