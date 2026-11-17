# carga.uy — Despliegue de Central en Kubernetes

Esta guía describe el despliegue de Central en ANTEL Elastic Cloud mediante
Kubernetes, Kustomize, Argo CD e Image Updater.

Los comandos se ejecutan desde la raíz de TSE_CENTRAL, con kubectl configurado
para el clúster y las imágenes publicadas en GHCR.

El arranque local con Docker se documenta en el [README principal](../README.md).

## Componentes

### Frontends

- `frontofficeciudadanos-front`
- `frontofficefuncionarios-front`
- `backofficeautoridadesadministradores-front`

Cada frontend dispone de su propio Deployment, Service e Ingress.

### WildFly

Servidor de aplicaciones que contiene los tres EAR Jakarta EE:

- `frontofficeciudadanos.ear`
- `frontofficefuncionarios.ear`
- `backofficeautoridadesadministradores.ear`

El HPA definido en `k8s/apps/wildfly/hpa.yaml` mantiene entre 2 y 3 réplicas,
con un objetivo de utilización de CPU del 70 %. Requiere metrics-server.

El backoffice utiliza tokens firmados mediante `BACKOFFICE_JWT_SECRET`.
Los timers EJB de revalidación de PDI y vencimiento de descargos utilizan
un lock en PostgreSQL para evitar su ejecución simultánea entre réplicas.

### Fiscalización automática

Aplicación Java independiente que consume la cola `tracking.events`,
persiste los eventos en MongoDB y detecta situaciones de fiscalización.

Se ejecuta como Deployment, sin Service ni Ingress. KEDA ajusta su cantidad
de réplicas entre 1 y 5.

La misma imagen se utiliza en el CronJob `fiscalizacionautomatica-poller`,
que consulta Balanzas cada 30 minutos.

### Infraestructura externa

PostgreSQL, MongoDB y RabbitMQ están alojados en ANTEL Elastic Cloud,
fuera del clúster Kubernetes. Las aplicaciones acceden mediante Services
de tipo `ExternalName`.

| Service | Destino | Puerto |
|---|---|---|
| `postgres` | `node6259-tse-postgres.web.elasticloud.uy` | 5432 |
| `mongodb` | `node6260-tse-mongo.web.elasticloud.uy` | 27017 |
| `rabbitmq` | `tse-rabbitmq.web.elasticloud.uy` | 5672 |

KEDA también requiere acceso a la API de administración de RabbitMQ
en el puerto 15672.

La inicialización de estos servicios se realiza por separado:

- `rabbitmq/definitions.json`: exchange `tracking`, cola `tracking.events`
  y binding con routing key `tracking.event`.
- `mongodb/init/01-tracking.js`: colección `tracking`.
- `mongodb/init/02-balanzas-metadata.js`: colección `balanzas_metadata`.

## Organización del directorio

- `k8s/apps/`: manifiestos y configuración Kustomize de las aplicaciones.
- `k8s/infrastructure/`: Services para la infraestructura externa.
- `k8s/argocd/`: Applications de Argo CD y recursos de Image Updater.

## Publicación y actualización de imágenes

Los workflows de GitHub Actions construyen y publican las imágenes Docker
en GitHub Container Registry, con los tags:

```text
latest
sha-<commit>
```

Image Updater selecciona tags `sha-<commit>` de siete caracteres hexadecimales
mediante la estrategia `newest-build`. Actualiza las aplicaciones mediante
write-back `argocd`, y Argo CD sincroniza los cambios con Kubernetes.

PostgreSQL, MongoDB y RabbitMQ se administran externamente; sus actualizaciones
no son gestionadas por los manifiestos de aplicaciones ni por Image Updater.

## Configuración inicial

### 1. Requisitos del clúster

- Controlador NGINX Ingress.
- metrics-server para el HPA de WildFly.
- Argo CD.
- Argo CD Image Updater compatible con el recurso `ImageUpdater`.
- KEDA para el escalado de fiscalización.

Crear los namespaces si todavía no existen:

```bash
kubectl create namespace tse
kubectl create namespace argocd
```

### 2. Secretos de las aplicaciones

El Secret `tse-runtime-secrets`, en el namespace `tse`, contiene las
credenciales compartidas por las aplicaciones.

| Claves | Uso |
|---|---|
| `postgres-user`, `postgres-password` | Conexión de WildFly a PostgreSQL |
| `mongo-user`, `mongo-password` | Conexión a MongoDB |
| `rabbitmq-user`, `rabbitmq-password` | Conexión a RabbitMQ y autenticación de KEDA |
| `ciudadanos-api-key` | Clave compartida entre el frontend y backend de ciudadanos |
| `funcionarios-api-key` | Clave compartida entre el frontend y backend de funcionarios |
| `ejb-user`, `ejb-password` | Autenticación EJB remota de fiscalización |
| `oidc-client-secret` | Cliente ID Uruguay de los frontends y WildFly |
| `session-secret` | Sesión del frontend de ciudadanos |
| `movil-token-secret` | Referenciado por WildFly y el frontend de ciudadanos; WildFly lo usa para firmar tokens móviles |
| `backoffice-jwt-secret` | Firma de tokens del backoffice en WildFly |
| `pdi-organismo-clave` | Autenticación de Central ante la PDI |

