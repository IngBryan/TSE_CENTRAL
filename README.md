# TSE Central

Componente central del laboratorio: backend Jakarta EE (desplegado en un unico WildFly), sus
frontends Next.js y el RabbitMQ al que publican los nodos perifericos. Los nodos (`tracking`,
`balanzas`) viven en el repo hermano `TSE_NODOS`.

## Estructura del repositorio

```
TSE_CENTRAL/
├── componente_central/          # Sistema principal, separado en back y front
│   ├── backend/                 # Apps Jakarta EE (Maven multi-modulo: ejb + web + ear)
│   │   ├── Frontofficeciudadanos/
│   │   ├── Frontofficefuncionarios/
│   │   ├── Backofficeautoridadesadministradores/
│   │   └── Fiscalizacionautomatica/   # script standalone (jar), sin ejb/web/ear
│   └── frontend/                # Clientes Next.js que consumen el backend de arriba
│       ├── frontofficeciudadanos/
│       ├── Frontofficefuncionarios/
│       └── backofficeautoridadesadministradores/
│
├── wildfly/                     # Imagen del backend: un WildFly con los 3 EAR (Dockerfile + scripts CLI)
├── logstash/                    # Pipeline de logs (WildFly -> Elasticsearch)
├── rabbitmq/                    # Topologia de RabbitMQ (definitions.json)
├── docker-compose.yml           # Levanta todo: RabbitMQ, PostgreSQL, WildFly, Elasticsearch/Kibana/Logstash y los 3 frontends
└── .gitignore                   # Reglas a nivel workspace (cada modulo tiene el suyo)
```

### componente_central/backend

Tres proyectos Maven, cada uno con la misma forma:

- `pom.xml` padre (`packaging=pom`), Jakarta EE sobre el BOM de WildFly.
- `ejb/` — packaging `ejb`, capa de modelo/servicio/persistencia (`model`, `service`, `data`).
- `web/` — packaging `war`, JSF + PrimeFaces + REST (`web/controller`, `web/jsf`, `web/ws`).
- `ear/` — packaging `ear`, empaqueta `ejb` + `web` para desplegar en WildFly.


Todos comparten `groupId` base `tse.lab.<proyecto>`, Java 24 (`maven.compiler.release=24`)
y corren sobre el mismo WildFly.

**Levantar un modulo:**
```bash
cd componente_central/backend/Frontofficeciudadanos
mvn clean install
mvn wildfly:deploy -pl ear
```

### componente_central/frontend

Tres apps Next.js (una por cada frontend de negocio del backend), mismo stack que
`tracking`/`balanzas`: Next.js 16, React 19, TypeScript, Tailwind 4, React Compiler activado.

```bash
cd componente_central/frontend/frontofficeciudadanos
npm install
npm run dev
```

### Desarrollo de los frontends

```bash
cd componente_central/frontend/<app>
npm install
npm run dev
```

### Docker

Todo el componente central corre en Docker. Los 3 frontends de `componente_central/frontend` estan dockerizados: `Dockerfile` multi-stage
(`deps → builder → runner`, imagen final `node:24-alpine`, `output: "standalone"`) +
`.dockerignore` en cada una, orquestadas desde el `docker-compose.yml` de la raiz.

#### Variables de entorno (`TSE_CENTRAL/.env`)

Crear `TSE_CENTRAL/.env` junto al `docker-compose.yml` (ignorado por git, no entra en las
imagenes):

```bash
#RabbitMQ
RABBITMQ_USER=tse
RABBITMQ_PASSWORD=<un-secreto>

#PostgreSQL (backend)
POSTGRES_USER=tse
POSTGRES_PASSWORD=<un-secreto>         # usuario duenio de la base main_db; WildFly lo usa para conectarse
#POSTGRES_PORT=5433                    # opcional: puerto en la maquina (por defecto 5432); usar otro si ya hay un PostgreSQL local

#Elasticsearch / Kibana (observabilidad)
ELASTIC_PASSWORD=<un-secreto>          # usuario "elastic" (superusuario)
KIBANA_PASSWORD=<otro-secreto>         # usuario interno "kibana_system", lo usa Kibana para conectarse
KIBANA_ENCRYPTION_KEY=<minimo-32-caracteres>
LOGS_PASSWORD=<otro-secreto>            # usuario "tse_logs": con el que Logstash envia los logs
#ELASTIC_VERSION=9.3.0                 # opcional: version de Elasticsearch y Kibana (deben coincidir)
#ELASTIC_HEAP=512m                     # opcional: heap de la JVM de Elasticsearch
#LOGSTASH_HEAP=256m                    # opcional: heap de la JVM de Logstash
```

