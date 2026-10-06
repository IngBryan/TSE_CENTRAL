#!/bin/sh
# Crea una base por proyecto del componente central. Lo ejecuta la imagen de postgres solo la
# primera vez (volumen vacio). POSTGRES_USER (el que crea la imagen) queda como duenio de todas.
set -e
for db in frontofficeciudadanos frontofficefuncionarios backofficeautoridadesadministradores; do
  psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname postgres -c "CREATE DATABASE $db"
done
