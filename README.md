# TSE Central

Componente central del laboratorio: backend Jakarta EE (desplegado en un unico WildFly), sus
frontends Next.js, la fiscalizacion automatica y el RabbitMQ al que publican los nodos
perifericos. Los nodos (`tracking`, `balanzas`, `pdi`) viven en el repo hermano `TSE_NODOS`.

## Estructura del repositorio

```
TSE_CENTRAL/
├── componente_central/          # Sistema principal, separado en back y front
│   ├── backend/                 # Apps Jakarta EE (Maven multi-modulo: ejb + web + ear)
│   │   ├── Modelo/                    # entidades JPA compartidas (jar, paquete tse.lab.model)
│   │   ├── Frontofficeciudadanos/
│   │   ├── Frontofficefuncionarios/
│   │   ├── Backofficeautoridadesadministradores/
│   │   └── Fiscalizacionautomatica/   # script standalone (jar, imagen propia), sin ejb/web/ear
│   └── frontend/                # Clientes Next.js que consumen el backend de arriba
│       ├── frontofficeciudadanos/
│       ├── Frontofficefuncionarios/
│       └── backofficeautoridadesadministradores/
│
├── wildfly/                     # Imagen del backend: un WildFly con los 3 EAR (Dockerfile + scripts CLI)
├── rabbitmq/                    # Topologia de RabbitMQ (definitions.json)
├── mongodb/init/                # Scripts del primer arranque de MongoDB (crea las colecciones tracking y balanzas_metadata)
├── docker-compose.yml           # Levanta todo: RabbitMQ, PostgreSQL, MongoDB, WildFly, la fiscalizacion automatica y los 3 frontends
└── .gitignore                   # Reglas a nivel workspace (cada modulo tiene el suyo)
```

### componente_central/backend

Tres proyectos Maven, cada uno con la misma forma:

- `pom.xml` padre (`packaging=pom`), Jakarta EE sobre el BOM de WildFly.
- `ejb/` — packaging `ejb`, capa de servicio/persistencia (`service`, `data`).
- `web/` — packaging `war`, JSF + PrimeFaces + REST (`web/controller`, `web/jsf`, `web/ws`).
- `ear/` — packaging `ear`, empaqueta `ejb` + `web` para desplegar en WildFly.


Todos comparten `groupId` base `tse.lab.<proyecto>`, Java 24 (`maven.compiler.release=24`)
y corren sobre el mismo WildFly.

**Modelo compartido:** las entidades JPA (`Empresa`, `Responsable`, `Chofer`, `Guia`, ...) estan en
`backend/Modelo` (paquete `tse.lab.model`), porque los tres backends usan la misma base `main_db`.
Cada `ejb/pom.xml` depende de `tse.lab.modelo:modelo`, el EAR lo empaqueta en `lib/modelo.jar` y el
`persistence.xml` lo incluye con `<jar-file>lib/modelo.jar</jar-file>`. Cada backend tiene sus propios
DAOs y servicios (sus ABM); lo unico compartido son las entidades.

- El esquema lo genera **solo** Frontofficeciudadanos (`drop-and-create` + `datos-prueba.sql`). Los otros
  dos usan `schema-generation.database.action=none`: si no, se borrarian las tablas entre si en cada deploy.
- Hay que instalar Modelo antes de compilar cualquier backend (y despues de cambiar una entidad).

