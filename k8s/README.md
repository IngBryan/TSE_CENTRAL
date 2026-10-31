
# Deploy en Kubernetes con Argo CD

El componente central de TSE utiliza Kubernetes para el despliegue de sus servicios y Argo CD para la sincronización automática con los manifiestos del repositorio.

## Componentes

### Frontends

- `frontofficeciudadanos-front`
- `frontofficefuncionarios-front`
- `backofficeautoridadesadministradores-front`

Cada frontend dispone de su propio Deployment, Service e Ingress.

### Backends

**WildFly:** servidor de aplicaciones que contiene los tres EAR Jakarta EE:

- `frontofficeciudadanos.ear`
- `frontofficefuncionarios.ear`
- `backofficeautoridadesadministradores.ear`

Escala horizontalmente con un HPA por CPU (`k8s/apps/wildfly/hpa.yaml`: 2 a 3 réplicas, objetivo 70 % del request de 1 CPU; requiere metrics-server). Puede porque no guarda estado propio: el backoffice se autentica con un token firmado (`BACKOFFICE_JWT_SECRET`) en lugar de sesión, y el timer de revalidación de la PDI toma un lock en PostgreSQL para que lo corra una sola réplica a la vez.

**Fiscalizacionautomatica:** daemon Java independiente que consume eventos de la cola `tracking.events` de RabbitMQ, los persiste en MongoDB y detecta los casos de fiscalización. Utiliza un Deployment sin Service ni Ingress, ya que no expone una interfaz HTTP; KEDA ajusta sus réplicas (1 a 5) según los mensajes pendientes en la cola. La misma imagen corre como CronJob (`fiscalizacionautomatica-poller`) cada 30 minutos para consultar las balanzas.

### Infraestructura externa

PostgreSQL, MongoDB y RabbitMQ se encuentran alojados en ANTEL Elastic Cloud, fuera del cluster Kubernetes.

Para acceder a estos servicios, Kubernetes utiliza Services de tipo `ExternalName`, definidos en `k8s/infrastructure/`.

| Service | Destino | Puerto |
|---|---|---|
| `postgres` | `node6259-tse-postgres.web.elasticloud.uy` | 5432 |
| `mongodb` | `node6260-tse-mongo.web.elasticloud.uy` | 27017 |
| `rabbitmq` | `tse-rabbitmq.web.elasticloud.uy` | 5672 |

Las aplicaciones utilizan los nombres internos `postgres`, `mongodb` y `rabbitmq`, sin depender directamente de los dominios externos.

La configuración inicial de RabbitMQ se encuentra en `rabbitmq/definitions.json`, que define el exchange `tracking`, la cola `tracking.events` y su binding con routing key `tracking.event`.

La inicialización de MongoDB se encuentra en `mongodb/init/01-tracking.js`, que crea la colección `tracking`.

Estas configuraciones deben estar aplicadas en las instancias correspondientes de Elastic Cloud.

Las credenciales de conexión se proporcionan mediante el Secret Kubernetes `tse-runtime-secrets`.

## Organización

- `k8s/apps/`: manifiestos de cada aplicación y sus archivos Kustomize.
- `k8s/infrastructure/`: Services `ExternalName` para conectar con PostgreSQL, MongoDB y RabbitMQ alojados en ANTEL Elastic Cloud.
- `k8s/argocd/`: Applications de Argo CD y configuraciones de Image Updater.

## Publicación y actualización

Los workflows de GitHub Actions construyen y publican las imágenes Docker en GitHub Container Registry (GHCR).

Las imágenes se publican con los tags:

```text
latest
sha-<commit>
```

Argo CD Image Updater detecta las nuevas imágenes y actualiza la aplicación correspondiente. Argo CD sincroniza los cambios con Kubernetes.

PostgreSQL, MongoDB y RabbitMQ se administran externamente en ANTEL Elastic Cloud, por lo que sus imágenes y actualizaciones no son gestionadas mediante los manifiestos Kubernetes de este repositorio ni por Image Updater.

