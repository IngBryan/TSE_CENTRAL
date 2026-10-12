# Deploy en Kubernetes con Argo CD

El componente central de TSE se despliega en Kubernetes utilizando Argo CD.

## Componentes desplegados

### Frontend

- `frontofficeciudadanos-front`
- `frontofficefuncionarios-front`
- `backofficeautoridadesadministradores-front`

Cada frontend se despliega de forma independiente y publica su propia imagen en GitHub Container Registry (GHCR).

### Backend

Las tres aplicaciones Jakarta EE se ejecutan en una única instancia de WildFly:

- Frontoffice ciudadanos
- Frontoffice funcionarios
- Backoffice autoridades/administradores

La imagen utilizada es:

```text
ghcr.io/ingbryan/tse_central/wildfly
```

### Infraestructura

La infraestructura desplegada en Kubernetes está formada por:

- PostgreSQL
- MongoDB
- RabbitMQ

Los datos persistentes utilizan `PersistentVolumeClaim`.

`Fiscalizacionautomatica` todavía no forma parte del despliegue runtime y se configurará posteriormente como componente independiente.

## Estructura

```text
k8s/
├── apps/
│   ├── frontofficeciudadanos-front/
│   ├── frontofficefuncionarios-front/
│   ├── backofficeautoridadesadministradores-front/
│   └── wildfly/
│
├── infrastructure/
│   ├── postgres/
│   ├── mongodb/
│   ├── rabbitmq/
│   └── kustomization.yaml
│
└── argocd/
    ├── frontofficeciudadanos-front.yaml
    ├── frontofficefuncionarios-front.yaml
    ├── backofficeautoridadesadministradores-front.yaml
    ├── infrastructure.yaml
    └── wildfly.yaml
```

Cada aplicación en `k8s/apps/` contiene sus manifiestos Kubernetes y su
`kustomization.yaml`.

La infraestructura se administra conjuntamente mediante:

```text
k8s/infrastructure/kustomization.yaml
```

## Flujo de CD

```text
Push / merge a main
        |
        v
GitHub Actions
        |
        +-- Build / test / análisis
        |
        +-- Build imagen Docker
        |
        v
GitHub Container Registry
        |
        v
Argo CD Image Updater
        |
        v
Argo CD
        |
        v
Kubernetes
```

GitHub Actions no necesita ingresar al cluster.

Las imágenes de las aplicaciones se publican con los tags:

```text
latest
sha-<commit>
```

Argo CD Image Updater observa los tags `sha-*` y actualiza las aplicaciones correspondientes.

PostgreSQL, MongoDB y RabbitMQ utilizan imágenes definidas explícitamente en sus manifiestos y no son actualizadas automáticamente mediante Image Updater.

## Secretos de runtime

Las credenciales de runtime no se almacenan en Git.

Los manifiestos esperan un Secret llamado:

```text
tse-runtime-secrets
```

en el namespace:

```text
tse
```

El Secret debe contener las siguientes claves:

```text
postgres-user
postgres-password
mongo-user
mongo-password
rabbitmq-user
rabbitmq-password
```

Antes del primer despliegue se crea directamente en el cluster:

```bash
kubectl -n tse create secret generic tse-runtime-secrets \
  --from-literal=postgres-user='<POSTGRES_USER>' \
  --from-literal=postgres-password='<POSTGRES_PASSWORD>' \
  --from-literal=mongo-user='<MONGO_USER>' \
  --from-literal=mongo-password='<MONGO_PASSWORD>' \
  --from-literal=rabbitmq-user='<RABBITMQ_USER>' \
  --from-literal=rabbitmq-password='<RABBITMQ_PASSWORD>'
```

Los valores reales nunca deben agregarse al repositorio.

## Configuración inicial de Argo CD

### 1. Instalar Argo CD y Argo CD Image Updater

