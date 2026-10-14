#!/bin/sh
# Arranque de WildFly. Si estan EJB_USER / EJB_PASSWORD, crea (o actualiza) el usuario de aplicacion
# con el que los clientes de EJB remoto (Fiscalizacionautomatica) se autentican. Se hace al arrancar y
# no al construir la imagen, para que la contrasenia no quede guardada en la imagen.
set -e

if [ -n "$EJB_USER" ] && [ -n "$EJB_PASSWORD" ]; then
  /opt/jboss/wildfly/bin/add-user.sh -a -u "$EJB_USER" -p "$EJB_PASSWORD" --silent
  echo "Usuario de aplicacion '$EJB_USER' listo para EJB remoto"
else
  echo "EJB_USER / EJB_PASSWORD sin definir: no se crea el usuario de EJB remoto"
fi

# -b 0.0.0.0: escuchar en todas las interfaces, no solo dentro del contenedor.
exec /opt/jboss/wildfly/bin/standalone.sh -b 0.0.0.0
