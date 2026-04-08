# ─────────────────────────────────────────────────────────────
# CASSITRACK — Spring Boot Dockerfile
# Multi-stage build: compile with Maven, run with slim JRE
# ─────────────────────────────────────────────────────────────

# Stage 1: Build the JAR
FROM eclipse-temurin:17-jdk-alpine AS builder
WORKDIR /build

# Install Maven
RUN apk add --no-cache maven

# Copy pom first (for Docker layer caching)
# Dependencies are downloaded only when pom.xml changes
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy source and build
COPY src ./src
RUN mvn package -DskipTests -B

# Stage 2: Run with a minimal JRE
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# Create non-root user (security best practice)
RUN addgroup -S cassitrack && adduser -S cassitrack -G cassitrack
USER cassitrack

# Copy the built JAR from Stage 1
COPY --from=builder /build/target/*.jar app.jar

# Expose the Spring Boot port
EXPOSE 8080

# JVM flags optimised for containers
ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-jar", "app.jar"]