## Configuración inicial

### 1. Crear namespaces

```bash
kubectl create namespace tse
kubectl create namespace argocd
```

### 2. Crear secretos de runtime

Los manifiestos utilizan el Secret `tse-runtime-secrets` en el namespace `tse`, con las siguientes claves:

```text
postgres-user
postgres-password
mongo-user
mongo-password
rabbitmq-user
rabbitmq-password
ciudadanos-api-key
funcionarios-api-key
ejb-user
ejb-password
oidc-client-secret
session-secret
movil-token-secret
backoffice-jwt-secret
pdi-organismo-clave
```

| Clave | Variable (`.env` local) | La usa |
|---|---|---|
| `ciudadanos-api-key` | `CIUDADANOS_API_KEY` | wildfly y frontofficeciudadanos-front (`BACKEND_API_KEY`), el mismo valor |
| `oidc-client-secret` | `OIDC_CLIENT_SECRET` | frontofficeciudadanos-front |
| `session-secret` | `SESSION_SECRET` | frontofficeciudadanos-front |
| `movil-token-secret` | `MOVIL_TOKEN_SECRET` | frontofficeciudadanos-front |
| `backoffice-jwt-secret` | `BACKOFFICE_JWT_SECRET` | wildfly: firma el token del backoffice, que reemplaza a la sesion para poder tener varias replicas (sin la clave, el login del backoffice responde 503) |
| `pdi-organismo-clave` | `PDI_ORGANISMO_CLAVE` | wildfly (igual al de la PDI) |

`ejb-user` / `ejb-password` son el usuario de aplicacion de WildFly con el que Fiscalizacionautomatica
llama por EJB remoto a Frontofficeciudadanos: WildFly lo crea al arrancar (`wildfly/start.sh`) y
fiscalizacion se autentica con el. En WildFly son opcionales (sin ellas arranca, sin el usuario), pero
el consumidor de tracking de fiscalizacion las necesita: al arrancar pide la lista de GPS activos por EJB
remoto y no consume hasta tenerla. Hay que cargarlas en el Secret antes de desplegar.

Crearlo con los valores correspondientes al entorno:

```bash
kubectl -n tse create secret generic tse-runtime-secrets \
  --from-literal=postgres-user='<POSTGRES_USER>' \
  --from-literal=postgres-password='<POSTGRES_PASSWORD>' \
  --from-literal=mongo-user='<MONGO_USER>' \
  --from-literal=mongo-password='<MONGO_PASSWORD>' \
  --from-literal=rabbitmq-user='<RABBITMQ_USER>' \
  --from-literal=rabbitmq-password='<RABBITMQ_PASSWORD>' \
  --from-literal=ciudadanos-api-key='<CIUDADANOS_API_KEY>' \
  --from-literal=funcionarios-api-key='<FUNCIONARIOS_API_KEY>' \
  --from-literal=ejb-user='<EJB_USER>' \
  --from-literal=ejb-password='<EJB_PASSWORD>' \
  --from-literal=oidc-client-secret='<OIDC_CLIENT_SECRET>' \
  --from-literal=session-secret='<SESSION_SECRET>' \
  --from-literal=movil-token-secret='<MOVIL_TOKEN_SECRET>' \
  --from-literal=backoffice-jwt-secret='<BACKOFFICE_JWT_SECRET>' \
  --from-literal=pdi-organismo-clave='<PDI_ORGANISMO_CLAVE>'
```

Si el Secret ya existe, agregar las claves nuevas sin recrearlo (y reiniciar WildFly para que cree el
usuario: `kubectl -n tse rollout restart deployment/wildfly`):

```bash
kubectl -n tse patch secret tse-runtime-secrets --type merge \
  -p '{"stringData":{"ejb-user":"<EJB_USER>","ejb-password":"<EJB_PASSWORD>"}}'
```