Las credenciales de los nodos (`balanzas`, `tracking`) van en `TSE_NODOS/.env`.
`RABBITMQ_USER` / `RABBITMQ_PASSWORD` deben ser las mismas en ambos `.env`: el central crea el
usuario y tracking lo usa para publicar.

```bash
docker compose up --build
```

| Servicio                                     | URL                     |
|-----------------------------------------------|--------------------------|
| `elasticsearch` (API)                         | http://localhost:9200    |
| `kibana`                                      | http://localhost:5601    |
| `logstash` (entrada de logs, TCP)             | `localhost:5044`         |
| `wildfly` (las 3 aplicaciones del backend)    | http://localhost:8080    |
| `postgres`                                    | `localhost:5432` (o `POSTGRES_PORT`) |
| `rabbitmq` (panel web)                        | http://localhost:15672   |
| `rabbitmq` (AMQP)                             | `amqp://localhost:5672`  |
| `frontofficeciudadanos-front`                 | http://localhost:3002    |
| `frontofficefuncionarios-front`               | http://localhost:3003    |
| `backofficeautoridadesadministradores-front`  | http://localhost:3004    |

Los puertos `3000` y `3001` los usan `tracking` y `balanzas` (`TSE_NODOS`), asi que ambos
compose pueden correr a la vez.

### Backend (WildFly + PostgreSQL)

Un unico servidor WildFly 41 con las 3 aplicaciones Jakarta EE desplegadas como EAR
(`Frontofficeciudadanos`, `Frontofficefuncionarios`, `Backofficeautoridadesadministradores`),
mas un PostgreSQL con una unica base (`main_db`) compartida por los 3.

```bash
docker compose up -d --build wildfly   # levanta tambien postgres y logstash
```

- **Imagen** (`wildfly/Dockerfile`, multi-stage): compila los proyectos con Maven (JDK 25;
  el codigo usa `release 24`), configura WildFly y copia los EAR a `standalone/deployments`.
  El build no necesita Java ni Maven en la maquina. La primera vez baja las dependencias de
  Maven (varios minutos); despues usa cache. Para reconstruir tras cambiar codigo:
  `docker compose up -d --build wildfly`.
- **Context roots:** cada app queda en `/<proyecto>-web`, por ejemplo
  http://localhost:8080/frontofficeciudadanos-web/. Hoy responden `403` porque todavia no hay
  paginas; un `404` indicaria que la app no esta desplegada.
- **Datasource:** `wildfly/datasources.cli` registra el driver de PostgreSQL y un unico
  datasource, `java:/MainDS`, que usan los `persistence.xml` de los 3 proyectos (comparten base
  y tablas). El host, base, usuario y password se resuelven al arrancar (`DB_HOST`, `DB_NAME`,
  `DB_USER`, `DB_PASSWORD`): no hay secretos en la imagen.
- **Base de datos:** la imagen de postgres crea `main_db` a partir de `POSTGRES_DB`
  (`docker-compose.yml`). Solo ocurre la primera vez (volumen `tse-central_postgres-data`
  vacio); para recrearla hay que borrar el volumen (`docker compose rm -sfv postgres` y
  `docker volume rm tse-central_postgres-data`) o crearla a mano. Los `persistence.xml` usan `drop-and-create`: **cada deploy borra y recrea
  el esquema**.
- **Fiscalizacionautomatica** no se despliega en WildFly: es un jar standalone (cliente
  remoto). La imagen lo compila y lo deja en `/opt/fiscalizacionautomatica/`, pero no se
  ejecuta (todavia no tiene codigo ni clase `Main`).
- **Logs:** los de WildFly salen por consola (`docker logs wildfly-server`) y por TCP a Logstash
  (ver mas abajo). El arranque espera hasta 60 s a que `logstash` resuelva; si no, arranca igual
  y esos logs no se envian (sin eso el handler fallaba y WildFly quedaba "started with errors").
