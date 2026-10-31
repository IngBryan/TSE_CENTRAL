# frontoffice-k6

Prueba de carga de las consultas del frontoffice con [k6](https://k6.io), para el objetivo 3a de la
Seccion 4.3: **el percentil 95 del tiempo de respuesta debe ser menor a 500 ms**. Se corre a la par
del generador de tracking (`load-tests/tracking-generator`) a la tasa estimada, asi el tiempo de
respuesta se mide con la plataforma recibiendo eventos (objetivos 3b y 3c).

## Que hace

- **Ciudadanos** (`/ciudadanos`): mi cuenta, empresa, guias y el detalle de una guia, vehiculos y el
  detalle de un vehiculo, choferes.
- **Funcionarios** (`/funcionarios`): bandeja de casos y el detalle de un caso, empresas y el detalle
  de una empresa (el inicio no: con sesion redirige a la bandeja).

Las paginas se renderizan en el servidor, asi que cada pedido incluye las consultas al backend. Cada
frontend recibe una cantidad fija de pedidos por segundo (ejecutor `constant-arrival-rate`), tarde lo
que tarde cada uno. Las sesiones se abren una sola vez al principio con el **ingreso de testing**
(`LOGIN_TESTING=true` en los fronts), y los ids de las paginas de detalle salen de los links de las
listas. Un redirect cuenta como error: quiere decir que se perdio la sesion.

Al terminar, k6 muestra el p95 por frontend y falla (codigo de salida distinto de 0) si alguno
supera los 500 ms o si mas del 1 % de los pedidos no devuelve 200.

## Configuracion

Todo por variables (`-e NOMBRE=valor`):

| Variable | Por defecto | Que es |
| --- | --- | --- |
| `CEDULA` | (obligatoria) | Cedula para el ingreso de testing, registrada como responsable de empresa y como funcionario |
| `BASE_URL` | `https://env-6537388.web.elasticloud.uy` | Donde estan los fronts |
| `DURATION` | `15m` | Duracion |
| `CITIZENS_RATE` | `10` | Pedidos por segundo al front de ciudadanos |
| `OFFICIALS_RATE` | `5` | Pedidos por segundo al front de funcionarios |

La cedula no va en el repositorio: se pasa al correrlo.

## Instalar k6 (en el servidor de pruebas)

Es un unico binario, no hace falta root. En el nodo `teststress`:

```bash
mkdir -p ~/k6 && cd ~/k6 && curl -sL https://github.com/grafana/k6/releases/download/v1.0.0/k6-v1.0.0-linux-amd64.tar.gz | tar xz --strip-components=1
```

Y desde la raiz del repo, en la PC:

```bash
scp -P 3022 load-tests/frontoffice-k6/consultas.js 6307-648@gate.elasticloud.uy:/home/jelastic/k6/
```

## Correr

El ingress del cluster responde con su certificado por defecto (emitido para `ingress.local`), asi
que hace falta `--insecure-skip-tls-verify` mientras el dominio no tenga un certificado valido.

```bash
cd ~/k6 && ./k6 run --insecure-skip-tls-verify -e CEDULA=<cedula> consultas.js
```

Para la prueba de los objetivos 3a, 3b y 3c, lanzar primero el generador de tracking a la tasa
estimada (perfil `constant`, misma duracion) y despues k6.

## Ver el p95 en Grafana

k6 puede mandar sus metricas al Prometheus de Grafana Cloud, y el p95 queda en el tiempo al lado de
RabbitMQ, MongoDB y WildFly (evidencia de la Seccion 4.4 y del objetivo 3c). La URL de remote write,
el usuario (instance ID) y un token con permiso `metrics:write` salen de Grafana Cloud → Prometheus →
Details:

```bash
K6_PROMETHEUS_RW_SERVER_URL=<url>/api/prom/push K6_PROMETHEUS_RW_USERNAME=<instance id> K6_PROMETHEUS_RW_PASSWORD=<token> K6_PROMETHEUS_RW_TREND_STATS="p(95),p(99),avg" ./k6 run --insecure-skip-tls-verify -o experimental-prometheus-rw -e CEDULA=<cedula> consultas.js
```

En Grafana, el p95 por frontend es `k6_http_req_duration_p95{kind="frontoffice"}` agrupado por `front`.
