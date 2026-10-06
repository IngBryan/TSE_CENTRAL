#!/bin/bash
# Arranque de WildFly. El handler de logs (socket-handler hacia Logstash) resuelve el host al
# iniciar: si el nombre todavia no existe (por ejemplo Logstash recien se esta levantando tras
# reiniciar el equipo, donde Docker no respeta depends_on) falla y WildFly queda "started with
# errors". Por eso se espera a que el nombre resuelva. Si no resuelve a tiempo se arranca
# igual: el servidor no debe depender de la observabilidad (solo se pierden esos logs).
set -u

host="${LOGSTASH_HOST:-localhost}"
for _ in $(seq 1 30); do
  getent hosts "$host" >/dev/null 2>&1 && break
  echo "Esperando a que '$host' resuelva (Logstash)..."
  sleep 2
done

exec /opt/jboss/wildfly/bin/standalone.sh -b 0.0.0.0