Lo mismo para las del front de ciudadanos y la PDI:

```bash
kubectl -n tse patch secret tse-runtime-secrets --type merge \
  -p '{"stringData":{"oidc-client-secret":"<OIDC_CLIENT_SECRET>","session-secret":"<SESSION_SECRET>","movil-token-secret":"<MOVIL_TOKEN_SECRET>","pdi-organismo-clave":"<PDI_ORGANISMO_CLAVE>"}}'
```

Y la del token del backoffice (despues, `kubectl -n tse rollout restart deployment/wildfly`):

```bash
kubectl -n tse patch secret tse-runtime-secrets --type merge \
  -p '{"stringData":{"backoffice-jwt-secret":"<BACKOFFICE_JWT_SECRET>"}}'
```

Los secretos de produccion deben ser distintos a los de desarrollo local. Despues de cambiar el
Secret, reiniciar los pods para que tomen los valores nuevos
(`kubectl -n tse rollout restart deployment/wildfly deployment/frontofficeciudadanos-front`).

La configuracion que no es secreta esta en ConfigMaps junto a cada aplicacion:

- `apps/frontofficeciudadanos-front/configmap.yaml`: `APP_BASE_URL`, `OIDC_CLIENT_ID`, `BACKEND_URL`.
- `apps/wildfly/configmap.yaml`: `PDI_URL`, `PDI_ORGANISMO_CODIGO`.

**Login con ID Uruguay:** la redirect URI es `APP_BASE_URL` y la de logout `APP_BASE_URL/logout`
(`https://env-6537388.web.elasticloud.uy/ciudadanos` y `.../ciudadanos/logout`). Ambas tienen que estar
registradas en ID Uruguay; si no, gub.uy rechaza el login. El ingress no tiene TLS: el HTTPS lo termina
el proxy de Elastic Cloud que esta delante.

El front de funcionarios usa el mismo cliente OIDC (`oidc.clientId` en
`apps/frontofficefuncionarios-front/app.json` y `oidc-client-secret` de `tse-runtime-secrets`). Su redirect
URI es `appBaseUrl` y la de logout `appBaseUrl/logout`: `https://env-6537388.web.elasticloud.uy/funcionarios`
y `.../funcionarios/logout`, también registradas en ID Uruguay. Después del login, WildFly controla que la
cédula esté en la tabla `funcionarios` y la valida contra la PDI (que exista en DNIC y sea mayor de edad).

**PDI:** `PDI_URL` tiene que apuntar a donde este desplegada la PDI (`TSE_NODOS`), y
`PDI_ORGANISMO_CODIGO` / `pdi-organismo-clave` coincidir con los que usa la PDI.

Las credenciales deben coincidir con las configuradas en los servicios de ANTEL Elastic Cloud y no deben almacenarse en el repositorio.

### 3. Instalar Argo CD e Image Updater

```bash
kubectl apply -n argocd --server-side --force-conflicts \
  -f https://raw.githubusercontent.com/argoproj/argo-cd/stable/manifests/install.yaml

kubectl apply -n argocd \
  -f https://raw.githubusercontent.com/argoproj-labs/argocd-image-updater/stable/config/install.yaml
```

### 4. Configurar acceso al repositorio

Para acceder al repositorio privado, Argo CD requiere un token de GitHub con permiso de lectura.

```bash
kubectl -n argocd create secret generic repo-tse-central \
  --from-literal=type=git \
  --from-literal=url=https://github.com/IngBryan/TSE_CENTRAL.git \
  --from-literal=username=IngBryan \
  --from-literal=password='<TOKEN_REPO>'

kubectl -n argocd label secret repo-tse-central \
  argocd.argoproj.io/secret-type=repository
```

### 5. Configurar acceso a GHCR

Se necesita un token de GitHub con permiso `read:packages` para descargar las imágenes privadas.

