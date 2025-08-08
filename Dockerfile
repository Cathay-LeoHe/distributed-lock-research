# Multi-stage Dockerfile for Distributed Lock Research Application
# Based on OpenJDK 21 with optimized layer caching and security

# Stage 1: Build Dependencies Cache
FROM openjdk:21-jdk-slim AS deps

# Install Maven
RUN apt-get update && \
    apt-get install -y --no-install-recommends maven && \
    rm -rf /var/lib/apt/lists/* && \
    apt-get clean

WORKDIR /app

# Copy Maven wrapper and configuration files for dependency caching
COPY mvnw mvnw.cmd pom.xml ./
COPY .mvn/ .mvn/

# Make Maven wrapper executable
RUN chmod +x mvnw

# Download dependencies (this layer will be cached if pom.xml doesn't change)
RUN ./mvnw dependency:go-offline -B --no-transfer-progress

# Stage 2: Build Application
FROM deps AS builder

# Copy source code
COPY src/ ./src/

# Build the application with optimizations
RUN ./mvnw clean package -DskipTests -B --no-transfer-progress && \
    mkdir -p target/extracted && \
    java -Djarmode=layertools -jar target/*.jar extract --destination target/extracted

# Stage 3: Runtime Image
FROM eclipse-temurin:21-jre AS runtime

# Install runtime dependencies
RUN apt-get update && \
    apt-get install -y --no-install-recommends \
        curl \
        netcat-openbsd \
        dumb-init \
        && \
    rm -rf /var/lib/apt/lists/* && \
    apt-get clean

# Create application user for security
RUN groupadd -r --gid 1001 appuser && \
    useradd -r --uid 1001 --gid appuser --home /app --shell /sbin/nologin appuser

# Set working directory
WORKDIR /app

# Copy Spring Boot layers for optimal caching
COPY --from=builder --chown=appuser:appuser /app/target/extracted/dependencies/ ./
COPY --from=builder --chown=appuser:appuser /app/target/extracted/spring-boot-loader/ ./
COPY --from=builder --chown=appuser:appuser /app/target/extracted/snapshot-dependencies/ ./
COPY --from=builder --chown=appuser:appuser /app/target/extracted/application/ ./

# Create startup script with dependency waiting and graceful shutdown
RUN cat > /app/startup.sh << 'EOF'
#!/bin/bash
set -e

# Trap SIGTERM for graceful shutdown
trap 'echo "Received SIGTERM, shutting down gracefully..."; kill -TERM $PID; wait $PID' TERM

echo "========================================="
echo "Distributed Lock Research Application"
echo "========================================="
echo "Environment: ${SPRING_PROFILES_ACTIVE:-docker}"
echo "Lock Provider: ${LOCK_PROVIDER:-redis}"
echo "Server Port: ${SERVER_PORT:-8080}"
echo "Java Version: $(java -version 2>&1 | head -n 1)"
echo "Memory: $(free -h | grep Mem)"
echo "========================================="

# Function to wait for service
wait_for_service() {
    local host=$1
    local port=$2
    local service_name=$3
    local timeout=${4:-60}
    
    echo "Waiting for $service_name at $host:$port (timeout: ${timeout}s)..."
    
    local count=0
    while ! nc -z "$host" "$port" 2>/dev/null; do
        if [ $count -ge $timeout ]; then
            echo "ERROR: Timeout waiting for $service_name at $host:$port"
            exit 1
        fi
        sleep 1
        count=$((count + 1))
        if [ $((count % 10)) -eq 0 ]; then
            echo "Still waiting for $service_name... (${count}s elapsed)"
        fi
    done
    echo "$service_name is ready!"
}

# Wait for dependencies based on lock provider
case "${LOCK_PROVIDER:-redis}" in
    "redis")
        if [ "${WAIT_FOR_REDIS:-true}" = "true" ]; then
            wait_for_service "${REDIS_HOST:-redis}" "${REDIS_PORT:-6379}" "Redis"
        fi
        ;;
    "zookeeper")
        if [ "${WAIT_FOR_ZOOKEEPER:-true}" = "true" ]; then
            wait_for_service "${ZK_HOST:-zookeeper}" "${ZK_PORT:-2181}" "ZooKeeper"
        fi
        ;;
    *)
        echo "Unknown lock provider: ${LOCK_PROVIDER}. Waiting for both Redis and ZooKeeper..."
        if [ "${WAIT_FOR_REDIS:-true}" = "true" ]; then
            wait_for_service "${REDIS_HOST:-redis}" "${REDIS_PORT:-6379}" "Redis"
        fi
        if [ "${WAIT_FOR_ZOOKEEPER:-true}" = "true" ]; then
            wait_for_service "${ZK_HOST:-zookeeper}" "${ZK_PORT:-2181}" "ZooKeeper"
        fi
        ;;
esac

echo "All dependencies are ready. Starting application..."

# Start the application in background
java ${JAVA_OPTS} org.springframework.boot.loader.launch.JarLauncher "$@" &
PID=$!

# Wait for the application process
wait $PID
EOF

# Create comprehensive health check script
RUN cat > /app/healthcheck.sh << 'EOF'
#!/bin/bash
set -e

# Configuration
HEALTH_URL="http://localhost:${SERVER_PORT:-8080}/actuator/health"
TIMEOUT=10
MAX_RETRIES=3

# Function to check health endpoint
check_health() {
    local retry=0
    while [ $retry -lt $MAX_RETRIES ]; do
        if curl -f -s --max-time $TIMEOUT "$HEALTH_URL" > /dev/null 2>&1; then
            echo "Health check passed (attempt $((retry + 1)))"
            return 0
        fi
        retry=$((retry + 1))
        if [ $retry -lt $MAX_RETRIES ]; then
            echo "Health check failed, retrying... (attempt $retry/$MAX_RETRIES)"
            sleep 2
        fi
    done
    return 1
}

# Perform health check
if check_health; then
    exit 0
else
    echo "Health check failed after $MAX_RETRIES attempts"
    exit 1
fi
EOF

# Make scripts executable
RUN chmod +x /app/startup.sh /app/healthcheck.sh

# Switch to application user
USER appuser

# Expose application port
EXPOSE 8080

# Configure JVM options optimized for containers
ENV JAVA_OPTS="-XX:+UseContainerSupport \
               -XX:MaxRAMPercentage=75.0 \
               -XX:+UseG1GC \
               -XX:+UseStringDeduplication \
               -XX:+OptimizeStringConcat \
               -XX:+UnlockExperimentalVMOptions \
               -XX:+UseCGroupMemoryLimitForHeap \
               -Djava.security.egd=file:/dev/./urandom \
               -Dspring.profiles.active=docker \
               -Dfile.encoding=UTF-8 \
               -Duser.timezone=UTC"

# Health check configuration with proper timing
HEALTHCHECK --interval=30s --timeout=15s --start-period=90s --retries=3 \
    CMD /app/healthcheck.sh

# Use dumb-init for proper signal handling
ENTRYPOINT ["dumb-init", "--"]
CMD ["/app/startup.sh"]