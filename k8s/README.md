
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

### Infraestructura

- PostgreSQL
- MongoDB
- RabbitMQ

Los tres servicios disponen de almacenamiento persistente mediante PVC.

## Organización

- `k8s/apps/`: manifiestos de cada aplicación y sus archivos Kustomize.
- `k8s/infrastructure/`: PostgreSQL, MongoDB y RabbitMQ.
- `k8s/argocd/`: Applications de Argo CD y configuraciones de Image Updater.

## Publicación y actualización

Los workflows de GitHub Actions construyen y publican las imágenes Docker en GitHub Container Registry (GHCR).

Las imágenes se publican con los tags:

```text
latest
sha-<commit>
```

Argo CD Image Updater detecta las nuevas imágenes y actualiza la aplicación correspondiente. Argo CD sincroniza los cambios con Kubernetes.

Las imágenes de PostgreSQL, MongoDB y RabbitMQ se definen directamente en los manifiestos y no utilizan Image Updater.

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
kubectl -n tse get pvc
```

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
- **PVC en Pending:** comprobar la StorageClass y el almacenamiento disponible.
- **WildFly no inicia:** revisar logs y conectividad con PostgreSQL.
- **Fiscalización no consume eventos:** revisar la conexión a RabbitMQ, la cola `tracking.events` y MongoDB.