Crear el Secret en `tse`, utilizado por los pods:

```bash
kubectl -n tse create secret docker-registry ghcr-credentials \
  --docker-server=ghcr.io \
  --docker-username=IngBryan \
  --docker-password='<TOKEN_PACKAGES>'
```

Crear el mismo Secret en `argocd`, utilizado por Image Updater:

```bash
kubectl -n argocd create secret docker-registry ghcr-credentials \
  --docker-server=ghcr.io \
  --docker-username=IngBryan \
  --docker-password='<TOKEN_PACKAGES>'
```

### 6. Secretos del front de funcionarios

Se crean en el namespace `tse` y no deben almacenarse en el repositorio.

`SESSION_SECRET` cifra la cookie de sesión (string largo y aleatorio, distinto del de ciudadanos) y `BACKEND_API_KEY` debe coincidir con `funcionarios-api-key` de `tse-runtime-secrets` (no con la key de ciudadanos).

```bash
kubectl -n tse create secret generic frontofficefuncionarios-front-secrets \
  --from-literal=SESSION_SECRET=$(node -e "console.log(require('crypto').randomBytes(32).toString('hex'))") \
  --from-literal=BACKEND_API_KEY='<FUNCIONARIOS_API_KEY>'
```

El ingreso de testing con cédula (`LOGIN_TESTING=true` en `deployment.yaml` y `FUNCIONARIOS_INGRESO_TESTING=true` en `apps/wildfly/configmap.yaml`) sigue habilitado hasta que ande el login con gub.uy en `/funcionarios`: mientras tanto cualquiera puede ingresar con la cédula de un funcionario registrado (WildFly igual la valida contra la PDI). Apagar los dos cuando gub.uy esté registrado.

Certificado TLS del host del ingress (`env-6537388.web.elasticloud.uy`), a partir del certificado y su clave privada en formato PEM:

```bash
kubectl -n tse create secret tls frontofficefuncionarios-front-tls \
  --cert=<certificado.pem> \
  --key=<clave.pem>
```

Las URLs de la aplicación y del backend no son secretas: se encuentran en `k8s/apps/frontofficefuncionarios-front/app.json`, que se monta como ConfigMap en `/app/config/app.json`.

### 7. Fiscalización automática: KEDA y datos de balanzas

**KEDA** escala el consumidor de tracking (`k8s/apps/fiscalizacionautomatica/keda.yaml`: una réplica cada 100 mensajes pendientes en `tracking.events`, entre 1 y 5). Se instala una vez en el cluster:

```bash
kubectl apply --server-side -f https://github.com/kedacore/keda/releases/download/v2.16.1/keda-2.16.1.yaml
```

El `ScaledObject` usa las claves `rabbitmq-user` / `rabbitmq-password` de `tse-runtime-secrets`. El Deployment no declara `replicas`: las maneja KEDA.

**Datos del nodo de balanzas** para el CronJob del poller (corre cada 30 minutos con `ENVIRONMENT=prod`):

- La URL de balanzas y el client id están en `k8s/apps/fiscalizacionautomatica/app.json` (`balanzasUrl`, `balanzasClientId`), que el `configMapGenerator` publica como ConfigMap y el CronJob monta en `/app/config/app.json`. Se cambian en el repo y Argo CD los sincroniza.
- El client secret está en el Secret `fiscalizacion-balanzas` (`secret-balanzas.yaml`), que Argo CD crea con la clave vacía y no vuelve a sincronizar (`ignoreDifferences` en la Application). El valor de producción se carga a mano:

```bash
kubectl -n tse patch secret fiscalizacion-balanzas --type merge \
  -p '{"stringData":{"BALANZAS_CLIENT_SECRET":"<client secret>"}}'
```

Mientras esté vacío, el poller falla al arrancar (`Missing environment variables`) y el Job queda como fallido: `kubectl -n tse get jobs`.

## Registrar aplicaciones en Argo CD

