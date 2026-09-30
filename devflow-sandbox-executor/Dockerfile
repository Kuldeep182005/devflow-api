# =============================================================================
# DevFlow Sandbox Executor — Production Dockerfile
# Multi-stage build: Maven build → minimal JRE runtime
#
# SECURITY: This is the ONLY DevFlow component with Docker daemon access.
# It runs as a separate process from devflow-api. The API communicates with
# this executor exclusively through an authenticated HTTP protocol (X-Sandbox-Token).
#
# The executor requires Docker socket access to create and manage isolated
# containers for sandbox code execution. This is the intended security boundary.
# =============================================================================

# --------------- Stage 1: Build ---------------
FROM eclipse-temurin:21-jdk-alpine AS build

WORKDIR /build

# Copy Maven wrapper and reactor POMs for layer caching
COPY .mvn/ .mvn/
COPY mvnw mvnw
COPY pom.xml pom.xml
COPY devflow-api/pom.xml devflow-api/pom.xml
COPY devflow-sandbox-executor/pom.xml devflow-sandbox-executor/pom.xml

RUN chmod +x mvnw

# Copy source code for the executor module
COPY devflow-sandbox-executor/src devflow-sandbox-executor/src

# Build the executor JAR (skip tests — tests run in CI)
RUN ./mvnw clean package -pl devflow-sandbox-executor -am -DskipTests -B

# --------------- Stage 2: Runtime ---------------
FROM eclipse-temurin:21-jre-alpine AS runtime

# OCI labels for supply-chain provenance
ARG BUILD_DATE="unset"
ARG VCS_REF="unset"
ARG VERSION="0.0.1-SNAPSHOT"

LABEL org.opencontainers.image.title="devflow-sandbox-executor" \
      org.opencontainers.image.description="DevFlow Isolated Sandbox Execution Daemon" \
      org.opencontainers.image.version="${VERSION}" \
      org.opencontainers.image.revision="${VCS_REF}" \
      org.opencontainers.image.created="${BUILD_DATE}" \
      org.opencontainers.image.source="https://github.com/devflow/devflow-api" \
      org.opencontainers.image.vendor="DevFlow"

WORKDIR /app

# Create non-root user
# NOTE: The executor runs as non-root but requires Docker socket access.
# The Docker socket is mounted at runtime (not baked into the image) and
# the executor user must be in the docker group or the socket must have
# appropriate permissions. This is configured at deployment time.
RUN addgroup -S devflow && adduser -S -G devflow -u 10002 devflow

# Create workspace root directory for sandbox execution
RUN mkdir -p /app/sandboxes && chown -R devflow:devflow /app

# Copy only the built fat JAR
COPY --from=build /build/devflow-sandbox-executor/target/devflow-sandbox-executor-*.jar /app/app.jar
RUN chown devflow:devflow /app/app.jar

# Run as non-root
USER 10002

EXPOSE 8089

# Health check (requires authenticated X-Sandbox-Token header)
HEALTHCHECK --interval=30s --timeout=5s --start-period=30s --retries=3 \
    CMD wget --no-verbose --tries=1 --spider --header="X-Sandbox-Token: ${SANDBOX_EXECUTOR_TOKEN:-devflow-sandbox-secret-token}" http://localhost:8089/api/v1/sandbox/health || exit 1

ENTRYPOINT ["java", \
    "-XX:+UseContainerSupport", \
    "-XX:MaxRAMPercentage=75.0", \
    "-XX:+ExitOnOutOfMemoryError", \
    "-Djava.security.egd=file:/dev/./urandom", \
    "-jar", "/app/app.jar"]
