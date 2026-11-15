# carga.uy — Frontoffice de ciudadanos

Aplicación Next.js para consultar información pública y gestionar la empresa,
vehículos, choferes, guías de viaje, infracciones y descargos.
Consume la API de Frontofficeciudadanos desplegada en WildFly.

## Ejecución con Docker

La preparación del entorno se documenta en el [README principal](../../../README.md).
Desde la raíz de TSE_CENTRAL, para construir y levantar este frontend:

```bash
docker compose up -d --build frontofficeciudadanos-front
```

Acceso local: http://localhost. Estado: http://localhost/health.
Compose configura el backend como `http://wildfly:8080/frontofficeciudadanos-web/api`
y utiliza las credenciales del `.env` de la raíz.

## Desarrollo sin Docker

Requiere Node.js 24 y npm, con WildFly disponible en el puerto 8081.
Desde esta carpeta, copiar `.env.example` a `.env.local` y completar:

| Variable | Uso |
|---|---|
| `SESSION_SECRET` | Clave larga y aleatoria para cifrar la cookie de sesión |
| `BACKEND_API_KEY` | Mismo valor que `CIUDADANOS_API_KEY` en WildFly |
| `BACKEND_URL` | API de WildFly; por defecto `http://localhost:8081/frontofficeciudadanos-web/api` |
| `APP_BASE_URL` | Dirección pública del frontend y retorno predeterminado de ID Uruguay |
| `OIDC_CLIENT_ID`, `OIDC_CLIENT_SECRET` | Credenciales del cliente ID Uruguay |

Agregar `NEXT_BASE_PATH=` a `.env.local` para usar las rutas locales sin prefijo.
Para el ingreso mediante ID Uruguay, conservar `APP_BASE_URL=http://localhost`
y utilizar el puerto 80:

```bash
npm ci
npm run dev:gubuy
```

Abrir http://localhost. La URL de retorno y la de logout deben estar registradas
en ID Uruguay. Las opciones adicionales de OIDC y timeout están en `.env.example`.

Para desarrollar en el puerto 3000, usar `npm run dev` y configurar
`APP_BASE_URL=http://localhost:3000`. El ingreso de testing por cédula requiere
`LOGIN_TESTING=true` en este frontend e `INGRESO_TESTING=true` en WildFly.
En el Compose, este último se controla con `CIUDADANOS_LOGIN_TESTING=true`
en el `.env` de la raíz. El ingreso por ID Uruguay requiere una URL registrada.

## Rutas y autenticación

El portal público ofrece validación de permisos y estadísticas.
Tras el ingreso, `/mi-cuenta` y `/empresa` presentan las operaciones disponibles
para el ciudadano. Las llamadas a WildFly se realizan desde el servidor Next.js
con la clave `BACKEND_API_KEY`; la sesión se conserva en una cookie cifrada.
La validación de identidad contra PDI se realiza en el backend.

`NEXT_BASE_PATH` se fija al compilar. En Docker local queda vacío; en Kubernetes
se utiliza `/ciudadanos`. El endpoint de estado es `/health`, precedido por
ese prefijo cuando corresponda.

## Pruebas y compilación

Desde esta carpeta:

```bash
npm run test:mensajes-rutas
npm run test:localidades
npm run lint
npm run build
```

Para ejecutar la compilación local con la configuración del puerto 80:

```bash
npm start -- -p 80
```

Despliegue remoto: [Kubernetes y Argo CD](../../../k8s/README.md).
