# ==========================================
# DOCKERFILE OPTIMIZADO PARA PRODUCCIÓN
# Multi-stage build con mejores prácticas
# ==========================================

# ==========================================
# STAGE 1: BUILD
# ==========================================
FROM maven:3.9-eclipse-temurin-21-alpine AS build
WORKDIR /build

# Argumentos de build
ARG GITHUB_USERNAME
ARG GITHUB_TOKEN

# Configurar Maven para GitHub Packages
RUN mkdir -p /root/.m2 && \
    echo "<settings>\
      <servers>\
        <server>\
          <id>github</id>\
          <username>${GITHUB_USERNAME}</username>\
          <password>${GITHUB_TOKEN}</password>\
        </server>\
      </servers>\
    </settings>" > /root/.m2/settings.xml

# Copiar solo pom.xml primero (mejor cache de dependencias)
COPY pom.xml .

# Descargar dependencias (se cachea si pom.xml no cambia)
RUN mvn dependency:go-offline -B

# Copiar código fuente
COPY src ./src

# Compilar sin tests (los tests van en CI/CD separado)
RUN mvn package -DskipTests -B && \
    rm -rf /root/.m2/settings.xml

# ==========================================
# STAGE 2: RUNTIME
# ==========================================
FROM eclipse-temurin:21-jre-alpine AS runtime

# Argumentos de runtime
ARG APP_NAME=app
ARG APP_PORT=8080

# Labels para metadata
LABEL maintainer="OGT Team"
LABEL version="1.0"
LABEL description="OGT Microservice"

# Crear usuario no-root para seguridad
RUN addgroup -g 1001 -S appgroup && \
    adduser -u 1001 -S appuser -G appgroup

WORKDIR /app

# Copiar JAR desde stage de build
COPY --from=build /build/target/*.jar app.jar

# Cambiar ownership
RUN chown -R appuser:appgroup /app

# Usar usuario no-root
USER appuser

# Exponer puerto
EXPOSE ${APP_PORT}

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD wget --no-verbose --tries=1 --spider http://localhost:${APP_PORT}/actuator/health || exit 1

# Configuración JVM optimizada para contenedores
ENV JAVA_OPTS="-XX:+UseContainerSupport \
    -XX:MaxRAMPercentage=75.0 \
    -XX:+UseG1GC \
    -XX:+UseStringDeduplication \
    -Djava.security.egd=file:/dev/./urandom"

# Entrypoint
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]