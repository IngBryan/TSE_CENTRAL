#!/usr/bin/env bash
set -euo pipefail

# Resolver la carpeta del script permite invocarlo desde cualquier directorio.
project_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
cd -- "$project_dir"

fail() {
    printf 'Error: %s\n' "$1" >&2
    exit 1
}

command -v docker >/dev/null 2>&1 ||
    fail 'Docker no está disponible. Instalá Docker y verificá que esté en PATH.'

if ! compose_version="$(docker compose version --short 2>/dev/null)"; then
    fail 'Docker Compose no está disponible. Se requiere docker compose 2.30 o superior.'
fi

if [[ "$compose_version" =~ ^v?([0-9]+)\.([0-9]+) ]]; then
    major=$((10#${BASH_REMATCH[1]}))
    minor=$((10#${BASH_REMATCH[2]}))
    if (( major < 2 || (major == 2 && minor < 30) )); then
        fail 'Se requiere Docker Compose 2.30 o superior.'
    fi
else
    fail 'No se pudo determinar la versión de Docker Compose.'
fi

[[ -f .env && -r .env ]] ||
    fail 'Falta un .env legible. Copiá .env.example a .env y completá los valores vacíos.'

docker info >/dev/null 2>&1 ||
    fail 'Docker no está accesible. Iniciá Docker; en Windows, abrí Docker Desktop con contenedores Linux.'

# Validar sin imprimir la configuración ni sus credenciales.
docker compose --env-file .env -f docker-compose.yml config --quiet

printf 'Compilando y levantando los servicios de Central...\n'
docker compose --env-file .env -f docker-compose.yml up -d --build

printf '\nArranque solicitado. Consultá el estado con: docker compose ps\n'
printf 'Ciudadanos: http://localhost\n'
printf 'Funcionarios: http://localhost:8080/funcionarios\n'
printf 'Backoffice: http://localhost:3004/backoffice\n'