**Levantar un modulo:**
```bash
mvn -f componente_central/backend/Modelo/pom.xml install
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

#Balanzas (TSE_NODOS): cliente OAuth con el que el poller de fiscalizacion consulta la API
BALANZAS_CLIENT_ID=componente-central  # mismo valor que BALANZAS_OAUTH_CLIENT_ID en TSE_NODOS/.env
BALANZAS_CLIENT_SECRET=<mismo-que-BALANZAS_OAUTH_CLIENT_SECRET-en-TSE_NODOS>
#BALANZAS_URL=http://host.docker.internal:3001   # opcional: donde corre balanzas

#EJB remoto (fiscalizacion -> Frontofficeciudadanos): usuario de aplicacion de WildFly
EJB_USER=fiscalizacion
EJB_PASSWORD=<un-secreto>             # WildFly crea el usuario con estos valores; fiscalizacion se autentica con ellos
#Login con ID Uruguay (gub.uy, testing) - frontofficeciudadanos
OIDC_CLIENT_ID=<client id que dieron los docentes>
OIDC_CLIENT_SECRET=<client secret>
SESSION_SECRET=<string largo y aleatorio>   # cifra la cookie de sesion

#API del backend Frontofficeciudadanos (la consume el front de Next)
BACKEND_API_KEY=<string largo y aleatorio>  # clave compartida front <-> WildFly (header X-Api-Key)

#App movil del chofer (TSE_MOVIL) - frontofficeciudadanos
MOVIL_TOKEN_SECRET=<string largo y aleatorio, 32+ caracteres>  # firma el token (JWT HS256) de la app
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
| `fiscalizacionautomatica` / `fiscalizacionautomatica-poller` | sin puertos (ver [Fiscalizacion automatica](#fiscalizacion-automatica)) |

Los puertos `3000`, `3001` y `3005` los usan `tracking`, `balanzas` y `pdi` (`TSE_NODOS`), asi
que ambos compose pueden correr a la vez.

### Login con ID Uruguay (frontofficeciudadanos)

El frontoffice de ciudadanos usa OpenID Connect (Authorization Code Flow) contra el ambiente de
testing de ID Uruguay (`https://auth-testing.iduruguay.gub.uy/oidc/v1`). Todo el flujo corre en el
servidor de Next (`src/app/auth/*`, `src/lib/auth/*`): el canje del `code` con Basic Auth, la
validacion del `id_token` contra el JWKS (iss, aud, exp, nonce) y el userinfo. El navegador solo
ve una cookie de sesion cifrada.

- **Puerto 80:** las redirect URIs registradas son `http://localhost` y `http://localhost:8080`
  (logout: `/logout`). El 8080 lo usa WildFly, asi que la app tiene que atender en el 80. El compose
  publica `80:3000`; en desarrollo usar `npm run dev:gubuy` (`next dev -p 80`).
- **Credenciales:** en desarrollo van en `componente_central/frontend/frontofficeciudadanos/.env.local`
  (ver `.env.example`); en Docker, en el `.env` de la raiz. Ninguno se commitea.
- **Perfil y PDI (CU-01 pasos 6 a 8, CU-03):** despues del login el front llama a
  `POST /frontofficeciudadanos-web/api/ciudadanos/ingreso-gubuy` (header `X-Api-Key` =
  `BACKEND_API_KEY`, sin ella responde 401) con la cedula, el email y el `sub` de gub.uy. El backend
  busca o crea el perfil (tabla `ciudadanos`, entidad `PerfilCiudadano`); si esta pendiente lo
  valida contra la PDI por SOAP (`ClientePdi`: timeout, reintentos y circuit breaker, configurables
  con `PDI_TIMEOUT_MS`, `PDI_REINTENTOS`, `PDI_CB_UMBRAL`, `PDI_CB_ESPERA_S`). Resultados:
  `VALIDADO` y `NO_DISPONIBLE` (sesion con la validacion pendiente) inician sesion; `NO_EXISTE` y
  `MENOR_DE_EDAD` no. Cada ingreso lleva un `X-Correlation-Id` que aparece en los logs del front
  (`[login gub.uy] correlationId=...`), de WildFly (`evento=ingreso.gubuy`, `evento=pdi.consulta`)
  y de la PDI. Para probarlo hay que levantar tambien la PDI (`TSE_NODOS`, servicio `pdi`).
- **Roles (CU-01 paso 9):** el ingreso devuelve `roles` (`RESPONSABLE` y/o `CHOFER`, con su empresa;
  el de chofer con estado `HABILITADO` o `PENDIENTE_VALIDACION`). El front los guarda en la sesion y
  `/mi-cuenta` muestra la pantalla segun el rol.
- **Alta de chofer (CU-02):** seccion `/empresa/choferes` del responsable. API:
  `GET /api/empresa/choferes` (listado), `POST /api/empresa/choferes/consulta` (pasos 2 a 7: valida
  contra la PDI si hace falta y devuelve solo primer nombre y primer apellido) y
  `POST /api/empresa/choferes` (confirmar, pasos 8 a 10). Quien llama va en el header
  `X-Usuario-Ci`; el backend (`AltaChoferService`) exige que sea responsable validado de una empresa
  habilitada y si no responde 403. **Limitacion:** el backend confia en `X-Usuario-Ci` porque solo
  el servidor de Next tiene la `BACKEND_API_KEY` y saca la cedula de su cookie de sesion cifrada; si
  la API se abre a otros clientes hay que pasar un token firmado. Cada consulta de cedula queda en
  la tabla `consultas_persona` (Ley 18.331: usuario, cedula consultada, fecha y hora).
