# syntax=docker/dockerfile:1
#
# Un unico WildFly con las 3 aplicaciones Jakarta EE del componente central desplegadas
# (Frontofficeciudadanos, Frontofficefuncionarios, Backofficeautoridadesadministradores).
#
# El contexto de build es esta carpeta (wildfly/); el codigo viene del contexto adicional
# "backend" (componente_central/backend), definido en docker-compose.yml.

ARG WILDFLY_IMAGE=quay.io/wildfly/wildfly:41.0.0.Final-jdk25
ARG PG_JDBC_VERSION=42.7.13

# ---- build: compila los proyectos Maven (el codigo usa release 24, se compila con JDK 25) ----
FROM maven:3.9-eclipse-temurin-25 AS builder
ARG PG_JDBC_VERSION
WORKDIR /build
COPY --from=backend . .

# Cada proyecto es un build Maven independiente. El cache de ~/.m2 evita bajar todo de nuevo.
RUN --mount=type=cache,target=/root/.m2 \
    set -e; \
    for project in Frontofficeciudadanos Frontofficefuncionarios Backofficeautoridadesadministradores; do \
      mvn -B -ntp -q -f "$project/pom.xml" clean package -Dmaven.test.skip=true; \
    done; \
    mkdir /out; \
    cp Frontofficeciudadanos/ear/target/*.ear Frontofficefuncionarios/ear/target/*.ear \
       Backofficeautoridadesadministradores/ear/target/*.ear /out/; \
    curl -fsSL -o /out/postgresql.jar \
      "https://repo.maven.apache.org/maven2/org/postgresql/postgresql/${PG_JDBC_VERSION}/postgresql-${PG_JDBC_VERSION}.jar"

# ---- runtime ----
FROM ${WILDFLY_IMAGE}
USER jboss
COPY --from=builder --chown=jboss:jboss /out/postgresql.jar /tmp/postgresql.jar
COPY --chown=jboss:jboss datasources.cli /tmp/cli/

# Configura WildFly una vez, al construir la imagen. Host, usuario y password de la base quedan
# como expresiones: se resuelven al arrancar el contenedor con DB_HOST/DB_PORT/DB_USER/DB_PASSWORD,
# sin secretos en la imagen.
RUN /opt/jboss/wildfly/bin/jboss-cli.sh --file=/tmp/cli/datasources.cli \
 && rm -rf /opt/jboss/wildfly/standalone/configuration/standalone_xml_history /tmp/cli /tmp/postgresql.jar

COPY --from=builder --chown=jboss:jboss /out/*.ear /opt/jboss/wildfly/standalone/deployments/

EXPOSE 8080
# Se reemplaza solo el CMD: el ENTRYPOINT de la imagen base (certificados) se conserva.
# -b 0.0.0.0: escuchar en todas las interfaces, no solo dentro del contenedor.
CMD ["/opt/jboss/wildfly/bin/standalone.sh", "-b", "0.0.0.0"]

