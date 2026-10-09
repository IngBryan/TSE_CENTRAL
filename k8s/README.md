# Deploy en Kubernetes con Argo CD

Por ahora solo esta configurado **Frontoffice funcionarios (frontend)**, como prueba.

## Como funciona

```
GitHub (CI)                                   Cluster (CD)
push a main con cambios en el front
 -> workflow "Frontend - Frontoffice funcionarios"
 -> publica ghcr.io/.../frontofficefuncionarios-front:sha-<commit>
                                       ---->  Argo CD Image Updater (revisa cada 2 min)
                                               ve la imagen nueva y actualiza la app
                                              Argo CD reemplaza el pod: solo esta app se reinicia
```

GitHub nunca entra al cluster: es el cluster el que se trae lo nuevo. Alcanza con que el
cluster tenga salida a internet (sirve tambien con un cluster local: minikube, kind, k3s,
Docker Desktop).

## Estructura

| Carpeta | Que tiene |
|---|---|
| `k8s/apps/<app>/` | Manifiestos de cada app (Deployment + Service), con Kustomize |
| `k8s/argocd/<app>.yaml` | `Application` (que carpeta del repo despliega Argo CD) + `ImageUpdater` (que imagen vigila) |

## Antes de empezar

- La imagen tiene que existir en ghcr.io: el workflow "Frontend - Frontoffice funcionarios"
  tiene que haber corrido en main al menos una vez.
- Dos tokens de GitHub (Settings -> Developer settings). **Nunca se suben al repo.**
  1. `TOKEN_PACKAGES`: *classic*, solo con el permiso `read:packages`. Para bajar las imagenes.
  2. `TOKEN_REPO`: *fine-grained*, solo para el repo TSE_CENTRAL, permiso **Contents: Read-only**.
     Para que Argo CD pueda leer el repo (es privado).

## Pasos (una sola vez)

**1. Instalar Argo CD y Argo CD Image Updater**

```bash
kubectl create namespace argocd
kubectl apply -n argocd --server-side --force-conflicts -f https://raw.githubusercontent.com/argoproj/argo-cd/stable/manifests/install.yaml
kubectl apply -n argocd -f https://raw.githubusercontent.com/argoproj-labs/argocd-image-updater/stable/config/install.yaml
```

**2. Darle a Argo CD acceso al repo**

```bash
kubectl -n argocd create secret generic repo-tse-central \
  --from-literal=type=git \
  --from-literal=url=https://github.com/IngBryan/TSE_CENTRAL.git \
  --from-literal=username=IngBryan \
  --from-literal=password=<TOKEN_REPO>
kubectl -n argocd label secret repo-tse-central argocd.argoproj.io/secret-type=repository
```

**3. Credenciales de ghcr.io**, en los dos namespaces: `tse` (para que el cluster baje la imagen)
y `argocd` (para que Image Updater pueda ver que versiones hay).

```bash
kubectl create namespace tse
kubectl -n tse create secret docker-registry ghcr-credentials \
  --docker-server=ghcr.io --docker-username=IngBryan --docker-password=<TOKEN_PACKAGES>
kubectl -n argocd create secret docker-registry ghcr-credentials \
  --docker-server=ghcr.io --docker-username=IngBryan --docker-password=<TOKEN_PACKAGES>
```

**4. Registrar la app en Argo CD**

```bash
kubectl apply -f k8s/argocd/frontofficefuncionarios-front.yaml
```

## Probar

```bash
kubectl -n tse get pods
kubectl -n tse port-forward svc/frontofficefuncionarios-front 3003:80
```

Y abrir http://localhost:3003

**Ciclo completo:** cambiar algo en `componente_central/frontend/Frontofficefuncionarios`, pushear
a main y esperar a que termine el workflow. En unos 2 minutos el pod se reemplaza solo
(`kubectl -n tse get pods -w`) y la imagen pasa a ser la del commit nuevo:

```bash
kubectl -n tse get deploy frontofficefuncionarios-front -o jsonpath="{.spec.template.spec.containers[0].image}"
```

**Interfaz web de Argo CD** (usuario `admin`):

```bash
kubectl -n argocd port-forward svc/argocd-server 8081:443
```

Abrir https://localhost:8081. La password inicial se obtiene con:

```bash
# Git Bash / Linux / Mac
kubectl -n argocd get secret argocd-initial-admin-secret -o jsonpath="{.data.password}" | base64 -d
```

```powershell
# PowerShell
[Text.Encoding]::UTF8.GetString([Convert]::FromBase64String((kubectl -n argocd get secret argocd-initial-admin-secret -o jsonpath="{.data.password}")))
```

## Si algo no anda

- Pod en `ImagePullBackOff`: falta el secret `ghcr-credentials` en `tse` o el token no tiene `read:packages`.
- La app no se actualiza sola: ver los logs de Image Updater
  (`kubectl -n argocd get deploy` para ver el nombre, despues `kubectl -n argocd logs deploy/<nombre>`).
- Argo CD dice que no puede leer el repo: revisar el secret `repo-tse-central` y el `TOKEN_REPO`.