Crear el Secret con los valores del entorno:

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

WildFly crea el usuario EJB al arrancar mediante `wildfly/start.sh`.
Fiscalización necesita esas credenciales para consultar los GPS activos
antes de comenzar a consumir eventos.

Para actualizar claves de un Secret existente:

```bash
kubectl -n tse patch secret tse-runtime-secrets --type merge \
  -p '{"stringData":{"ejb-user":"<EJB_USER>","ejb-password":"<EJB_PASSWORD>"}}'
```

Después de cambiar secretos utilizados como variables de entorno,
reiniciar los Deployments afectados para que tomen los nuevos valores.

### 3. Configuración e integración con ID Uruguay y PDI

La configuración no secreta se encuentra junto a cada aplicación:

- `k8s/apps/frontofficeciudadanos-front/configmap.yaml`:
  `APP_BASE_URL`, `OIDC_CLIENT_ID` y `BACKEND_URL`.
- `k8s/apps/frontofficefuncionarios-front/app.json`:
  URLs del frontend, backend y configuración OIDC.
- `k8s/apps/wildfly/configmap.yaml`:
  URL y código del organismo PDI, entre otros parámetros.

Las URI de retorno y logout deben estar registradas en ID Uruguay:

```text
https://env-6537388.web.elasticloud.uy/ciudadanos
https://env-6537388.web.elasticloud.uy/ciudadanos/logout
https://env-6537388.web.elasticloud.uy/funcionarios
https://env-6537388.web.elasticloud.uy/funcionarios/logout
```

`PDI_URL` debe apuntar al servicio PDI desplegado. El código del organismo
y `pdi-organismo-clave` deben coincidir con sus credenciales.

### 4. Instalar Argo CD e Image Updater

Si todavía no están instalados:

```bash
kubectl apply -n argocd --server-side --force-conflicts \
  -f https://raw.githubusercontent.com/argoproj/argo-cd/stable/manifests/install.yaml

kubectl apply -n argocd \
  -f https://raw.githubusercontent.com/argoproj-labs/argocd-image-updater/stable/config/install.yaml
```

### 5. Acceso al repositorio y al registro de imágenes

Para que Argo CD pueda leer el repositorio privado:

```bash
kubectl -n argocd create secret generic repo-tse-central \
  --from-literal=type=git \
  --from-literal=url=https://github.com/IngBryan/TSE_CENTRAL.git \
  --from-literal=username=IngBryan \
  --from-literal=password='<TOKEN_REPO>'

kubectl -n argocd label secret repo-tse-central \
  argocd.argoproj.io/secret-type=repository
```

Para descargar imágenes privadas de GHCR, utilizar un token con
permiso `read:packages`.

Crear las credenciales en `tse`, para los pods:

```bash
kubectl -n tse create secret docker-registry ghcr-credentials \
  --docker-server=ghcr.io \
  --docker-username=IngBryan \
  --docker-password='<TOKEN_PACKAGES>'
```

Crear las credenciales en `argocd`, para Image Updater:

```bash
kubectl -n argocd create secret docker-registry ghcr-credentials \
  --docker-server=ghcr.io \
  --docker-username=IngBryan \
  --docker-password='<TOKEN_PACKAGES>'
```

### 6. Configuración del frontend de funcionarios

El Secret `frontofficefuncionarios-front-secrets` contiene:

- `SESSION_SECRET`: secreto de sesión, distinto del utilizado por ciudadanos.
- `BACKEND_API_KEY`: mismo valor que `funcionarios-api-key`
  de `tse-runtime-secrets`.

Completar `SESSION_SECRET` con una clave larga y aleatoria generada previamente.

```bash
kubectl -n tse create secret generic frontofficefuncionarios-front-secrets \
  --from-literal=SESSION_SECRET='<FUNCIONARIOS_SESSION_SECRET>' \
  --from-literal=BACKEND_API_KEY='<FUNCIONARIOS_API_KEY>'
```

El Ingress de funcionarios referencia un Secret TLS:

```bash
kubectl -n tse create secret tls frontofficefuncionarios-front-tls \
  --cert='<certificado.pem>' \
  --key='<clave.pem>'
```

El ingreso de testing por cédula requiere `LOGIN_TESTING=true` en el
frontend y `FUNCIONARIOS_INGRESO_TESTING=true` en WildFly.
Deshabilitar ambos para utilizar exclusivamente ID Uruguay.

### 7. KEDA y poller de Balanzas

KEDA escala fiscalización entre 1 y 5 réplicas combinando dos señales:

- Tasa de publicación: objetivo de 1.500 mensajes por segundo por réplica.
- Cola pendiente: objetivo de 300 mensajes por réplica.

El manifiesto está en `k8s/apps/fiscalizacionautomatica/keda.yaml`.
Consulta cada 15 segundos y configura una ventana de estabilización
de reducción de 300 segundos, con una réplica menos cada 120 segundos.

