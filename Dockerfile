# HarmonySafeAgent Docker Image
# Multi-stage build for optimized image size

# Stage 1: Build stage
FROM maven:3.9.6-eclipse-temurin-17 AS builder

# Set working directory
WORKDIR /app

# Copy Maven configuration files
COPY pom.xml .
COPY src ./src

# Build the application
RUN mvn clean package -DskipTests

# Stage 2: Runtime stage
FROM eclipse-temurin:17-jre-alpine

# Install required tools for analysis
RUN apk add --no-cache \
    # Basic utilities
    bash \
    curl \
    git \
    # C/C++ analysis tools
    clang \
    clang-extra-tools \
    # Python for Semgrep (optional)
    python3 \
    py3-pip \
    # Build tools for compile_commands.json generation
    make \
    cmake \
    ninja \
    # Text processing tools
    grep \
    sed \
    awk

# Install Semgrep (optional but recommended)
RUN pip3 install --no-cache-dir semgrep

# Create app user for security
RUN addgroup -g 1000 harmony && \
    adduser -D -s /bin/bash -u 1000 -G harmony harmony

# Set working directory
WORKDIR /app

# Copy the built JAR from builder stage
COPY --from=builder /app/target/harmony-safe-agent-*.jar harmony-agent.jar

# Copy configuration files
COPY --from=builder /app/src/main/resources/application.yml ./config/
COPY --from=builder /app/src/main/resources/templates ./templates/
COPY --from=builder /app/src/main/resources/rules ./rules/

# Create directories for user data
RUN mkdir -p /app/workspace /app/reports /app/cache /home/harmony/.harmony-agent && \
    chown -R harmony:harmony /app /home/harmony

# Switch to non-root user
USER harmony

# Set environment variables
ENV JAVA_OPTS="-Xmx2g -Xms512m" \
    HARMONY_CONFIG_DIR="/app/config" \
    HARMONY_WORKSPACE_DIR="/app/workspace" \
    HARMONY_REPORTS_DIR="/app/reports" \
    HARMONY_CACHE_DIR="/app/cache"

# Expose port for potential web interface (future feature)
EXPOSE 8080

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=5s --retries=3 \
    CMD java -jar harmony-agent.jar config get ai.provider || exit 1

# Default command - start interactive mode
ENTRYPOINT ["java", "-jar", "harmony-agent.jar"]
CMD ["interactive"]