- **Perfil de la Empresa:** `/empresa` (front) y `GET`/`PUT /api/empresa` (`PerfilEmpresaService`). El
  responsable validado consulta y edita nombre publico, razon social y direccion principal (obligatorios,
  100/100/200 caracteres; 400 con el error por campo). El nro. de empresa se muestra pero no se edita:
  es la clave que referencian choferes, vehiculos, guias y el responsable. El control de acceso es
  `AutorizacionResponsable`, compartido con el alta de choferes.
- **Gestion de Viajes de Carga y CU-07:** `/empresa/guias` (front) y `/api/empresa/guias` (`GuiasService`).
  El responsable de una empresa habilitada ingresa guias (rubro del cliente, tipo de carga, carga en kg, fecha >= hoy,
  origen y destino distintos) y las asigna: `GET /{id}/asignacion` devuelve la guia, los vehiculos y
  solo los choferes `HABILITADO` de la empresa; `POST /{id}/asignacion` con `matricula` e `idChofer`
  la deja `ASIGNADA` (409 si ya lo estaba, 400 si el vehiculo o el chofer no son de la empresa o el
  chofer esta pendiente). `Guia` tiene `@Version` para que dos asignaciones simultaneas no ganen las dos.
- **App movil del chofer (`TSE_MOVIL`):** el servidor de Next hace de BFF; la app nunca habla con
  WildFly ni tiene la `BACKEND_API_KEY`.
  - **Login:** la app abre `/auth/movil/login?redirect=cargauy://auth&code_challenge=<S256>` en un
    Custom Tab. Es el mismo login con gub.uy (misma redirect URI `http://localhost`); la cookie de la
    transaccion marca que el flujo es movil. En `/auth/callback`, si el ingreso es valido y la persona
    es `CHOFER` `HABILITADO`, no se crea la sesion web: se emite un codigo de un solo uso (60 s, en
    memoria del proceso de Next, atado al `code_challenge` por PKCE) y se vuelve a
    `cargauy://auth?code=...` (o `?error=no_chofer|cancelado|...`). La app lo canjea en
    `POST /api/movil/token` (`{ code, codeVerifier }`) por un JWT HS256 firmado con
    `MOVIL_TOKEN_SECRET` (`sub` = cedula, `nombre`, `roles`, vence a las 8 h). El token no viaja en la URL.
  - **API (Next):** `GET /api/movil/viajes` y `POST /api/movil/eventos` validan el `Authorization: Bearer`
    (401 `token_vencido` / `token_invalido`) y llaman al backend con `X-Api-Key`, `X-Usuario-Ci` (la cedula
    del token) y `X-Correlation-Id` (el que mande la app, por ejemplo el `idEvento`, o uno nuevo).
  - **API (WildFly, `MovilResource` + `ViajesChoferService`):** `GET /api/movil/viajes` devuelve las guias
    `ASIGNADA` o `EN_CURSO` del chofer en todas las empresas donde esta `HABILITADO` (lista vacia si no
    tiene; 403 si no es chofer habilitado). `POST /api/movil/eventos` recibe
    `{ idEvento (UUID del movil), idGuia, tipo, fechaHora (ISO-8601 con zona), descripcion?, latitud?, longitud?, foto? (JPEG/PNG base64, max 5 MB), versionGuia? }`
    y lo guarda en `eventos_viaje` (entidad `EventoViaje`, clave = `idEvento`) con un resultado, segun la
    **politica de resolucion de conflictos** (`TSE_MOVIL/docs/adr/0002-resolucion-de-conflictos.md`):
    `APLICADO` (200; `INICIO` `ASIGNADA -> EN_CURSO`, `FIN` `EN_CURSO -> FINALIZADA`, cargas, descargas e
    incidentes en `EN_CURSO`), `SIN_EFECTO` (200; el efecto ya estaba aplicado, p. ej. otro `INICIO`) o
    `CONFLICTO` (409; se guarda pero no cambia la guia: `VIAJE_NO_INICIADO`, `VIAJE_FINALIZADO`,
    `GUIA_NO_ASIGNADA`, `GUIA_REASIGNADA`, `GUIA_CANCELADA`). La respuesta trae la guia actual y
    `guiaModificada: true` si `versionGuia` no coincide con el `@Version` de la guia.
    **Idempotencia:** si el `idEvento` ya existe responde lo mismo que la primera vez (`duplicado: true`)
    sin tocar la guia; la guia se bloquea (`PESSIMISTIC_WRITE`) durante el registro y la clave primaria
    resuelve dos envios simultaneos. Rechazos que no se guardan (el movil los marca `RECHAZADO`):
    400 `datos_invalidos` (con `campos`), 403 `no_autorizado`, 404 `no_encontrada` (guia inexistente o
    de una empresa donde no es chofer) y 409 `ID_EVENTO_EN_USO`. Logs: `evento=movil.viajes`,
    `movil.evento.registrado|duplicado|conflicto|rechazado`.
  - **Historial para el responsable:** `GET /api/empresa/guias/{id}/eventos` devuelve la guia, los eventos
    del chofer (ordenados por hora del dispositivo y de recepcion, con resultado), las modificaciones de la
    empresa y, si la guia esta `ASIGNADA`, los vehiculos y choferes habilitados para reasignar. La foto de
    un evento: `GET /api/empresa/guias/{id}/eventos/{idEvento}/foto`. En la web: `/empresa/guias/{id}`
    (link desde el numero de guia), con la linea de tiempo completa y los conflictos marcados.
  - `/empresa/guias` muestra el estado (`Asignada`, `En curso`, `Finalizada`, `Cancelada`) junto al vehiculo y chofer.
