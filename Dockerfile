# ─── Stage 1: Build ──────────────────────────────────
FROM maven:3.9-eclipse-temurin-17 AS builder

WORKDIR /app

# Download dependencies first (cached layer)
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

# Build the application
COPY src src
RUN mvn -B -q package -DskipTests

# ─── Stage 2: Runtime ────────────────────────────────
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

# Install Python + Semgrep
# (optional: comment out if you do not need it)
RUN apk add --no-cache python3 py3-pip && \
    pip3 install semgrep --break-system-packages --quiet

# Copy the JAR from the build stage
COPY --from=builder /app/target/*.jar app.jar

# Non-root user (security)
RUN addgroup -S appgroup && \
    adduser -S appuser -G appgroup
USER appuser

EXPOSE 8080

# JVM tuning for a small VPS (4 GB RAM)
ENTRYPOINT ["java", \
    "-Xmx512m", \
    "-Xms256m", \
    "-XX:+UseG1GC", \
    "-XX:MaxGCPauseMillis=200", \
    "-Djava.security.egd=file:/dev/./urandom", \
    "-jar", "app.jar"]