- **Memoria:** usa el heap por defecto de WildFly (512 MB maximo).
- **Consola de administracion:** no se publica (queda solo dentro del contenedor). Para correr
  comandos: `docker exec wildfly-server /opt/jboss/wildfly/bin/jboss-cli.sh -c "<comando>"`.

### Observabilidad (Elasticsearch + Kibana + Logstash)

Elasticsearch 9.x de un solo nodo (`xpack.security` activado, HTTP sin TLS) y Kibana, para
centralizar logs/metricas del laboratorio. Datos persistidos en el volumen
`tse-central_elasticsearch-data`.

- **Acceso:** Kibana en http://localhost:5601 con usuario `elastic` y `ELASTIC_PASSWORD`. La
  API de Elasticsearch en http://localhost:9200 requiere las mismas credenciales:
  `curl -u elastic:<ELASTIC_PASSWORD> http://localhost:9200`.
- **Arranque:** el servicio de una sola ejecucion `elasticsearch-setup` fija la password de
  `kibana_system` por API cuando Elasticsearch esta sano, y recien entonces arranca Kibana
  (Elasticsearch no permite definirla por variable de entorno). Es normal que figure como
  `Exited (0)`. Primer arranque: ~1 minuto.
- **Desde otros contenedores del compose:** `http://elasticsearch:9200`. Desde los nodos
  (`TSE_NODOS`): `http://host.docker.internal:9200`.
- **Recursos:** ~1.2 GB de RAM cada uno con el heap por defecto (512 MB).
- **Cambiar passwords:** `ELASTIC_PASSWORD` solo se aplica la primera vez (volumen vacio); para
  cambiarla despues, borrar el volumen (se pierden los datos):
  `docker compose rm -sfv elasticsearch kibana` y `docker volume rm tse-central_elasticsearch-data`.
  `KIBANA_PASSWORD` se vuelve a aplicar en cada arranque.
- **Version:** `ELASTIC_VERSION` fija la version de ambos; Kibana debe ser igual o 1 menor.
- **Linux:** si Elasticsearch no arranca por `vm.max_map_count`, correr
  `sudo sysctl -w vm.max_map_count=262144` en el host (Docker Desktop ya lo trae).

#### Envio de logs (WildFly → Logstash → Elasticsearch)

Los backends del componente central corren en un mismo WildFly y mandan sus logs por TCP
(JSON, un registro por linea) a Logstash, que los envia a Elasticsearch.

```
WildFly (socket-handler + json-formatter) ──TCP :5044──▶ logstash ──▶ elasticsearch ◀── kibana
```

- **Lado WildFly:** `wildfly/logging-logstash.cli` agrega el `socket-handler` al logging de
  WildFly (solo configuracion, las apps no cambian ni llevan librerias). El Dockerfile lo aplica
  al construir la imagen; para un WildFly fuera de Docker se aplica una vez con
  `$JBOSS_HOME/bin/jboss-cli.sh --file=wildfly/logging-logstash.cli`. Destino por variables
  `LOGSTASH_HOST` (por defecto `localhost`; el compose define `logstash`) y `LOGSTASH_PORT` (`5044`). Los logs siguen saliendo tambien por consola.
- **Lado Logstash:** `logstash/pipeline/logstash.conf` pasa los campos de WildFly a nombres
  ECS (`log.level`, `log.logger`, `error.stack_trace`, `host.name`...) y calcula
  `service.name` a partir del paquete de la clase: `tse.lab.<proyecto>.…` → `<proyecto>`
  (el groupId base de todos los proyectos). Lo que no es codigo propio (`org.jboss…`,
  `org.hibernate…`) va a `wildfly`. **Los logs de las apps tienen que loguear con clases
  dentro del paquete `tse.lab.<proyecto>`** para que queden bien clasificados.
- **Donde quedan:** un data stream por servicio, `logs-<servicio>-default` (ej.
  `logs-frontofficeciudadanos-default`).
- **Usuario:** Logstash escribe con `tse_logs` (rol `tse_logs_writer`: solo crear documentos
  en `logs-*-*`), creado por `elasticsearch-setup` con `LOGS_PASSWORD`. No usa `elastic`.
- **Si Logstash o Elasticsearch caen:** WildFly no se ve afectado (los registros de ese rato
  no se envian). Logstash sigue encolando en memoria y reintenta; no hay cola persistente.
