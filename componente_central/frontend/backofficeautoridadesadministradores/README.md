# carga.uy — Backoffice de autoridades y administradores

Aplicación Next.js para consultar reportes y administrar usuarios, nodos
periféricos y reglas de fiscalización según el rol del usuario.
Consume la API del backoffice desplegada en WildFly.

## Ejecución con Docker

La preparación del entorno se documenta en el [README principal](../../../README.md).
Desde la raíz de TSE_CENTRAL, para construir y levantar este frontend:

```bash
docker compose up -d --build backofficeautoridadesadministradores-front
```

Acceso local: http://localhost:3004/backoffice.
Estado: http://localhost:3004/backoffice/health.
Compose proporciona `BACKOFFICE_API_URL` con la dirección interna de WildFly.

## Desarrollo sin Docker

Requiere Node.js 24 y npm, con WildFly disponible en el puerto 8081.
Crear `.env.local` en esta carpeta con:

```dotenv
BACKOFFICE_API_URL=http://localhost:8081/backofficeautoridadesadministradores-web/api
```

Desde esta carpeta:

```bash
npm ci
npm run dev -- --port 3004
```

Abrir http://localhost:3004/backoffice. El prefijo `/backoffice` está definido
en `next.config.ts`.

## Autenticación y roles

El ingreso utiliza cédula y contraseña de un usuario registrado en el backoffice.
WildFly valida las credenciales, emite un JWT y comprueba el rol en cada operación.
El frontend guarda el token en una cookie HTTP-only y lo envía como Bearer
en las llamadas al backend.

`BACKOFFICE_JWT_SECRET` se configura en WildFly mediante el `.env` de la raíz
o el Secret de Kubernetes. Es necesario para emitir los tokens de acceso.
La configuración propia de este frontend requiere `BACKOFFICE_API_URL`.

Las secciones incluyen dashboard, reportes, usuarios, nodos periféricos,
reglas de fiscalización y cuenta. El acceso depende del rol.
Cuando el backend exige cambiar la contraseña inicial, el frontend dirige
al usuario al formulario correspondiente antes de continuar.

## Verificación y compilación

Desde esta carpeta:

```bash
npm run lint
npm run build
```

Para ejecutar la compilación en el puerto local documentado:

```bash
npm start -- -p 3004
```

Despliegue remoto: [Kubernetes y Argo CD](../../../k8s/README.md).
