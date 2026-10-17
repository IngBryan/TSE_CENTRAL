# syntax=docker/dockerfile:1

FROM maven:3.9-eclipse-temurin-25 AS builder
WORKDIR /build
COPY . .
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -ntp -q package -Dmaven.test.skip=true

# ---- runtime ----
FROM eclipse-temurin:25-jre-alpine
# Unprivileged user with a fixed uid, so Kubernetes can verify it is not root
RUN addgroup -S -g 1001 app && adduser -S -u 1001 -G app app
COPY --from=builder /build/target/fiscalizacionautomatica.jar /app/fiscalizacionautomatica.jar
USER app
CMD ["java", "-jar", "/app/fiscalizacionautomatica.jar"]