Con los manifiestos e imágenes publicados en `main`:

```bash
kubectl apply -f k8s/argocd/infrastructure.yaml

kubectl apply -f k8s/argocd/frontofficeciudadanos-front.yaml
kubectl apply -f k8s/argocd/frontofficefuncionarios-front.yaml
kubectl apply -f k8s/argocd/backofficeautoridadesadministradores-front.yaml

kubectl apply -f k8s/argocd/wildfly.yaml
kubectl apply -f k8s/argocd/fiscalizacionautomatica.yaml
```

## Validación

### Validar manifiestos sin acceder al cluster

```bash
kubectl kustomize k8s/infrastructure

kubectl kustomize k8s/apps/frontofficeciudadanos-front
kubectl kustomize k8s/apps/frontofficefuncionarios-front
kubectl kustomize k8s/apps/backofficeautoridadesadministradores-front
kubectl kustomize k8s/apps/wildfly
kubectl kustomize k8s/apps/fiscalizacionautomatica
```

### Comprobar recursos desplegados

```bash
kubectl -n tse get deployments
kubectl -n tse get pods
kubectl -n tse get services
kubectl -n tse get ingress
```

### Comprobar servicios externos

Verificar que los Services Kubernetes apunten a los destinos configurados en Elastic Cloud:

```bash
kubectl -n tse get svc postgres mongodb rabbitmq -o wide
```

Para validar la conectividad real, se deben comprobar desde un pod del cluster las conexiones TCP a los siguientes servicios:

- PostgreSQL: `postgres:5432`
- MongoDB: `mongodb:27017`
- RabbitMQ: `rabbitmq:5672`

Los Services de tipo `ExternalName` proporcionan resolución DNS, pero no garantizan por sí solos que los destinos externos acepten conexiones TCP.

También deben verificarse las credenciales y la inicialización correspondiente de MongoDB y RabbitMQ.

### Consultar logs

```bash
kubectl -n tse logs deployment/wildfly

kubectl -n tse logs deployment/fiscalizacionautomatica
```

Para observar el estado de los pods:

```bash
kubectl -n tse get pods -w
```

## Acceso HTTP

Host configurado en NGINX Ingress:

```text
env-6537388.web.elasticloud.uy
```

Rutas de los frontends:

```text
/ciudadanos
/funcionarios
/backoffice
```

Rutas de las aplicaciones Jakarta EE:

```text
/frontofficeciudadanos-web
/frontofficefuncionarios-web
/backofficeautoridadesadministradores-web
```

## Interfaz de Argo CD

Acceder mediante port-forward:

```bash
kubectl -n argocd port-forward svc/argocd-server 8081:443
```

Abrir:

```text
https://localhost:8081
```

Obtener la contraseña inicial del usuario `admin`:

```bash
kubectl -n argocd get secret argocd-initial-admin-secret \
  -o jsonpath="{.data.password}" | base64 -d
```

## Diagnóstico

- **ImagePullBackOff:** comprobar `ghcr-credentials` en el namespace `tse` y los permisos de GHCR.
- **Argo CD no accede al repositorio:** verificar `repo-tse-central`.
- **Image Updater no detecta imágenes:** revisar sus logs y `ghcr-credentials` en `argocd`.
- **Contenedores sin credenciales:** verificar las claves de `tse-runtime-secrets`.
- **Error de conexión con infraestructura externa:** comprobar resolución DNS, conectividad TCP, puertos, credenciales y disponibilidad de los servicios en Elastic Cloud.
- **WildFly no inicia:** revisar logs y conectividad con PostgreSQL.
- **Fiscalización no consume eventos:** revisar la conexión a RabbitMQ, la cola `tracking.events` y MongoDB.
- **Front de funcionarios sin sesión o con 401 del backend:** verificar `frontofficefuncionarios-front-secrets` (`SESSION_SECRET` y `BACKEND_API_KEY`).
