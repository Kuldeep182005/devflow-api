# =============================================================================
# DevFlow API — Production Dockerfile
# Multi-stage build: Maven build → minimal JRE runtime
#
# SECURITY: This image has NO Docker daemon access. It communicates with the
# sandbox executor only through an authenticated HTTP protocol.
# =============================================================================

# --------------- Stage 1: Build ---------------
FROM eclipse-temurin:21-jdk-alpine AS build

WORKDIR /build

# Copy Maven wrapper and reactor POMs first for layer caching
COPY .mvn/ .mvn/
COPY mvnw mvnw
COPY pom.xml pom.xml
COPY devflow-api/pom.xml devflow-api/pom.xml
COPY devflow-sandbox-executor/pom.xml devflow-sandbox-executor/pom.xml

RUN chmod +x mvnw

# Copy source code for the API module
COPY devflow-api/src devflow-api/src

# Build the API JAR (skip tests — tests run in CI)
RUN ./mvnw clean package -pl devflow-api -am -DskipTests -B

# --------------- Stage 2: Runtime ---------------
FROM eclipse-temurin:21-jre-alpine AS runtime

# OCI labels for supply-chain provenance (overridden at build time via --build-arg)
ARG BUILD_DATE="unset"
ARG VCS_REF="unset"
ARG VERSION="0.0.1-SNAPSHOT"

LABEL org.opencontainers.image.title="devflow-api" \
      org.opencontainers.image.description="DevFlow Secure AI Developer Platform API" \
      org.opencontainers.image.version="${VERSION}" \
      org.opencontainers.image.revision="${VCS_REF}" \
      org.opencontainers.image.created="${BUILD_DATE}" \
      org.opencontainers.image.source="https://github.com/devflow/devflow-api" \
      org.opencontainers.image.vendor="DevFlow"

WORKDIR /app

# Create non-root user
RUN addgroup -S devflow && adduser -S -G devflow -u 10001 devflow

# Copy only the built fat JAR from the build stage — no source, no Maven cache
COPY --from=build /build/devflow-api/target/devflow-api-*.jar /app/app.jar

# Own application directory
RUN chown -R devflow:devflow /app

# Run as non-root
USER 10001

EXPOSE 8080

# Actuator liveness health check
HEALTHCHECK --interval=30s --timeout=5s --start-period=40s --retries=3 \
    CMD wget --no-verbose --tries=1 --spider http://localhost:8080/actuator/health/liveness || exit 1

ENTRYPOINT ["java", \
    "-XX:+UseContainerSupport", \
    "-XX:MaxRAMPercentage=75.0", \
    "-XX:+ExitOnOutOfMemoryError", \
    "-Djava.security.egd=file:/dev/./urandom", \
    "-jar", "/app/app.jar"]

