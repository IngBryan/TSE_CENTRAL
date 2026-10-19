# syntax=docker/dockerfile:1

FROM maven:3.9-eclipse-temurin-25 AS builder
WORKDIR /build
# The remote EJB contracts (ejb-client of Frontofficeciudadanos, Frontofficefuncionarios and
# Backofficeautoridadesadministradores) come from the "backend" context (componente_central/backend,
# defined in docker-compose.yml) and are installed first, with Modelo.
COPY --from=backend Modelo Modelo
COPY --from=backend Frontofficeciudadanos Frontofficeciudadanos
COPY --from=backend Frontofficefuncionarios Frontofficefuncionarios
COPY --from=backend Backofficeautoridadesadministradores Backofficeautoridadesadministradores
COPY . Fiscalizacionautomatica
RUN --mount=type=cache,target=/root/.m2 \
    set -e; \
    mvn -B -ntp -q -f Modelo/pom.xml clean install -Dmaven.test.skip=true; \
    mvn -B -ntp -q -f Frontofficeciudadanos/pom.xml -pl ejb -am clean install -Dmaven.test.skip=true; \
    mvn -B -ntp -q -f Frontofficefuncionarios/pom.xml -pl ejb -am clean install -Dmaven.test.skip=true; \
    mvn -B -ntp -q -f Backofficeautoridadesadministradores/pom.xml -pl ejb -am clean install -Dmaven.test.skip=true; \
    mvn -B -ntp -q -f Fiscalizacionautomatica/pom.xml package -Dmaven.test.skip=true

# ---- runtime ----
FROM eclipse-temurin:25-jre-alpine
# Unprivileged user with a fixed uid, so Kubernetes can verify it is not root
RUN addgroup -S -g 1001 app && adduser -S -u 1001 -G app app
COPY --from=builder /build/Fiscalizacionautomatica/target/fiscalizacionautomatica.jar /app/fiscalizacionautomatica.jar
USER app
# The mode goes as argument: tracking-consumer (default), weighings-poller or detect-situations
ENTRYPOINT ["java", "-jar", "/app/fiscalizacionautomatica.jar"]