- **Modificacion de guias (frontoffice ciudadanos):** el responsable puede **editar**, **reasignar** o
  **cancelar** una guia desde `/empresa/guias/{id}` (botones con confirmacion y el aviso *"El chofer puede
  estar sin conexion con la version anterior; sus eventos se resolveran segun la politica de
  conflictos"*). Codigo: `GuiasService.editar|reasignar|cancelar`, `GuiasResource`, entidad
  `ModificacionGuia` (tabla `modificaciones_guia`).
  - **Reglas:** solo se modifica una guia **`ASIGNADA`** (el chofer todavia no la inicio). `SIN_ASIGNAR`
    se asigna con CU-07; `EN_CURSO`, `FINALIZADA` y `CANCELADA` no se tocan (409 `GUIA_NO_MODIFICABLE`).
    El control de acceso es `AutorizacionResponsable` (responsable validado de una empresa habilitada; 403).
    - **Editar:** rubro, tipo de carga, kg, fecha (>= hoy) y origen/destino distintos, con las mismas
      validaciones que el alta (400 `datos_invalidos` por campo; 400 `general` si no cambio nada).
    - **Reasignar:** vehiculo de la empresa y chofer `HABILITADO` de la empresa (al menos uno distinto).
    - **Cancelar:** motivo obligatorio (hasta 500 caracteres). La guia pasa a `CANCELADA`.
  - **Endpoints** (con `X-Api-Key` y `X-Usuario-Ci`, como el resto):
    `PUT /api/empresa/guias/{id}` `{ rubroCliente, tipoCarga, pesoCargaKg, fecha, origen, destino, version }`,
    `POST /api/empresa/guias/{id}/reasignacion` `{ matricula, idChofer, version }` y
    `POST /api/empresa/guias/{id}/cancelacion` `{ motivo, version }`. Responden la guia actualizada (con
    su nueva `version`).
  - **Concurrencia:** el body lleva la `version` de la guia que el responsable estaba mirando (`GuiaVista.version`,
    el `@Version` de `Guia`). La guia se bloquea (`PESSIMISTIC_WRITE`, el mismo bloqueo que usan los
    eventos del movil) y, si la version cambio (otra sesion la modifico o el chofer la inicio), responde
    409 `GUIA_DESACTUALIZADA` y la web pide recargar. Cada modificacion incrementa la `version`.
  - **Auditoria:** cada cambio queda en `modificaciones_guia` (tipo, cedula del responsable, fecha, que
    cambio —`destino: Paysandu -> Salto`—, motivo, version anterior y nueva) y se muestra en la linea de
    tiempo de la guia junto a los eventos del chofer. Log `evento=guia.modificada` con `correlationId`.
  - **Interaccion con la app movil** (politica de conflictos, `TSE_MOVIL/docs/adr/0002-resolucion-de-conflictos.md`):
    - **Editar:** los eventos del chofer se siguen aplicando; como el telefono manda la `versionGuia` que
      conocia, la respuesta trae `guiaModificada: true` y los datos nuevos, y la app muestra *"La empresa
      modifico esta guia"*.
    - **Reasignar:** los eventos del chofer anterior quedan `CONFLICTO` / `GUIA_REASIGNADA` (registrados,
      sin cambiar la guia); la guia sale de su app y aparece en la del nuevo chofer al refrescar.
    - **Cancelar:** los eventos posteriores quedan `CONFLICTO` / `GUIA_CANCELADA` y la guia deja de
      venir en `GET /api/movil/viajes`.
  - Tests: `GuiasServiceTest` (un caso por regla y uno integrado: editar -> evento con version vieja ->
    `guiaModificada`; reasignar -> `CONFLICTO`; cancelar -> `CONFLICTO`).
- **Modelo:** `Chofer` es la asociacion persona-empresa (id propio, `(ci, nro_empresa)` unico), asi
  una persona puede ser chofer de varias empresas; las guias referencian esa asociacion.
- **Datos de prueba (solo desarrollo):** `ejb/src/main/resources/META-INF/datos-prueba.sql` carga en
  cada deploy (despues del drop-and-create) la empresa 1 habilitada y su responsable, dos vehiculos,
  y para la app movil a `56087406` y `54074380` como choferes `HABILITADO` de la empresa 1 con una
  guia `ASIGNADA` cada uno (`GV-MOVIL001` y `GV-MOVIL002`). Las otras
  aplicaciones no tienen tablas propias en `main_db`, asi que el script no choca con nada.

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
- **Fiscalizacionautomatica** no se despliega en WildFly: es un jar standalone con su propia
  imagen. Ver [Fiscalizacion automatica](#fiscalizacion-automatica).
- **Logs:** los de WildFly salen por consola (`docker logs wildfly-server`). Ver
  [Observabilidad](#observabilidad).
- **Memoria:** usa el heap por defecto de WildFly (512 MB maximo).
- **Consola de administracion:** no se publica (queda solo dentro del contenedor). Para correr
  comandos: `docker exec wildfly-server /opt/jboss/wildfly/bin/jboss-cli.sh -c "<comando>"`.

### MongoDB

MongoDB 8.0.0 (`mongo:8.0.0`) con la base `main_db` y las colecciones `tracking` (eventos de
tracking) y `balanzas_metadata` (ultima consulta del poller de balanzas).

```bash
docker compose up -d mongodb
```

- **Usuario:** `MONGO_USER` / `MONGO_PASSWORD` es un usuario root, creado en la base `admin`.
  Por eso al conectarse hay que indicar `authSource=admin`.
- **Colecciones:** `mongodb/init/01-tracking.js` y `02-balanzas-metadata.js` las crean en
  `main_db`. El servicio queda `healthy` recien cuando existe `tracking`. Con un volumen que ya
  existia, `balanzas_metadata` la crea el poller en su primera escritura (no hace falta borrar el
  volumen).
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

### Fiscalizacion automatica

`componente_central/backend/Fiscalizacionautomatica` es un jar standalone (`java -jar`), no se
despliega en WildFly. Tiene su propia imagen (`Dockerfile` de la carpeta) y el mismo jar corre
en varios modos, segun el argumento:

| Modo | Servicio del compose | Que hace | Como corre |
|------|----------------------|----------|------------|
| `tracking-consumer` (por defecto) | `fiscalizacionautomatica` (`fiscalizacion-server`) | Consume la cola `tracking.events` y guarda cada evento en MongoDB (`main_db.tracking`) | Siempre arriba; puede escalar (varias replicas consumen la misma cola) |
| `weighings-poller` | `fiscalizacionautomatica-poller` (perfil `cron`) | Consulta la API de balanzas (`TSE_NODOS`) desde la ultima consulta exitosa (`main_db.balanzas_metadata`) hasta ahora | Corre **una vez y termina**: lo lanza un cron |
| `active-trips` | (ninguno, prueba manual) | Pide por EJB remoto los viajes en curso de `SBC1234` y los imprime | Corre una vez y termina; codigo de salida `1` si falla la llamada |

**Consumidor de tracking:**

- Confirmacion manual: el mensaje se confirma recien despues de guardarlo. Duplicado → se
  confirma sin guardar; invalido → se descarta; si falla el guardado (por ejemplo, Mongo
  caido) vuelve a la cola y se reintenta.
- Un evento se identifica por `(licensePlate, timestamp)` (matricula y fecha_hora), con un indice
  unico `licensePlate_timestamp` que el consumidor crea al arrancar: una reentrega choca con el
  indice y no se guarda dos veces. El `_id` lo genera MongoDB y el id del evento en tracking
  queda en `externalId` (no sirve como clave: tracking lo reinicia al reiniciarse). Documento:
  `{ _id, messageId, externalId, gpsId, licensePlate, location (GeoJSON), timestamp }`.
- El `message_id` de RabbitMQ es obligatorio: un mensaje sin el se descarta como invalido (sin
  reencolar). Se guarda en `messageId` para rastrear el evento hasta su mensaje.
- Variables: `RABBITMQ_HOST` (`localhost`), `RABBITMQ_PORT` (`5672`), `RABBITMQ_USER`,
  `RABBITMQ_PASSWORD`, `MONGO_HOST` (`localhost`), `MONGO_PORT` (`27017`), `MONGO_DB`
  (`main_db`), `MONGO_USER`, `MONGO_PASSWORD`, `MONGO_AUTH_DB` (`admin`). Si RabbitMQ o Mongo
  no estan arriba al arrancar, reintenta cada 5 s.

**Poller de balanzas:**

- Cada ejecucion pide `GET /api/scales/{id}/weighings?licensePlate&from&to` por cada balanza
  (fechas en hora de Uruguay, `-03:00`), imprime
  `Fetched N weighings from <url> (until <to>)` y termina. El proceso no guarda estado en
  memoria: la frecuencia la define el cron que lo lanza.
- **Rango consultado, por balanza:** desde la ultima consulta exitosa de esa balanza hasta ahora.
  Se guarda en MongoDB, en la coleccion `balanzas_metadata`, un documento por balanza:
  `{ _id: <id de la balanza>, balanzas_last_timestamp: <Date> }`. Si el documento de una balanza
  no existe (primera consulta), su rango arranca al inicio del dia de hoy en hora de Uruguay.
  Cuando la balanza responde se guarda el `to` de esa ejecucion, asi la proxima arranca donde
  termino esta.
- **Si falla una balanza**, se sigue con las demas: solo esa no actualiza su
  `balanzas_last_timestamp`, y en la proxima ejecucion repite su rango (no se pierden pesajes ni
  se vuelven a consultar las que respondieron).
- Codigo de salida: `0` si consulto todas las balanzas, `1` si fallo alguna o si MongoDB no esta
  disponible (el cron o el CronJob lo registra como ejecucion fallida).
- Cliente con MicroProfile Rest Client (RESTEasy) y OAuth 2.0 client credentials: pide un token
  por ejecucion y, ante un `401`, pide otro y reintenta una vez.
- Variables: `BALANZAS_URL` (`http://localhost:3001`; en el compose
  `http://host.docker.internal:3001`), `BALANZAS_CLIENT_ID`, `BALANZAS_CLIENT_SECRET`, y las de
  MongoDB (`MONGO_HOST`, `MONGO_PORT`, `MONGO_DB`, `MONGO_USER`, `MONGO_PASSWORD`,
  `MONGO_AUTH_DB`, igual que el consumidor).
- **Por ahora:** que consultar esta hardcodeado en `Main` (`SCALE_IDS` y matricula
  `LICENSE_PLATE = "SBC1234"`; se va a definir que balanzas y vehiculos consultar) y los pesajes
  no se guardan.

**Viajes en curso (EJB remoto de Frontofficeciudadanos):**

Para saber si un vehiculo tiene guia, fiscalizacion llama por EJB remoto a `ViajesEnCursoService`
(Frontofficeciudadanos, paquete `service.gestiondeguiasyviajes`), que devuelve las guias del
vehiculo en estado `ASIGNADA` o `EN_CURSO` (`List<DatosViaje>`). El cliente es
`ruleengine/trips/ActiveTripsClient`.

- **Contrato:** fiscalizacion no depende del ejb entero sino de `frontofficeciudadanos-ejb-client.jar`
  (lo genera el `maven-ejb-plugin` con `generateClient`), que trae solo `ViajesEnCurso`,
  `ViajesEnCursoRemote` y `DatosViaje`. Por eso, antes de compilar fiscalizacion hay que instalar
  Modelo y Frontofficeciudadanos en el `.m2` (el `Dockerfile` y el workflow de CI ya lo hacen):

  ```bash
  mvn -f componente_central/backend/Modelo/pom.xml install -Dmaven.test.skip=true
  mvn -f componente_central/backend/Frontofficeciudadanos/pom.xml install -Dmaven.test.skip=true
  ```

  Si se agrega otra interfaz remota o DTO, hay que sumarlo a `clientIncludes` en
  `Frontofficeciudadanos/ejb/pom.xml`.
- **Conexion:** por el mismo puerto HTTP de WildFly (`remote+http://<host>:8080`); WildFly hace el
  upgrade a su protocolo de EJB, no hay que abrir otro puerto.
- **Autenticacion:** WildFly no acepta llamadas remotas anonimas: valida usuario y contrasenia
  contra su `ApplicationRealm` (`application-users.properties`). Un mismo par de valores
  (`EJB_USER` / `EJB_PASSWORD`, en el `.env` o en el `Secret` de Kubernetes) llega a los dos
  contenedores:

  ```
  .env: EJB_USER / EJB_PASSWORD
     ├─► wildfly                  → add-user.sh crea el usuario al arrancar
     └─► fiscalizacionautomatica  → se autentica con ese usuario
  ```

  El usuario lo crea `wildfly/start.sh` (el `CMD` de la imagen de WildFly) al **arrancar** el
  contenedor y no al construir la imagen, para que la contrasenia no quede guardada en la imagen
  (mismo criterio que `DB_USER` / `DB_PASSWORD`). Si `EJB_USER` / `EJB_PASSWORD` no estan
  definidas, WildFly arranca igual, sin el usuario:

  ```sh
  /opt/jboss/wildfly/bin/add-user.sh -a -u "$EJB_USER" -p "$EJB_PASSWORD" --silent
  exec /opt/jboss/wildfly/bin/standalone.sh -b 0.0.0.0
  ```

- Variables de fiscalizacion: `WILDFLY_URL` (`remote+http://localhost:8080`; en el compose
  `remote+http://wildfly:8080`), `EJB_USER`, `EJB_PASSWORD`.
- Errores: si WildFly esta caido, las credenciales son incorrectas o el EAR no esta desplegado, la
  llamada lanza `EJBException`, que el cliente convierte en `IOException`.
- En Kubernetes los valores salen de las claves `ejb-user` / `ejb-password` de
  `tse-runtime-secrets` (ver `k8s/README.md`).

El jar se arma con `maven-shade-plugin` (no `maven-assembly-plugin`) porque combina los archivos
`META-INF/services` con los que RESTEasy registra sus providers.

#### Probar en dev

**Consumidor de tracking** (con RabbitMQ y MongoDB arriba):

```bash
docker compose up -d --build fiscalizacionautomatica
docker logs fiscalizacion-server            # "Consuming tracking.events from rabbitmq:5672, ..."
```

Para mandarle un evento: prender el nodo `tracking` (`TSE_NODOS`) o publicar a mano desde el panel
de RabbitMQ (Exchanges → `tracking` → *Publish message*, routing key `tracking.event`, property
`message_id` con cualquier valor y el JSON del evento como payload). El documento aparece en
`main_db.tracking` (Compass).

**Poller de balanzas** (con `balanzas` arriba: `docker compose up -d balanzas` en `TSE_NODOS`, y
`BALANZAS_CLIENT_ID` / `BALANZAS_CLIENT_SECRET` en el `.env`; levanta MongoDB si no esta arriba):

```bash
docker compose run --rm --build fiscalizacionautomatica-poller   # una ejecucion; "Fetched N weighings from ... (until <to>)"
echo $?                                                         # 0 = ok, 1 = fallo alguna balanza o MongoDB
```

La ultima consulta de cada balanza queda en `main_db.balanzas_metadata` (Compass). Para volver a
consultar una balanza desde el inicio del dia, borrar su documento (por ejemplo `{ _id: 4 }`).

Para que se ejecute solo cada 5 minutos en la maquina de desarrollo (opcional):

```bash
# Linux / macOS: agregar con "crontab -e"
*/5 * * * * docker compose -f /ruta/a/TSE_CENTRAL/docker-compose.yml run --rm fiscalizacionautomatica-poller >> /tmp/weighings-poller.log 2>&1
```

```powershell
# Windows (Programador de tareas). Para quitarlo: schtasks /Delete /TN tse-weighings-poller /F
schtasks /Create /SC MINUTE /MO 5 /TN tse-weighings-poller /TR "docker compose -f C:\ruta\a\TSE_CENTRAL\docker-compose.yml run --rm fiscalizacionautomatica-poller"
```

**Viajes en curso por EJB remoto** (con `EJB_USER` / `EJB_PASSWORD` en el `.env`; WildFly tiene
que haber arrancado con esas variables para crear el usuario):

```bash
docker compose up -d --build wildfly
docker logs wildfly-server | grep "Usuario de aplicacion"                 # el usuario se creo
docker compose run --rm --build fiscalizacionautomatica active-trips     # "N active trips for SBC1234 from remote+http://wildfly:8080"
```

**Fuera de Docker** (con las variables de cada modo definidas, y Modelo y Frontofficeciudadanos
instalados en el `.m2`, ver *Viajes en curso*):

```bash
cd componente_central/backend/Fiscalizacionautomatica
mvn clean package
java -jar target/fiscalizacionautomatica.jar                     # consumidor de tracking
java -jar target/fiscalizacionautomatica.jar weighings-poller    # poller de balanzas (una ejecucion)
java -jar target/fiscalizacionautomatica.jar active-trips        # viajes en curso de SBC1234 (EJB remoto)
```

Si el contenedor `fiscalizacion-server` esta arriba, el consumidor local y el del contenedor se
reparten los mensajes de la cola: para debuggear, pararlo antes
(`docker compose stop fiscalizacionautomatica`).

#### Produccion

Los modos usan la misma imagen, que publica en ghcr.io el workflow
`docker-fiscalizacionautomatica.yml`. Las credenciales van en un `Secret` de Kubernetes, no en el
manifiesto. Para el EJB remoto, el `Deployment` necesita ademas `WILDFLY_URL`
(`remote+http://wildfly:8080`) y `EJB_USER` / `EJB_PASSWORD` del mismo `Secret` que usa WildFly.

| | Consumidor de tracking | Poller de balanzas |
|---|---|---|
| Recurso | `Deployment` (escala con el largo de la cola, p. ej. con KEDA) | `CronJob` (corre una vez por ejecucion) |
| Argumentos | ninguno | `["weighings-poller"]` |
| Variables | `RABBITMQ_HOST`, `RABBITMQ_PORT`, `RABBITMQ_USER`, `RABBITMQ_PASSWORD`, `MONGO_HOST`, `MONGO_PORT`, `MONGO_DB`, `MONGO_USER`, `MONGO_PASSWORD`, `MONGO_AUTH_DB` (base donde esta definido el usuario de Mongo) | `BALANZAS_URL` (URL de balanzas en produccion), `BALANZAS_CLIENT_ID`, `BALANZAS_CLIENT_SECRET` (cliente OAuth dado de alta en balanzas) y las mismas `MONGO_*` del consumidor (guarda la ultima consulta en `balanzas_metadata`) |

Puntos del `CronJob` del poller:

```yaml
spec:
  schedule: "*/5 * * * *"          # frecuencia de consulta
  concurrencyPolicy: Forbid        # nunca dos ejecuciones a la vez
  jobTemplate:
    spec:
      backoffLimit: 0              # si falla, se reintenta en la proxima ejecucion programada
      template:
        spec:
          restartPolicy: Never
          imagePullSecrets:
            - name: ghcr-credentials
          containers:
            - name: poller
              image: ghcr.io/ingbryan/tse_central/fiscalizacionautomatica:<tag>
              args: ["weighings-poller"]
              envFrom:
                - secretRef:
                    name: fiscalizacion-balanzas   # BALANZAS_URL, BALANZAS_CLIENT_ID, BALANZAS_CLIENT_SECRET
              env:                                 # MongoDB: igual que en el Deployment del consumidor
                - name: MONGO_HOST
                  value: mongodb
                - name: MONGO_USER
                  valueFrom:
                    secretKeyRef:
                      name: tse-runtime-secrets
                      key: mongo-user
                - name: MONGO_PASSWORD
                  valueFrom:
                    secretKeyRef:
                      name: tse-runtime-secrets
                      key: mongo-password
```

Un codigo de salida `1` deja el `Job` como fallido (`kubectl -n tse get jobs`).

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
- **Quien consume:** el servicio `fiscalizacionautomatica` (ver
  [Fiscalizacion automatica](#fiscalizacion-automatica)). Desde la maquina:
  `amqp://<RABBITMQ_USER>:<RABBITMQ_PASSWORD>@localhost:5672`; desde un contenedor de este
  compose el host es `rabbitmq`.
- **Mensajes:** JSON persistente (`content_type: application/json`, `type: tracking.event`):
  `{ id, gpsId, licensePlate, latitude, longitude, datetime }`. La entrega es *at-least-once*:
  ante un reintento puede llegar un duplicado, que el consumidor descarta porque tiene la misma
  matricula y fecha (`licensePlate`, `datetime`).

## Requisitos

- JDK 24 y Apache Maven 3.9+ (solo para compilar el backend fuera de Docker)
- Node.js 24 / npm
- Docker + Docker Compose 2.30+ (RabbitMQ, PostgreSQL, MongoDB, WildFly, la fiscalizacion automatica y las 3 apps Next.js; el compose usa `post_start`)
