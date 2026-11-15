# carga.uy — Frontoffice de funcionarios

Aplicación Next.js para gestionar empresas y consultar vehículos, viajes,
seguimiento GPS y casos de fiscalización, con sus evidencias y actuaciones.
Consume la API de Frontofficefuncionarios desplegada en WildFly.

## Ejecución con Docker

La preparación del entorno se documenta en el [README principal](../../../README.md).
Desde la raíz de TSE_CENTRAL, para construir y levantar este frontend:

```bash
docker compose up -d --build frontofficefuncionarios-front
```

Acceso local: http://localhost:8080/funcionarios.
Estado: http://localhost:8080/funcionarios/health.
Compose monta `config/docker.json` y utiliza las credenciales del `.env` de la raíz.

## Desarrollo sin Docker

Requiere Node.js 24 y npm, con WildFly disponible en el puerto 8081.
Desde esta carpeta, copiar `.env.example` a `.env.local` y completar:

| Variable | Uso |
|---|---|
| `SESSION_SECRET` | Clave de sesión, distinta de la utilizada por ciudadanos |
| `BACKEND_API_KEY` | Mismo valor que `FUNCIONARIOS_API_KEY` en WildFly |
| `OIDC_CLIENT_SECRET` | Secreto del cliente ID Uruguay; requerido para ese ingreso |
| `LOGIN_TESTING` | `true` para habilitar el ingreso por cédula |
| `CONFIG_FILE` | Archivo JSON de configuración; usar `config/local.json` en desarrollo |

`config/local.json` define la URL pública `http://localhost:3003/funcionarios`,
la API `http://localhost:8081/frontofficefuncionarios-web/api`, el timeout
y el identificador del cliente OIDC. Las rutas relativas de `CONFIG_FILE`
se resuelven desde el directorio en que se ejecuta Next.js.

```bash
npm ci
npm run dev
```

Abrir http://localhost:3003/funcionarios.

## Autenticación y configuración

El ingreso de testing requiere `LOGIN_TESTING=true` en el frontend y
`FUNCIONARIOS_INGRESO_TESTING=true` en WildFly. En Compose, ambos se controlan
con `FUNCIONARIOS_LOGIN_TESTING` en el `.env` de la raíz.
El backend comprueba que la cédula corresponda a un funcionario y la valida
contra la PDI.

Para ID Uruguay, configurar `oidc.clientId` en el JSON y `OIDC_CLIENT_SECRET`
en el entorno. Las URL de retorno y logout deben estar registradas.
`config/docker.json` utiliza `http://localhost:8080` y
`http://localhost:8080/logout` como retornos para el despliegue local con Compose.
Las opciones `oidc.redirectUri` y `oidc.postLogoutRedirectUri` permiten indicarlos.

En Compose, `SESSION_SECRET` toma el valor de `FUNCIONARIOS_SESSION_SECRET`
y `BACKEND_API_KEY` toma el de `FUNCIONARIOS_API_KEY` del `.env` de la raíz.
En Kubernetes, el JSON y los secretos se proporcionan mediante ConfigMap y Secrets.

La aplicación utiliza el prefijo `/funcionarios`. Las principales secciones
son `/empresas`, `/viajes` y `/casos`, precedidas por ese prefijo.
Las llamadas a WildFly se realizan desde el servidor Next.js y la sesión
se conserva en una cookie cifrada.

## Pruebas y compilación

Desde esta carpeta:

```bash
npm run test:polling-gps
npm run test:evidencia-desvio
npm run lint
npm run build
```

Para ejecutar la compilación con `CONFIG_FILE=config/local.json`:

```bash
npm start -- -p 3003
```

Despliegue remoto: [Kubernetes y Argo CD](../../../k8s/README.md).
