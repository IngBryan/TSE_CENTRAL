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
├── rabbitmq/                    # Topologia de RabbitMQ (definitions.json)
├── mongodb/init/                # Scripts del primer arranque de MongoDB (crea la coleccion tracking)
├── docker-compose.yml           # Levanta todo: RabbitMQ, PostgreSQL, MongoDB, WildFly y los 3 frontends
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

#PDI (Servicio de Informacion DNIC, corre en TSE_NODOS)
PDI_ORGANISMO_CODIGO=MTOP-CARGA        # mismo valor que en TSE_NODOS/.env
PDI_ORGANISMO_CLAVE=<mismo-que-en-TSE_NODOS>
#PDI_URL=http://host.docker.internal:3005/ws/servicioInformacion   # opcional: donde corre la PDI

#MongoDB (base main_db, coleccion tracking)
MONGO_USER=tse
MONGO_PASSWORD=<un-secreto>           # usuario root; se crea solo en el primer arranque
#MONGO_PORT=27017                      # opcional: puerto en la maquina (por defecto 27017)
```

Las credenciales de los nodos (`balanzas`, `tracking`, `pdi`) van en `TSE_NODOS/.env`.
`RABBITMQ_USER` / `RABBITMQ_PASSWORD` deben ser las mismas en ambos `.env`: el central crea el
usuario y tracking lo usa para publicar. Lo mismo `PDI_ORGANISMO_CODIGO` / `PDI_ORGANISMO_CLAVE`:
la PDI crea el organismo al arrancar y el central lo usa en `Organismo` / `ClaveAcceso1`.
El compose le pasa a WildFly `RABBITMQ_*` (host `rabbitmq`) y `PDI_*`.

```bash
docker compose up --build
```

| Servicio                                     | URL                     |
|-----------------------------------------------|--------------------------|
| `wildfly` (las 3 aplicaciones del backend)    | http://localhost:8080    |
| `postgres`                                    | `localhost:5432` (o `POSTGRES_PORT`) |
| `mongodb`                                     | `mongodb://localhost:27017` (o `MONGO_PORT`) |
| `rabbitmq` (panel web)                        | http://localhost:15672   |
| `rabbitmq` (AMQP)                             | `amqp://localhost:5672`  |
| `frontofficeciudadanos-front`                 | http://localhost:3002    |
| `frontofficefuncionarios-front`               | http://localhost:3003    |
| `backofficeautoridadesadministradores-front`  | http://localhost:3004    |

Los puertos `3000`, `3001` y `3005` los usan `tracking`, `balanzas` y `pdi` (`TSE_NODOS`), asi
que ambos compose pueden correr a la vez.

### Backend (WildFly + PostgreSQL)

Un unico servidor WildFly 41 con las 3 aplicaciones Jakarta EE desplegadas como EAR
(`Frontofficeciudadanos`, `Frontofficefuncionarios`, `Backofficeautoridadesadministradores`),
mas un PostgreSQL con una unica base (`main_db`) compartida por los 3.

```bash
docker compose up -d --build wildfly   # levanta tambien postgres
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
- **Logs:** los de WildFly salen por consola (`docker logs wildfly-server`). Ver
  [Observabilidad](#observabilidad).
- **Memoria:** usa el heap por defecto de WildFly (512 MB maximo).
- **Consola de administracion:** no se publica (queda solo dentro del contenedor). Para correr
  comandos: `docker exec wildfly-server /opt/jboss/wildfly/bin/jboss-cli.sh -c "<comando>"`.

### MongoDB

MongoDB 8.0.0 (`mongo:8.0.0`) con la base `main_db` y la coleccion `tracking`.

```bash
docker compose up -d mongodb
```

- **Usuario:** `MONGO_USER` / `MONGO_PASSWORD` es un usuario root, creado en la base `admin`.
  Por eso al conectarse hay que indicar `authSource=admin`.
- **Coleccion:** `mongodb/init/01-tracking.js` la crea en `main_db`. El servicio queda
  `healthy` recien cuando la coleccion existe.
- **Primer arranque:** el usuario y la coleccion se crean solo con el volumen
  `tse-central_mongodb-data` vacio. Si se cambian las credenciales en el `.env` o se agrega un
  script en `mongodb/init`, hay que borrar el volumen (se pierden los datos):
  `docker compose rm -sfv mongodb` y `docker volume rm tse-central_mongodb-data`.
- **Desde un contenedor de este compose** el host es `mongodb:27017`.

**Conectarse con MongoDB Compass:** *Add new connection* y pegar en *URI* (con los valores
del `.env`):

```
mongodb://<MONGO_USER>:<MONGO_PASSWORD>@localhost:27017/main_db?authSource=admin
```

O en *Advanced Connection Options*: *General* → Host `localhost:27017`; *Authentication* →
*Username/Password* con `MONGO_USER` / `MONGO_PASSWORD` y *Authentication Database* `admin`.

### Observabilidad

- **Logs:** cada contenedor escribe sus logs por consola. En local: `docker logs <contenedor>`
  (por ejemplo `docker logs wildfly-server`). En el cluster: `kubectl -n tse logs <pod>`.
- **Metricas:** en el cluster se usan el Prometheus y el Grafana que trae el entorno de
  Kubernetes (namespace `kubernetes-monitoring`).

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
- Docker + Docker Compose 2.30+ (RabbitMQ, PostgreSQL, WildFly y las 3 apps Next.js; el compose usa `post_start`)
