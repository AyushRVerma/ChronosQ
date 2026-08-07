# ---------- Stage 1: Build the application ----------
FROM eclipse-temurin:25-jdk-alpine AS builder

WORKDIR /workspace

# Copy Maven Wrapper and pom.xml first.
# This allows Docker to cache downloaded dependencies.
COPY .mvn .mvn
COPY mvnw pom.xml ./

RUN chmod +x mvnw \
    && ./mvnw dependency:go-offline -B

# Copy the application source code.
COPY src src

# Build the executable Spring Boot JAR.
# Tests will run separately in our CI pipeline.
RUN ./mvnw clean package -DskipTests -B


# ---------- Stage 2: Run the application ----------
FROM eclipse-temurin:25-jre-alpine

WORKDIR /app

# Create a non-root user for better container security.
RUN addgroup --system chronosq \
    && adduser --system --ingroup chronosq chronosq

# Copy only the final JAR—not Maven or source code.
COPY --from=builder \
     --chown=chronosq:chronosq \
     /workspace/target/*.jar \
     /app/chronosq.jar

USER chronosq

EXPOSE 8080

ENTRYPOINT [
    "java",
    "-XX:MaxRAMPercentage=75.0",
    "-jar",
    "/app/chronosq.jar"
]