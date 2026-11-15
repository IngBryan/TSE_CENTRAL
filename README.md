# carga.uy — Componente central

Incluye los frontoffices de ciudadanos y funcionarios, el backoffice,
los backends Jakarta EE en WildFly, la fiscalización automática,
PostgreSQL, MongoDB y RabbitMQ.

## Requisitos y configuración

- Docker con Docker Compose 2.30 o superior.
- En Windows: Docker Desktop iniciado con contenedores Linux y
  Git for Windows instalado.
- En Linux/macOS: Bash y Docker iniciado.

Antes del primer arranque, copiar `.env.example` a `.env` y completar
los valores vacíos siguiendo los comentarios de la plantilla.

Configurar las credenciales de las bases de datos, RabbitMQ, ID GUB UY,
las claves de las API, los secretos de sesión y la autenticación EJB.

Los siguientes valores deben coincidir entre Central y Nodos:

- `RABBITMQ_USER` y `RABBITMQ_PASSWORD`.
- `PDI_ORGANISMO_CODIGO` y `PDI_ORGANISMO_CLAVE`.
- `BALANZAS_CLIENT_ID` y `BALANZAS_CLIENT_SECRET` de Central con
  `BALANZAS_OAUTH_CLIENT_ID` y `BALANZAS_OAUTH_CLIENT_SECRET` de Nodos.

Las direcciones de PDI y Balanzas están configuradas para ejecución local.

## Arranque local

Desde la raíz de este repositorio:

**Linux/macOS o Git Bash en Windows:**

```bash
bash iniciar.sh
```

**PowerShell en Windows**, con Git instalado en su ubicación habitual:

```powershell
& "C:\Program Files\Git\bin\bash.exe" ./iniciar.sh
```

El script compila las imágenes y levanta únicamente Central.
Los nodos periféricos se inician desde su propio repositorio,
en la misma PC.

| Aplicación | Dirección local |
|---|---|
| Ciudadanos | http://localhost |
| Funcionarios | http://localhost:8080/funcionarios |
| Backoffice | http://localhost:3004/backoffice |

## Opciones y operación

`CIUDADANOS_LOGIN_TESTING=true` habilita el ingreso por cédula sin
ID GUB UY. Para funcionarios, `FUNCIONARIOS_LOGIN_TESTING` está
habilitado por defecto en local. Ambos modos son para pruebas.

El consumidor de tracking permanece activo. En Kubernetes, el poller
de Balanzas se ejecuta mediante un CronJob cada 30 minutos. Desde el
panel de Nodos se solicitan ejecuciones manuales para las pruebas.

Estado: `docker compose ps`. Logs: `docker compose logs -f`.
Detener Central: `docker compose down`.

**Datos de prueba:** el despliegue de Frontofficeciudadanos recrea el
esquema SQL y carga sus datos iniciales.

Despliegue en ANTEL Elastic Cloud:
[guía de Kubernetes y Argo CD](k8s/README.md).

Pruebas de performance: [generador de carga de Tracking](load-tests/tracking-generator/README.md)
y [consultas del frontoffice con k6](load-tests/frontoffice-k6/README.md).