```bash
kubectl create namespace argocd

kubectl apply -n argocd --server-side --force-conflicts \
  -f https://raw.githubusercontent.com/argoproj/argo-cd/stable/manifests/install.yaml

kubectl apply -n argocd \
  -f https://raw.githubusercontent.com/argoproj-labs/argocd-image-updater/stable/config/install.yaml
```

### 2. Dar acceso de Argo CD al repositorio

El repositorio es privado, por lo que Argo CD necesita credenciales de lectura.

```bash
kubectl -n argocd create secret generic repo-tse-central \
  --from-literal=type=git \
  --from-literal=url=https://github.com/IngBryan/TSE_CENTRAL.git \
  --from-literal=username=IngBryan \
  --from-literal=password=<TOKEN_REPO>

kubectl -n argocd label secret repo-tse-central \
  argocd.argoproj.io/secret-type=repository
```

### 3. Dar acceso a GHCR

El namespace `tse` necesita credenciales para descargar las imágenes privadas:

```bash
kubectl create namespace tse

kubectl -n tse create secret docker-registry ghcr-credentials \
  --docker-server=ghcr.io \
  --docker-username=IngBryan \
  --docker-password=<TOKEN_PACKAGES>
```

Argo CD Image Updater también necesita acceso a GHCR:

```bash
kubectl -n argocd create secret docker-registry ghcr-credentials \
  --docker-server=ghcr.io \
  --docker-username=IngBryan \
  --docker-password=<TOKEN_PACKAGES>
```

## Registrar las aplicaciones en Argo CD

Una vez que los manifiestos se encuentren en `main`:

```bash
kubectl apply -f k8s/argocd/infrastructure.yaml
kubectl apply -f k8s/argocd/frontofficeciudadanos-front.yaml
kubectl apply -f k8s/argocd/frontofficefuncionarios-front.yaml
kubectl apply -f k8s/argocd/backofficeautoridadesadministradores-front.yaml
kubectl apply -f k8s/argocd/wildfly.yaml
```

A partir de ese momento Argo CD mantiene sincronizado el estado del cluster con los manifiestos almacenados en Git.

## Validación offline

Los manifiestos pueden validarse sin conexión al cluster utilizando Kustomize:

```bash
kubectl kustomize k8s/apps/frontofficeciudadanos-front
kubectl kustomize k8s/apps/frontofficefuncionarios-front
kubectl kustomize k8s/apps/backofficeautoridadesadministradores-front
kubectl kustomize k8s/apps/wildfly
kubectl kustomize k8s/infrastructure
```

## Acceso HTTP

Los componentes se publican mediante NGINX Ingress sobre:

```text
env-6537388.web.elasticloud.uy
```

Rutas de los frontends:

```text
/ciudadanos
/funcionarios
/backoffice
```

Rutas de las aplicaciones Jakarta EE desplegadas en WildFly:

```text
/frontofficeciudadanos-web
/frontofficefuncionarios-web
/backofficeautoridadesadministradores-web
```

## Interfaz web de Argo CD

Para acceder localmente a la interfaz de Argo CD:

```bash
kubectl -n argocd port-forward svc/argocd-server 8081:443
```

Luego abrir:

```text
https://localhost:8081
```

La contraseña inicial del usuario `admin` puede obtenerse con:

```bash
kubectl -n argocd get secret argocd-initial-admin-secret \
  -o jsonpath="{.data.password}" | base64 -d
```

## Diagnóstico

Si un pod queda en `ImagePullBackOff`, revisar que exista `ghcr-credentials` en el namespace `tse`.

Si Argo CD no puede leer el repositorio, revisar el Secret `repo-tse-central`.

Si Image Updater no detecta nuevas imágenes, revisar sus logs y verificar que `ghcr-credentials` exista en el namespace `argocd`.

Si una aplicación no arranca por falta de credenciales de runtime, verificar que exista `tse-runtime-secrets` en el namespace `tse`.