- **Seguridad:** el puerto `5044` es TCP plano y sin autenticacion (laboratorio). No exponerlo
  fuera de la red de confianza.
- **Si Logstash recien arranca:** el puerto `5044` publicado por Docker acepta conexiones unos
  segundos antes de que el pipeline este listo; WildFly reintenta, pero lo enviado en ese
  intervalo puede perderse.

**Ver los logs:** Kibana → Discover → vista de datos *Logs TSE* (`logs-*`). Si no existe
(volumen nuevo), crearla en Stack Management → Data Views con patron `logs-*` y campo de
tiempo `@timestamp`. Filtros utiles: `service.name : "frontofficeciudadanos"`,
`log.level : "error"`.

**Logs de ejemplo:** para probar sin WildFly, enviar a `localhost:5044` lineas JSON con el
formato de WildFly (`timestamp`, `level`, `loggerName`, `message`, `threadName`, `hostName`,
`stackTrace`...), por ejemplo:

```bash
echo '{"timestamp":"2026-10-05T12:00:00.000+00:00","level":"ERROR","loggerName":"tse.lab.frontofficeciudadanos.service.PadronService","message":"Prueba","threadName":"main","hostName":"wildfly"}' | nc localhost 5044
```

### RabbitMQ (tracking → componente central)

El nodo de tracking y el componente central no se comunican directamente: tracking publica
los eventos en RabbitMQ y el componente central los consume de la cola.

```
tracking ──publica──▶ exchange "tracking" (direct) ──"tracking.event"──▶ cola "tracking.events" ──▶ componente central
```

Comunicacion *one-way* (punto a punto): tracking solo publica y el componente central solo
consume; no hay respuesta ni cola de retorno. Cada mensaje lo procesa un unico consumidor.

- **Donde corre:** RabbitMQ es parte del central (este compose). `tracking` (en `TSE_NODOS`)
  lo alcanza por red en el puerto `5672` publicado en el host; no comparten red de Docker.
  Por eso este compose debe estar arriba para que tracking publique (si no, tracking reintenta).
- **Topologia del lado de RabbitMQ:** el exchange, la cola (durable) y el binding estan en
  `rabbitmq/definitions.json`. Un hook `post_start` del servicio los importa con
  `rabbitmqctl import_definitions` en cada arranque (es idempotente). No se cargan en el boot
  porque en ese caso RabbitMQ no crea el usuario de `RABBITMQ_USER`. Las apps no declaran
  colas: solo publican o consumen.
- **Cambiar la topologia:** la importacion solo **crea lo que falta**; no modifica ni borra
  objetos existentes (y no da error). Para agregar algo alcanza con editar el archivo y correr
  `docker compose up -d --force-recreate rabbitmq`. Para cambiar una propiedad de algo que ya
  existe (tipo de exchange, tipo de cola, `durable`...), primero borrarlo desde el panel
  (Exchanges/Queues → Delete) y despues recrear el contenedor.
- **Healthcheck:** el servicio queda `healthy` recien cuando la cola existe.
- **Usuario:** RabbitMQ crea `RABBITMQ_USER` / `RABBITMQ_PASSWORD` **solo la primera vez**
  (volumen `rabbitmq-data` vacio). Si despues se cambian en el `.env`, hay que borrar el
  volumen para que se vuelva a crear (se pierden los mensajes encolados):
  `docker compose rm -sf rabbitmq` y `docker volume rm tse-central_rabbitmq-data`.
- **Conexion desde el componente central** (WildFly, hoy fuera del compose):
  `amqp://<RABBITMQ_USER>:<RABBITMQ_PASSWORD>@localhost:5672`, consumiendo de la cola
  `tracking.events`. Desde un contenedor de este compose el host es `rabbitmq`.
- **Mensajes:** JSON persistente (`content_type: application/json`, `type: tracking.event`):
  `{ id, gpsId, licensePlate, latitude, longitude, datetime }`. La entrega es *at-least-once*:
  ante un reintento puede llegar un duplicado con el mismo `message_id`, que el consumidor
  debe descartar.

## Requisitos

- JDK 24 y Apache Maven 3.9+ (solo para compilar el backend fuera de Docker)
- Node.js 24 / npm
- Docker + Docker Compose 2.30+ (RabbitMQ, Elasticsearch/Kibana y las 3 apps Next.js; el compose usa `post_start`)