Instalar KEDA si todavía no está disponible:

```bash
kubectl apply --server-side \
  -f https://github.com/kedacore/keda/releases/download/v2.16.1/keda-2.16.1.yaml
```

El ScaledObject utiliza `rabbitmq-user` y `rabbitmq-password`
de `tse-runtime-secrets`. KEDA administra las réplicas del Deployment.

El poller consulta Balanzas cada 30 minutos con `ENVIRONMENT=prod`:

- URL y client ID: `k8s/apps/fiscalizacionautomatica/app.json`,
  montado mediante ConfigMap.
- Client secret: Secret `fiscalizacion-balanzas`.

Argo CD crea ese Secret con la clave vacía y conserva las modificaciones
manuales mediante `ignoreDifferences`. Después de su creación, cargar:

```bash
kubectl -n tse patch secret fiscalizacion-balanzas --type merge \
  -p '{"stringData":{"BALANZAS_CLIENT_SECRET":"<client secret>"}}'
```

Mientras esté vacío, el poller falla al arrancar.

## Registrar aplicaciones en Argo CD

Con los manifiestos y las imágenes publicados en `main`, y los requisitos
anteriores preparados:

```bash
kubectl apply -f k8s/argocd/infrastructure.yaml
kubectl apply -f k8s/argocd/frontofficeciudadanos-front.yaml
kubectl apply -f k8s/argocd/frontofficefuncionarios-front.yaml
kubectl apply -f k8s/argocd/backofficeautoridadesadministradores-front.yaml
kubectl apply -f k8s/argocd/wildfly.yaml
kubectl apply -f k8s/argocd/fiscalizacionautomatica.yaml
```

## Validación

### Manifiestos

Estos comandos generan los manifiestos sin aplicarlos al clúster:

```bash
kubectl kustomize k8s/infrastructure
kubectl kustomize k8s/apps/frontofficeciudadanos-front
kubectl kustomize k8s/apps/frontofficefuncionarios-front
kubectl kustomize k8s/apps/backofficeautoridadesadministradores-front
kubectl kustomize k8s/apps/wildfly
kubectl kustomize k8s/apps/fiscalizacionautomatica
```

### Recursos desplegados

```bash
kubectl -n tse get deployments
kubectl -n tse get pods
kubectl -n tse get services
kubectl -n tse get ingress
kubectl -n tse get jobs
kubectl -n tse get hpa
kubectl -n tse get scaledobjects
```

### Infraestructura externa

Comprobar los destinos de los Services:

```bash
kubectl -n tse get svc postgres mongodb rabbitmq -o wide
```

Verificar desde el clúster la conectividad con:

- PostgreSQL: `postgres:5432`.
- MongoDB: `mongodb:27017`.
- RabbitMQ: `rabbitmq:5672`.
- Administración de RabbitMQ para KEDA: `rabbitmq:15672`.

Los Services `ExternalName` proporcionan resolución DNS; también deben
comprobarse la conectividad TCP, las credenciales y la inicialización
de los servicios externos.

### Logs

```bash
kubectl -n tse logs deployment/wildfly
kubectl -n tse logs deployment/fiscalizacionautomatica
kubectl -n tse get pods -w
```

## Acceso a las aplicaciones

Host configurado en los Ingress:

```text
env-6537388.web.elasticloud.uy
```

| Aplicación | Ruta |
|---|---|
| Ciudadanos | `/ciudadanos` |
| Funcionarios | `/funcionarios` |
| Backoffice | `/backoffice` |
| Backend de ciudadanos | `/frontofficeciudadanos-web` |
| Backend de funcionarios | `/frontofficefuncionarios-web` |
| Backend de autoridades y administradores | `/backofficeautoridadesadministradores-web` |

## Interfaz de Argo CD

Crear un port-forward:

```bash
kubectl -n argocd port-forward svc/argocd-server 9090:443
```

Abrir `https://localhost:9090`.

Para obtener la contraseña inicial de `admin`:

```bash
kubectl -n argocd get secret argocd-initial-admin-secret \
  -o jsonpath="{.data.password}" | base64 -d
```

## Diagnóstico

| Problema | Comprobaciones |
|---|---|
| `ImagePullBackOff` | Secret `ghcr-credentials` en `tse` y permisos de GHCR |
| Argo CD no accede al repositorio | Secret `repo-tse-central` |
| Image Updater no actualiza imágenes | Tags publicados, logs y credenciales en `argocd` |
| WildFly no inicia | Logs, PostgreSQL y secretos requeridos |
| Fiscalización no consume | RabbitMQ, cola `tracking.events`, MongoDB y autenticación EJB |
| Poller fallido | Credenciales de Balanzas, conectividad y logs del Job |
| Funcionarios responde 401 | Correspondencia de `BACKEND_API_KEY` con `funcionarios-api-key` |
| KEDA no escala | Acceso a RabbitMQ, credenciales y estado del ScaledObject |
| HPA no escala | metrics-server, métricas disponibles y solicitudes de CPU |
