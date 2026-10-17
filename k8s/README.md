
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

**Fiscalizacionautomatica:** daemon Java independiente que consume eventos de la cola `tracking.events` de RabbitMQ y los persiste en MongoDB. Utiliza un Deployment sin Service ni Ingress, ya que no expone una interfaz HTTP.

### Infraestructura externa

PostgreSQL, MongoDB y RabbitMQ se encuentran alojados en ANTEL Elastic Cloud, fuera del cluster Kubernetes.

Para acceder a estos servicios, Kubernetes utiliza Services de tipo `ExternalName`, definidos en `k8s/infrastructure/`.

| Service | Destino | Puerto |
|---|---|---|
| `postgres` | `tse-postgres.web.elasticloud.uy` | 5432 |
| `mongodb` | `tse-mongo.web.elasticloud.uy` | 27017 |
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
```

Crearlo con los valores correspondientes al entorno:

```bash
kubectl -n tse create secret generic tse-runtime-secrets \
  --from-literal=postgres-user='<POSTGRES_USER>' \
  --from-literal=postgres-password='<POSTGRES_PASSWORD>' \
  --from-literal=mongo-user='<MONGO_USER>' \
  --from-literal=mongo-password='<MONGO_PASSWORD>' \
  --from-literal=rabbitmq-user='<RABBITMQ_USER>' \
  --from-literal=rabbitmq-password='<RABBITMQ_PASSWORD>'
```

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

`SESSION_SECRET` cifra la cookie de sesión (string largo y aleatorio, distinto del de ciudadanos) y `BACKEND_API_KEY` debe coincidir con la que recibe WildFly.

```bash
kubectl -n tse create secret generic frontofficefuncionarios-front-secrets \
  --from-literal=SESSION_SECRET=$(node -e "console.log(require('crypto').randomBytes(32).toString('hex'))") \
  --from-literal=BACKEND_API_KEY='<BACKEND_API_KEY>'
```

Certificado TLS del host del ingress (`env-6537388.web.elasticloud.uy`), a partir del certificado y su clave privada en formato PEM:

```bash
kubectl -n tse create secret tls frontofficefuncionarios-front-tls \
  --cert=<certificado.pem> \
  --key=<clave.pem>
```

Las URLs de la aplicación y del backend no son secretas: se encuentran en `k8s/apps/frontofficefuncionarios-front/app.json`, que se monta como ConfigMap en `/app/config/app.json`.

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
