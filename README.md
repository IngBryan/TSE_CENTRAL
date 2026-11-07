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
├── load-tests/tracking-generator/ # Generador de carga de eventos de tracking (prueba de performance)
├── load-tests/frontoffice-k6/   # Prueba de carga de las consultas del frontoffice con k6 (p95 < 500 ms)
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
- `AdaptadorPdi` es el cliente SOAP de la PDI (DNIC) compartido por Frontofficeciudadanos y
  Frontofficefuncionarios (timeouts, reintentos y circuit breaker). Tambien hay que instalarlo antes de
  compilarlos. Lo que cada uno hace con el resultado (estados del ciudadano, sesion del funcionario) queda
  en cada proyecto.

**Levantar un modulo:**
```bash
mvn -f componente_central/backend/Modelo/pom.xml install
mvn -f componente_central/backend/AdaptadorPdi/pom.xml install
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
CIUDADANOS_API_KEY=<string largo y aleatorio>  # clave compartida front <-> WildFly (header X-Api-Key)

#Frontofficefuncionarios: su propia API key y su propio secreto de sesion (distintos de los de ciudadanos)
FUNCIONARIOS_API_KEY=<string largo y aleatorio>
FUNCIONARIOS_SESSION_SECRET=<string largo y aleatorio>

#Infracciones y descargos - WildFly
#PLAZO_DESCARGO_DIAS=10                 # opcional: dias corridos desde la notificacion para presentar el descargo
#VENCIMIENTO_DESCARGO_ACTIVO=true       # opcional: false apaga el pasaje automatico a EN_DESCARGO al vencer el plazo

#Portal publico - WildFly
#PORTAL_UMBRAL_EMPRESAS=3               # opcional: minimo de empresas distintas por numero publicado (nunca menos de 3)

#App movil del chofer (TSE_MOVIL) - WildFly (usa tambien OIDC_CLIENT_ID / OIDC_CLIENT_SECRET)
MOVIL_TOKEN_SECRET=<string largo y aleatorio, 32+ caracteres>  # firma el token (JWT HS256) de la app
BACKOFFICE_JWT_SECRET=<otro string aleatorio, 32+ caracteres>  # firma el token del backoffice (sin el, su login responde 503)
#OIDC_REDIRECT_URIS_MOVIL=uy.carga.movil:/oauth2redirect,http://localhost:8080  # redirect URIs de la app
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
| `wildfly` (las 3 aplicaciones del backend)    | http://localhost:8081    |
| `postgres`                                    | `localhost:5432` (o `POSTGRES_PORT`) |
| `mongodb`                                     | `mongodb://localhost:27017` (o `MONGO_PORT`) |
| `rabbitmq` (panel web)                        | http://localhost:15672   |
| `rabbitmq` (AMQP)                             | `amqp://localhost:5672`  |
| `frontofficeciudadanos-front`                 | http://localhost:3002    |
| `frontofficefuncionarios-front`               | http://localhost:8080/funcionarios |
| `backofficeautoridadesadministradores-front`  | http://localhost:3004/backoffice |
| `fiscalizacionautomatica` / `fiscalizacionautomatica-poller` | sin puertos (ver [Fiscalizacion automatica](#fiscalizacion-automatica)) |

Los puertos `3000`, `3001` y `3005` los usan `tracking`, `balanzas` y `pdi` (`TSE_NODOS`), asi
que ambos compose pueden correr a la vez.

### Login con ID Uruguay (frontofficeciudadanos)

El frontoffice de ciudadanos usa OpenID Connect (Authorization Code Flow) contra el ambiente de
testing de ID Uruguay (`https://auth-testing.iduruguay.gub.uy/oidc/v1`). Todo el flujo corre en el
servidor de Next (`src/app/auth/*`, `src/lib/auth/*`): el canje del `code` con Basic Auth, la
validacion del `id_token` contra el JWKS (iss, aud, exp, nonce) y el userinfo. El navegador solo
ve una cookie de sesion cifrada.

- **Ingreso de testing (sin gub.uy):** mientras ID Uruguay no registre la redirect URI del ambiente de
  Kubernetes (`https://env-6537388.web.elasticloud.uy/ciudadanos`), la portada muestra un formulario
  *"Modo testing"* para ingresar con una cedula. El backend hace el mismo ingreso que con gub.uy
  (`IngresoService.ingresarParaTesting`: perfil, validacion contra la PDI y roles), pero no toca el `sub`
  ni el email de la cuenta gub.uy. Hacen falta los dos interruptores: `LOGIN_TESTING=true` en el front
  (si no, `POST /auth/testing` da 404) e `INGRESO_TESTING=true` en WildFly (si no,
  `POST /api/ciudadanos/ingreso-testing` da 404). En Kubernetes estan prendidos en los configmaps del
  front y de wildfly; en el compose se prenden con `CIUDADANOS_LOGIN_TESTING=true` en el `.env`. Cada
  ingreso queda en el log (`evento=ingreso.testing`, `[login testing]`). Apagarlo cuando ande gub.uy.
- **Puerto 80:** las redirect URIs registradas para desarrollo son `http://localhost` (ciudadanos) y
  `http://localhost:8080` (funcionarios)
  (logout: `/logout`). Ciudadanos tiene que atender en el 80 y funcionarios en el 8080, por eso WildFly
  se publica en el 8081. El compose
  publica `80:3000`; en desarrollo usar `npm run dev:gubuy` (`next dev -p 80`).
- **Credenciales:** en desarrollo van en `componente_central/frontend/frontofficeciudadanos/.env.local`
  (ver `.env.example`); en Docker, en el `.env` de la raiz. Ninguno se commitea.
- **Perfil y PDI (CU-01 pasos 6 a 8, CU-03):** despues del login el front llama a
  `POST /frontofficeciudadanos-web/api/ciudadanos/ingreso-gubuy` (header `X-Api-Key` =
  `CIUDADANOS_API_KEY`, sin ella responde 401) con la cedula, el email y el `sub` de gub.uy. El backend
  busca o crea el perfil (tabla `ciudadanos`, entidad `PerfilCiudadano`); si esta pendiente lo
  valida contra la PDI por SOAP (`ClientePdi`, ver *Resiliencia con la PDI*). Resultados:
  `VALIDADO` y `NO_DISPONIBLE` (sesion con la validacion pendiente) inician sesion; `NO_EXISTE` y
  `MENOR_DE_EDAD` no. Cada ingreso lleva un `X-Correlation-Id` que aparece en los logs del front
  (`[login gub.uy] correlationId=...`), de WildFly (`evento=ingreso.gubuy`, `evento=pdi.consulta`)
  y de la PDI. Para probarlo hay que levantar tambien la PDI (`TSE_NODOS`, servicio `pdi`).
- **Resiliencia con la PDI** (variables del contenedor de WildFly; entre parentesis, el valor por defecto):
  - *Timeout:* `PDI_TIMEOUT_MS` (5000) por intento. Con la PDI en modo `LENTO` (10 s) cada intento
    vence; para ver la respuesta lenta llegar, subirlo por encima de la latencia.
  - *Reintentos con espera exponencial:* `PDI_REINTENTOS` (2, o sea hasta 3 intentos). Antes del
    reintento n se espera `PDI_REINTENTO_BASE_MS` (500) × 2^(n-1), con tope `PDI_REINTENTO_MAX_MS`
    (4000), y de eso entre la mitad y el total al azar (jitter).
  - *Circuit breaker:* tras `PDI_CB_UMBRAL` (3) consultas fallidas seguidas no se llama a la PDI
    durante `PDI_CB_ESPERA_S` (30) segundos: se responde `NO_DISPONIBLE` al instante. Si la consulta de
    prueba vuelve a fallar, se abre por el doble (30 s, 60 s, 120 s...) hasta `PDI_CB_ESPERA_MAX_S` (300).
  - *Comportamiento degradado:* el ingreso sigue andando con el perfil `PENDIENTE_VALIDACION`, y el
    alta de chofer queda pendiente de validacion.
  - *Reenvio de pendientes (CU-04, `RevalidacionPdiJob`):* cada `PDI_REVALIDACION_S` (30) segundos se
    mandan a la PDI los perfiles pendientes. Mientras siga caida, la proxima revision se aleja de forma
    exponencial (60 s, 120 s, 240 s...) hasta `PDI_REVALIDACION_MAX_S` (600). Cuando responde se
    validan todos, edad incluida: quedan `VALIDADO` o `NO_HABILITADO`, y los choferes que esperaban
    quedan habilitados. Despues vuelve a 30 s. En el log: `evento=pdi.revalidacion`.
    `PDI_REVALIDACION_S=0` lo apaga.
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
- **App movil del chofer (`TSE_MOVIL`):** la app habla directo con gub.uy y con WildFly por REST; no
  pasa por el front de Next ni tiene la `BACKEND_API_KEY` (ver `TSE_MOVIL/docs/adr/0003-login-movil-directo-gubuy.md`).
  - **Login (`MovilAuthResource` + `LoginMovilService`):** la app abre el `authorize` de ID Uruguay en
    un Custom Tab (PKCE S256, `state`, `nonce`) y recibe el `code` en su redirect URI
    (`uy.carga.movil:/oauth2redirect`, o el loopback `http://localhost:8080` mientras no este
    registrada). Lo manda a `POST /api/movil/auth/token` (`{ code, codeVerifier, nonce, redirectUri }`).
    WildFly canjea el `code` con el `client_secret` (`ClienteGubUy`), valida el `id_token`
    (`VerificadorIdToken`: firma contra el JWKS, iss, aud, exp y el `nonce` de la app), llama a userinfo,
    aplica el mismo ingreso que la web (`IngresoService`: perfil, PDI, roles), exige `CHOFER`
    `HABILITADO` y devuelve un JWT HS256 firmado con `MOVIL_TOKEN_SECRET` (`TokenMovil`: `sub` = cedula,
    `nombre`, 8 h). Errores: `{ "error": "token_invalido" | "no_chofer" | "no_existe" | "menor_de_edad" |
    "idp_no_disponible" | ... }` con 400/403/503. Sin `OIDC_CLIENT_SECRET` o `MOVIL_TOKEN_SECRET` responde
    503 `ingreso_no_disponible`.
  - **Autorizacion (`MovilTokenFilter`):** el resto de `/api/movil/*` exige `Authorization: Bearer`
    (401 `token_vencido` / `token_invalido`); la cedula sale del token, no de un header del cliente.
    `/api/movil/*` no pide `X-Api-Key`.
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
    `PUT /api/empresa/guias/{id}` `{ rubroCliente, tipoCarga, pesoCargaKg, fecha, origenId, destinoId, version }`,
    `POST /api/empresa/guias/{id}/reasignacion` `{ matricula, idChofer, version }` y
    `POST /api/empresa/guias/{id}/cancelacion` `{ motivo, version }`. Responden la guia actualizada (con
    su nueva `version`). `GET /api/empresa/guias/rubros` y `GET /api/empresa/guias/tipos-carga` devuelven los
    catalogos con los que se validan `rubroCliente` y `tipoCarga`: se aceptan sin distinguir mayusculas ni tildes y
    se guardan con el nombre del catalogo. Al editar, una guia anterior a los catalogos puede conservar su valor.
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
- **ABM de Vehiculos (frontoffice ciudadanos):** el responsable da de alta, consulta, modifica y da de
  baja los vehiculos de su empresa en `/empresa/vehiculos` (boton *Vehiculos* en Mi cuenta), y registra
  el Permiso Nacional de Circulacion y las ITV desde `/empresa/vehiculos/{matricula}`. Codigo:
  `GestionVehiculosService` (paquete `gestionvehiculos`), `VehiculosResource`, `PermisoCirculacionDAO`,
  `ITVDAO`.
  - **Reglas:** control de acceso con `AutorizacionResponsable` (responsable validado de una empresa
    habilitada; 403). Matricula obligatoria, **normalizada** (mayusculas, sin espacios ni guiones:
    `sba 1234` -> `SBA1234`, `Vehiculo.normalizarMatricula`, para cruzarla con balanzas y tracking), de 4
    a 10 letras o numeros y unica en todo el sistema (si es de otra empresa no se dice cual); no se
    modifica. Marca y modelo obligatorios (hasta 50), peso del vehiculo 1..60.000 kg y capacidad de carga
    1..100.000 kg. Un vehiculo de otra empresa responde 404, igual que uno inexistente.
  - **Permiso:** uno por vehiculo (`nro` unico, emision < vencimiento); guardar otro numero lo reemplaza.
    **ITV:** se agregan y se conserva el historial (no dos con el mismo vencimiento); la vigente es la de
    vencimiento mas lejano. **Habilitado** = activo, permiso vigente hoy (emision <= hoy <= vencimiento) e ITV
    sin vencer; se muestra en el listado y en el detalle.
  - **Baja logica** (`Vehiculo.activo`): el vehiculo sigue en el listado como *Dado de baja*, no se ofrece
    ni se acepta en guias nuevas o reasignaciones, y ya no se modifica (409 `VEHICULO_DADO_DE_BAJA`). No
    se puede dar de baja con guias `ASIGNADA` o `EN_CURSO` (409 `VEHICULO_CON_VIAJES`). Guias, permiso e
    ITV lo referencian y la fiscalizacion necesita su historia, por eso no se borra.
  - **Endpoints** (con `X-Api-Key` y `X-Usuario-Ci`): `GET|POST /api/empresa/vehiculos`
    (`{ matricula, marca, modelo, peso, capacidadCargaKg }`), `GET|PUT /api/empresa/vehiculos/{matricula}`
    (`{ marca, modelo, peso, capacidadCargaKg }`), `PUT .../{matricula}/permiso`
    (`{ nro, fechaEmision, fechaVencimiento }`, fechas `yyyy-MM-dd`), `POST .../{matricula}/itv`
    (`{ fechaVencimiento }`) y `POST .../{matricula}/baja`. Logs `evento=vehiculo.alta|modificado|permiso|itv|baja`.
  - Tests: `GestionVehiculosServiceTest` (un caso por regla, contra H2).
- **Modelo:** `Chofer` es la asociacion persona-empresa (id propio, `(ci, nro_empresa)` unico), asi
  una persona puede ser chofer de varias empresas; las guias referencian esa asociacion.
- **Datos de prueba (solo desarrollo):** `ejb/src/main/resources/META-INF/datos-prueba.sql` carga en
  cada deploy (despues del drop-and-create) la empresa 1 habilitada, su responsable (`54074380`) y dos
  vehiculos: `SBA1234` habilitado (permiso `PNC-0001` e ITV vigentes) y `SAB5678` sin permiso ni ITV.
  No precarga choferes ni guias: se crean desde el frontoffice. Las otras
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
  http://localhost:8081/frontofficeciudadanos-web/. Hoy responden `403` porque todavia no hay
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
| `detect-situations [matricula]` | (ninguno, prueba manual) | Evalua un evento de ese vehiculo (por defecto `SBC1234`) en este momento: imprime lo que sabe el central del vehiculo (EJB remotos) y las situaciones que aplican | Corre una vez y termina; codigo de salida `1` si falla la llamada a WildFly |

**Consumidor de tracking:**

- Confirmacion manual: el mensaje se confirma recien despues de guardarlo. Duplicado → se
  confirma sin guardar; invalido → se descarta; si falla el guardado (por ejemplo, Mongo
  caido) vuelve a la cola y se reintenta.
- Un evento se identifica por `(licensePlate, timestamp)` (matricula y fecha_hora), con un indice
  unico `licensePlate_timestamp` que el consumidor crea al arrancar: una reentrega choca con el
  indice y no se guarda dos veces. El `_id` lo genera MongoDB y el id del evento en tracking
  queda en `externalId` (no sirve como clave: tracking lo reinicia al reiniciarse). Documento:
  `{ _id, messageId, externalId, gpsId, licensePlate, location (GeoJSON), timestamp, receivedAt,
  detected, detectedAt }`.
- **Deteccion asincronica:** el evento se guarda con `detected: false` y se pasa a la deteccion,
  que corre en otros hilos (sin demorar la recepcion). Si aplica alguna situacion, el evento se
  asigna a un caso de fiscalizacion (ver *Casos de fiscalizacion*); las situaciones no se guardan
  en el evento. Al terminar se marca `detected: true` y `detectedAt`.
  - Los eventos de un vehiculo van siempre al mismo hilo (4 hilos, elegidos por la matricula): se
    evaluan en orden y nunca dos a la vez dentro del pod.
  - Si un evento no se pudo evaluar (WildFly caido, cola llena, el pod se reinicio), queda en
    `detected: false`. Cada 60 s (y al arrancar) se vuelven a encolar los que llevan mas de 1 minuto
    pendientes (indice parcial `pending_receivedAt`).
  - Al apagar, deja de consumir y espera hasta 10 s a que terminen las detecciones encoladas; las
    que no terminan quedan pendientes.
- El `message_id` de RabbitMQ es obligatorio: un mensaje sin el se descarta como invalido (sin
  reencolar). Se guarda en `messageId` para rastrear el evento hasta su mensaje.
- **GPS dados de alta:** solo se aceptan eventos de GPS dados de alta como `NodoTracking` activo
  (su `gpsId` es el `idExterno` del nodo). Los de un GPS desconocido o inactivo se descartan sin
  guardar y sin reencolar. La lista de GPS activos se pide al backoffice por EJB remoto
  (`NodosActivosRemote`, ver *Nodos activos*) y se guarda en memoria para no llamar a WildFly por
  cada mensaje: se refresca cada 60 s y, si un refresco falla, se sigue con la ultima lista.
- **Ultima comunicacion exitosa del GPS:** cada evento guardado registra en memoria la hora de
  recepcion para su GPS, y cada 60 s el lote se manda al backoffice
  (`NodosActivosRemote.registrarComunicaciones`), que actualiza `ultima_comunicacion_exitosa` del
  `NodoTracking` (solo la avanza, nunca la retrocede: varios pods mandan lotes). Si el envio falla,
  el lote se manda con el siguiente; al apagarse, el pod manda lo que le quedaba.
- Variables: `RABBITMQ_HOST` (`localhost`), `RABBITMQ_PORT` (`5672`), `RABBITMQ_USER`,
  `RABBITMQ_PASSWORD`, `MONGO_HOST` (`localhost`), `MONGO_PORT` (`27017`), `MONGO_DB`
  (`main_db`), `MONGO_USER`, `MONGO_PASSWORD`, `MONGO_AUTH_DB` (`admin`), `WILDFLY_URL`,
  `EJB_USER`, `EJB_PASSWORD`. Si Mongo, WildFly (la lista de GPS) o RabbitMQ no estan arriba al
  arrancar, reintenta cada 5 s; no consume hasta tener la lista de GPS.

**Poller de balanzas:**

- **Balanzas a consultar:** al empezar cada ejecucion le pide al backoffice, por EJB remoto
  (`NodosActivosRemote.listarNodos()`, ver *Nodos activos* abajo), los nodos perifericos activos y
  se queda con los de tipo `BALANZA`. A la API de balanzas le pasa el `idExterno` de cada
  `NodoBalanza` (el id de la balanza en ese nodo). Si no hay balanzas activas, termina sin
  consultar (codigo `0`).
- Despues pide `GET /api/scales/{id}/weighings?from&to` por cada balanza activa, **sin filtrar por
  matricula**: trae los pesajes de todos los vehiculos del rango (fechas en hora de Uruguay, `-03:00`)
  y **los guarda todos en PostgreSQL** (`eventos_pesajes`), aunque el vehiculo no este registrado en
  el central (en ese caso no genera ningun caso),
  pendientes de deteccion (`detectado = false`), por EJB remoto (`PesajesRemote.guardarPesajes`,
  Frontofficeciudadanos, `service.gestiondeguiasyviajes`). Los que ya estaban guardados (misma
  matricula y fecha y hora) se ignoran, asi repetir un rango no duplica nada.
- **Deteccion:** al final de cada ejecucion evalua todos los pesajes pendientes, tambien los que
  quedaron de ejecuciones anteriores (`PendingWeighingsDetection`), y marca cada uno como
  detectado. Si aplica alguna situacion, el pesaje se asigna a un caso (ver *Casos de
  fiscalizacion*).
  No usa hilos: hay un solo poller y cuando detecta ya termino de recibir. Un pesaje que no se pudo
  evaluar (WildFly caido) queda pendiente para la proxima ejecucion.
- Imprime `Fetched N weighings (M new) of scales [..] from <url> (until <to>)` y termina. El proceso
  no guarda estado en memoria: la frecuencia la define el cron que lo lanza.
- **Rango consultado, por balanza:** desde la ultima consulta exitosa de esa balanza hasta ahora.
  Se guarda en MongoDB, en la coleccion `balanzas_metadata`, un documento por balanza:
  `{ _id: <id de la balanza>, balanzas_last_timestamp: <Date> }`. Si el documento de una balanza
  no existe (primera consulta), su rango arranca al inicio del dia de hoy en hora de Uruguay.
  Cuando los pesajes de la balanza quedan guardados se guarda el `to` de esa ejecucion, asi la
  proxima arranca donde termino esta (la deteccion no hace falta para avanzar: los pesajes ya
  quedaron pendientes).
- **Si falla una balanza** (la API o el guardado), se sigue con las demas: solo esa no actualiza
  su `balanzas_last_timestamp`, y en la proxima ejecucion repite su rango (no se pierden pesajes ni
  se vuelven a consultar las que respondieron).
- **Ultima comunicacion exitosa de la balanza:** las balanzas que respondieron y cuyos pesajes se
  guardaron quedan con `ultima_comunicacion_exitosa = to` en su `NodoBalanza`, en un solo llamado
  al final del poll (`NodosActivosRemote.registrarComunicaciones`). Si falla, solo se loguea: no se
  pierde ningun pesaje y la proxima ejecucion la vuelve a registrar.
- Codigo de salida: `0` si consulto y guardo todas las balanzas y evaluo todos los pendientes, `1`
  si fallo alguna balanza o algun pesaje quedo sin evaluar, si no pudo pedir las balanzas activas
  a WildFly o si MongoDB no esta disponible (el cron o el CronJob lo registra como ejecucion
  fallida).
- Cliente con MicroProfile Rest Client (RESTEasy) y OAuth 2.0 client credentials: pide un token
  por ejecucion y, ante un `401`, pide otro y reintenta una vez.
- URL de balanzas y client id: con `ENVIRONMENT=dev` (por defecto, y en el compose) salen de
  `BALANZAS_URL` (`http://localhost:3001`; en el compose `http://host.docker.internal:3001`) y
  `BALANZAS_CLIENT_ID`; con cualquier otro valor, del archivo JSON `CONFIG_FILE` (por defecto
  `/app/config/app.json`, claves `balanzasUrl` y `balanzasClientId`).
- Variables: `BALANZAS_CLIENT_SECRET` (siempre variable de entorno), y las de
  MongoDB (`MONGO_HOST`, `MONGO_PORT`, `MONGO_DB`, `MONGO_USER`, `MONGO_PASSWORD`,
  `MONGO_AUTH_DB`, igual que el consumidor) y del EJB remoto (`WILDFLY_URL`, `EJB_USER`,
  `EJB_PASSWORD`).

**Viajes en curso (EJB remoto de Frontofficeciudadanos):**

Para saber si un vehiculo tiene guia, fiscalizacion llama por EJB remoto a `ViajesEnCursoService`
(Frontofficeciudadanos, paquete `service.gestiondeguiasyviajes`), que devuelve las guias del
vehiculo en estado `ASIGNADA` o `EN_CURSO` (`List<DatosViaje>`), y a `VehiculosService` (paquete
`service.gestionvehiculos`) para saber si el vehiculo existe y de que empresa es. El cliente de los
dos es `ruleengine/vehicles/VehicleFactsClient`.

- **Contrato:** fiscalizacion no depende del ejb entero sino de `frontofficeciudadanos-ejb-client.jar`
  (lo genera el `maven-ejb-plugin` con `generateClient`), que trae solo las interfaces remotas y sus
  DTOs: `ViajesEnCurso`, `ViajesEnCursoRemote`, `DatosViaje`, `Pesajes`, `PesajesRemote`,
  `DatosPesaje` y, de `service.gestionvehiculos`,
  `Vehiculos`, `VehiculosRemote` y `DatosVehiculo` (`buscarVehiculo(matricula)`: matricula y
  empresa, o `null` si el vehiculo no existe). Lo mismo con `backofficeautoridadesadministradores-ejb-client.jar`
  (`NodosActivos`, `NodosActivosRemote`, `DatosNodo`, `TipoNodo`) y con
  `frontofficefuncionarios-ejb-client.jar` (`CasosAutomaticos`, `CasosAutomaticosRemote`,
  `EventoFiscalizado`, `OrigenEvento`, `CasoAsignado`). Por eso, antes de compilar fiscalizacion hay
  que instalar Modelo, AdaptadorPdi y los tres proyectos en el `.m2` (el `Dockerfile` y el workflow de CI ya lo
  hacen):

  ```bash
  mvn -f componente_central/backend/Modelo/pom.xml install -Dmaven.test.skip=true
  mvn -f componente_central/backend/AdaptadorPdi/pom.xml install -Dmaven.test.skip=true
  mvn -f componente_central/backend/Frontofficeciudadanos/pom.xml install -Dmaven.test.skip=true
  mvn -f componente_central/backend/Frontofficefuncionarios/pom.xml install -Dmaven.test.skip=true
  mvn -f componente_central/backend/Backofficeautoridadesadministradores/pom.xml install -Dmaven.test.skip=true
  ```

**Nodos activos (EJB remoto de Backofficeautoridadesadministradores):** `NodosActivosService`
(paquete `service.gestionnodosperifericos`) devuelve los nodos perifericos con `activo = true`
(`List<DatosNodo>` con `id`, `idExterno`, `nombre` y `tipo`: `BALANZA` o `TRACKING`). Fiscalizacion
lo usa con el cliente `ruleengine/nodes/ActiveNodesClient`: el poller para saber que balanzas
consultar y el consumidor de tracking para saber de que GPS aceptar eventos
(`ruleengine/nodes/ActiveGpsRegistry`). Usuario, URL y errores igual que en *Viajes en curso*.

`id` es el que genera el central e `idExterno` el del nodo en su propio sistema (id de la balanza
en la API de balanzas, `gpsId` en tracking). Balanzas y GPS repiten ids entre si (hay balanza 1 y
GPS 1), por eso `idExterno` es unico por `tipo` en `nodos_perifericos` y no puede ser el `id`.


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

- Variables de fiscalizacion: `WILDFLY_URL` (`remote+http://localhost:8081`; en el compose
  `remote+http://wildfly:8080`), `EJB_USER`, `EJB_PASSWORD`.
- Errores: si WildFly esta caido, las credenciales son incorrectas o el EAR no esta desplegado, la
  llamada lanza `EJBException`, que el cliente convierte en `IOException`.
- En Kubernetes los valores salen de las claves `ejb-user` / `ejb-password` de
  `tse-runtime-secrets` (ver `k8s/README.md`).

**Deteccion de situaciones (`ruleengine/detection`):**

- Situaciones, en orden de prioridad (`Situation`): Viaje sin guia, Exceso de carga, Vehiculo sin
  habilitacion vigente y Desvio de ruta. Sin viaje en curso solo pueden aplicar Viaje sin guia y
  Habilitacion.
- Cada situacion es una regla (`SituationRule`, en `detection/rules`) que dice si aplica a un
  evento (`VehicleEvent`: un `TrackingEvent` o un `Weighing`) segun lo que el central sabe del
  vehiculo (`VehicleFacts`: el vehiculo y sus viajes en curso, por EJB remoto).
- `SituationDetector` devuelve las situaciones que aplican, en orden de prioridad. Si el vehiculo no
  existe en el central no devuelve ninguna (no hay empresa a la que notificar). Si WildFly no
  responde, falla: el evento se vuelve a evaluar despues.
- Agregar una situacion = agregar su regla en `Main.createDetector`.
- Implementadas:
  - Viaje sin guia (`TripWithoutGuideRule`): el vehiculo no tiene guias `ASIGNADA` ni `EN_CURSO`.
  - Exceso de carga (`OverloadRule`): en un pesaje de un vehiculo con viaje en curso, peso del
    vehiculo + su capacidad de carga declarada < peso medido por la balanza. La carga declarada en la
    guia no se usa. La tolerancia sera configurable (requisito 5); por ahora cualquier exceso cuenta.
- Eventos de tracking: los evalua el consumidor en segundo plano (`TrackingDetectionWorker`, ver
  *Consumidor de tracking*).

**Casos de fiscalizacion:** con las situaciones de un evento, `ruleengine/cases/CaseAssigner` decide
el caso aplicando la jerarquia, y deja el evento asociado a **un unico caso**:

- Recorre las situaciones en orden de prioridad y crea un caso, en estado **DETECTADA**, para la
  primera que el vehiculo no tenga **abierta**; a continuacion lo **notifica a la empresa** (pasa a
  `NOTIFICADA`). Abierto = `DETECTADA` o `NOTIFICADA` (`EstadoCaso.ABIERTOS`); un caso en descargo o
  resuelto ya no cuenta, y el proximo evento abre uno nuevo.
- Si todas las situaciones ya tienen un caso abierto, el evento se suma como evidencia al de mayor
  prioridad.
- Un evento que ya es evidencia de un caso no se vuelve a asignar (reprocesamiento). La base lo
  garantiza: `(matricula, fecha_hora)` es unico en `casos_eventos_tracking` y `casos_eventos_pesajes`.
- Un caso que quedo `DETECTADA` (fallo la notificacion) se notifica con su proximo evento.
- El caso guarda el vehiculo (y por el, la empresa) y, si hay viaje en curso, su guia (un Viaje sin
  guia no tiene). Tipos: `VIAJE_SIN_GUIA`, `EXCESO_CARGA`, `VEHICULO_SIN_HABILITACION`, `DESVIO_RUTA`.
- Los casos se guardan con el EJB remoto `CasosAutomaticosRemote` (Frontofficefuncionarios, paquete
  `service.gestioncasos`, cliente `ruleengine/cases/RemoteCaseStore`), que no decide nada: devuelve
  los casos abiertos de un vehiculo y el caso de un evento, crea un caso, agrega evidencia y notifica.
  La creacion y la notificacion quedan en la auditoria (`auditoria_casos`) con usuario `SISTEMA`
  (acciones `CREACION` y `CAMBIO_ESTADO`).
- **Varios pods a la vez:** como la decision la toma fiscalizacion, la base impide dos casos abiertos
  del mismo tipo para un vehiculo con un indice unico parcial (`uk_caso_abierto`, en
  `Frontofficeciudadanos/.../META-INF/esquema-adicional.sql`). Si dos pods crean el mismo caso a la
  vez, el segundo falla, su evento queda pendiente y al reintentarlo se suma al caso que ya existe.
- Si una llamada falla (WildFly caido), el evento queda pendiente y se vuelve a evaluar.
- En el log: `Created case 4 (VIAJE_SIN_GUIA) for SBC1234 at ...`, `Notified case 4 (VIAJE_SIN_GUIA)`
  o `Added to case 4 ...`.
- La notificacion es el cambio de estado y su auditoria. La empresa la ve en la campana del
  Frontoffice Ciudadanos (ver *Infracciones y descargos*); el envio por correo u otro canal esta pendiente.

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

**Poller de balanzas** (con `balanzas` arriba: `docker compose up -d balanzas` en `TSE_NODOS`,
`BALANZAS_CLIENT_ID` / `BALANZAS_CLIENT_SECRET` y `EJB_USER` / `EJB_PASSWORD` en el `.env`, y al
menos un `NodoBalanza` activo cuyo `idExterno` exista en balanzas, como la balanza 4 de
`datos-prueba.sql`; levanta MongoDB y WildFly si no estan
arriba):

```bash
docker compose run --rm --build fiscalizacionautomatica-poller   # una ejecucion; "Fetched N weighings of scales [4] from ... (until <to>)"
echo $?                                                         # 0 = ok, 1 = fallo alguna balanza, WildFly o MongoDB
```

La ultima consulta de cada balanza queda en `main_db.balanzas_metadata` (Compass). Para volver a
consultar una balanza desde el inicio del dia, borrar su documento (por ejemplo `{ _id: 4 }`).

Para que se ejecute solo cada 30 minutos en la maquina de desarrollo (opcional):

```bash
# Linux / macOS: agregar con "crontab -e"
*/30 * * * * docker compose -f /ruta/a/TSE_CENTRAL/docker-compose.yml run --rm fiscalizacionautomatica-poller >> /tmp/weighings-poller.log 2>&1
```

```powershell
# Windows (Programador de tareas). Para quitarlo: schtasks /Delete /TN tse-weighings-poller /F
schtasks /Create /SC MINUTE /MO 30 /TN tse-weighings-poller /TR "docker compose -f C:\ruta\a\TSE_CENTRAL\docker-compose.yml run --rm fiscalizacionautomatica-poller"
```

**Deteccion por EJB remoto** (con `EJB_USER` / `EJB_PASSWORD` en el `.env`; WildFly tiene
que haber arrancado con esas variables para crear el usuario):

```bash
docker compose up -d --build wildfly
docker logs wildfly-server | grep "Usuario de aplicacion"                 # el usuario se creo
docker compose run --rm --build fiscalizacionautomatica detect-situations           # SBC1234: "Situations: [TRIP_WITHOUT_GUIDE]"
docker compose run --rm --build fiscalizacionautomatica detect-situations STU5678   # con guia EN_CURSO: "Situations: []"
```

**Fuera de Docker** (con las variables de cada modo definidas, y Modelo y Frontofficeciudadanos
instalados en el `.m2`, ver *Viajes en curso*):

```bash
cd componente_central/backend/Fiscalizacionautomatica
mvn clean package
java -jar target/fiscalizacionautomatica.jar                     # consumidor de tracking
java -jar target/fiscalizacionautomatica.jar weighings-poller    # poller de balanzas (una ejecucion)
java -jar target/fiscalizacionautomatica.jar detect-situations [matricula]   # deteccion para un vehiculo (EJB remotos)
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
| Recurso | `Deployment` + KEDA (`keda.yaml`: 1 a 5 replicas, una cada 100 mensajes en `tracking.events`) | `CronJob` (`cronjob-poller.yaml`: cada 30 minutos, nunca dos a la vez) |
| Argumentos | ninguno | `["weighings-poller"]` |
| Variables | `RABBITMQ_HOST`, `RABBITMQ_PORT`, `RABBITMQ_USER`, `RABBITMQ_PASSWORD`, `MONGO_HOST`, `MONGO_PORT`, `MONGO_DB`, `MONGO_USER`, `MONGO_PASSWORD`, `MONGO_AUTH_DB` (base donde esta definido el usuario de Mongo) | `ENVIRONMENT=prod` (la URL de balanzas y el client id se leen de `app.json`, ConfigMap montado en `/app/config`), `BALANZAS_CLIENT_SECRET` (cliente OAuth dado de alta en balanzas) y las mismas `MONGO_*` del consumidor (guarda la ultima consulta en `balanzas_metadata`), y `WILDFLY_URL`, `EJB_USER`, `EJB_PASSWORD` (pide las balanzas activas al backoffice) |

Los manifiestos estan en `k8s/apps/fiscalizacionautomatica/` (ver `k8s/README.md`, seccion 7: instalar
KEDA, ajustar `app.json` y cargar el client secret en el Secret `fiscalizacion-balanzas`, que se crea con la clave
vacia). Un codigo de salida `1` deja el `Job` del poller como fallido (`kubectl -n tse get jobs`).

### Infracciones y descargos (frontofficeciudadanos)

Continua el ciclo de vida de los casos de fiscalizacion (*detectada → notificada → en descargo →
confirmada | desestimada*). El responsable de la empresa ve las infracciones de sus vehiculos con la
evidencia y presenta un descargo; el funcionario lo revisa antes de resolver.

**Pantallas:** en *Mi cuenta* → **Infracciones** (`/empresa/infracciones`). El listado marca **Nueva** a
las que todavia no se abrieron y muestra hasta cuando se puede presentar el descargo. El detalle
(`/empresa/infracciones/<id>`) muestra el vehiculo con su peso maximo (peso + capacidad declarada), la
guia, los pesajes con el exceso y la cantidad de posiciones de tracking; despues el formulario del
descargo (o el descargo presentado) y, si ya se resolvio, la resolucion del MTOP con su fundamento.

**Notificaciones (campana):** al responsable de la empresa el encabezado le muestra una campana con la
cantidad de casos notificados que todavia no vio (campo `notificacion_vista` del caso). Al abrirla se
despliegan los ultimos 10, primero los no vistos, con el plazo del descargo si todavia se puede presentar.
Al hacer click en uno queda visto y se abre la infraccion; tambien se pueden marcar todas como vistas.
La campana se vuelve a consultar cada 60 s mientras la pestaña esta visible, asi un caso nuevo aparece sin
recargar. Abrir el detalle de la infraccion tambien la marca vista.

**API** (`frontofficeciudadanos-web/api`, `X-Api-Key` + `X-Usuario-Ci` como el resto de `/empresa`):

| Metodo | Ruta | Que hace |
|---|---|---|
| `GET` | `/empresa/infracciones` | Casos de la empresa, del mas nuevo al mas viejo |
| `GET` | `/empresa/infracciones/{id}` | Detalle con la evidencia; marca la notificacion como vista |
| `GET` | `/empresa/infracciones/notificaciones` | `{noVistas, notificaciones: [...]}` para la campana |
| `POST` | `/empresa/infracciones/{id}/vista` | Marca la notificacion del caso como vista; devuelve las notificaciones |
| `POST` | `/empresa/infracciones/notificaciones/vistas` | Marca todas como vistas; devuelve las notificaciones |
| `POST` | `/empresa/infracciones/{id}/descargo` | Body `{"descripcion": "..."}`; devuelve el detalle actualizado |

**Reglas** (en `service/infraccionesydescargos/InfraccionesService`, no en la interfaz):

- La empresa de un caso es la de su vehiculo. Solo se ven los casos desde que se notifican
  (`NOTIFICADA`, `EN_DESCARGO`, `CONFIRMADA`, `DESESTIMADA`); un caso `DETECTADA` o de otra empresa
  responde 404, como si no existiera.
- El descargo se presenta **una sola vez**, con el caso `NOTIFICADA`, dentro del plazo y con hasta 2000
  caracteres. Si no: `400` (texto vacio o largo), `409 DESCARGO_YA_PRESENTADO`,
  `409 TRANSICION_NO_PERMITIDA` o `409 PLAZO_VENCIDO`.
- **Plazo:** `PLAZO_DESCARGO_DIAS` dias corridos (10 por defecto) desde la notificacion, que se toma de la
  pista de auditoria (el ultimo `CAMBIO_ESTADO` a `NOTIFICADA`; si no hay, la fecha de deteccion). Se cuenta
  por dias de Uruguay: el dia de la notificacion no cuenta y el ultimo dia vale entero (vence a las 00:00 del
  dia siguiente). `venceDescargo` es ese ultimo dia (`yyyy-MM-dd`). Ej.: notificada el 1/11 a cualquier hora,
  con 10 dias se puede presentar hasta el 11/11 inclusive.
- **Vencimiento automatico:** `VencimientoDescargoJob` corre todos los dias a las 00:05 (hora de Uruguay) y
  una vez al minuto de arrancar WildFly. Pasa a `EN_DESCARGO` (sin descargo) los casos `NOTIFICADA` con el
  plazo vencido, para que el funcionario los pueda resolver. Usa la misma regla del plazo que
  `InfraccionesService` (no la calcula de nuevo). Cada caso va en su propia transaccion, bloqueado y
  revisado otra vez (si justo se presento un descargo no lo toca), y queda en la auditoria con usuario
  `SISTEMA` y el fundamento "Vencio el plazo de N dias sin descargo de la empresa". Con varias replicas lo
  corre una sola (advisory lock de PostgreSQL). Cada corrida deja una linea `descargo.vencimiento` en el log, aunque no haya
  casos vencidos, y al arrancar queda otra con `resultado=programado`. Si el job todavia no corrio, `presentarDescargo` igual
  rechaza con `409 PLAZO_VENCIDO`. `VENCIMIENTO_DESCARGO_ACTIVO=false` lo apaga (en k8s, en
  `k8s/apps/wildfly/configmap.yaml`).
- Al presentarlo, el caso pasa a `EN_DESCARGO` (la transicion la valida `CasoFiscalizacion`) y queda en
  `auditoria_casos` con la cedula del responsable, el `correlationId` y el fundamento
  "Descargo presentado por la empresa N° ...". El caso se bloquea en la base mientras tanto
  (`PESSIMISTIC_WRITE`), asi dos descargos simultaneos no lo pisan.
- `Descargo` guarda el texto, `fechaPresentacion` y `ciPresentante`. El frontoffice de funcionarios los
  muestra en *Descargo de la empresa* ("Presentado el ... por el responsable C.I. ...").
- No se exige que la empresa este habilitada: una empresa inhabilitada igual tiene que poder defenderse.

**Probar en local:** entrar a http://localhost como el responsable `54074380` (con gub.uy o el ingreso
de testing), abrir *Infracciones* y presentar el descargo del caso `DEMO DESVIO` (`SAB5678`, en
`NOTIFICADA`). Despues, en el frontoffice de funcionarios, el caso aparece *En descargo* con el texto y
la auditoria (ver el escenario E11 de `Frontofficeciudadanos/.../META-INF/README_ESCENARIOS.md`).

**Pendiente:** un caso `NOTIFICADA` cuyo plazo vence sin descargo queda trabado: la empresa ya no puede
presentarlo y el funcionario solo resuelve casos `EN_DESCARGO`.

### Portal publico (frontofficeciudadanos, sin login)

Las dos funcionalidades publicas del frontoffice de ciudadanos. Se entra desde la portada (seccion *Sin
ingresar*); no hace falta sesion. El navegador nunca llama a WildFly: el servidor de Next consulta
`/publico/*` con la `X-Api-Key`, como el resto de la API (sin ella responde 401).

**Validacion de Permisos de Empresas** (`/validar`): un cliente verifica a la transportista antes de
contratarla. Busca por numero de empresa (o por nombre, `?nombre=`, si no lo sabe) y ve si esta habilitada
(no se dan numeros de la empresa, como cuantos vehiculos tiene). Si ademas ingresa una matricula, ve si el vehiculo es de esa
empresa, si esta en actividad y si el permiso de circulacion y la ITV estan vigentes (con su vencimiento).
Un vehiculo esta habilitado con la misma regla que en Gestion de Vehiculos (activo, permiso vigente, ITV
sin vencer) y ademas la empresa habilitada. Son datos de empresas y vehiculos, nunca de personas: no se
muestra el responsable ni los choferes, y una matricula de otra empresa solo dice que no pertenece (no se
revela de quien es).

**Listados de Informacion Publica** (`/estadisticas`, del anio en curso): empresas y vehiculos habilitados (hoy),
viajes y carga transportada en el anio, viajes por mes, carga por rubro del cliente y por tipo de carga,
empresas habilitadas por rubro (hoy) e infracciones por tipo. Se cuentan los viajes `FINALIZADA` y solo las infracciones `CONFIRMADA` (las demas pueden terminar
desestimadas). Rubros y tipos de carga son catalogos precargados (`esquema-adicional.sql`, sin ABM): cada valor
se muestra por nombre, incluso en 0, y lo que no esta en el catalogo (datos viejos) va junto en *Otros*, sin su
nombre. Las filas reservadas van despues de las visibles y por nombre, para que el orden no revele su valor. En
empresas por rubro, una empresa cuenta en cada rubro suyo: se aplica el umbral por fila, sin supresion
complementaria, porque las filas no suman ningun total.

Indicadores de fiscalizacion del anio (casos detectados en el anio):

- **Infracciones cada 100 viajes:** confirmadas / viajes finalizados x 100. Es mas comparable que el total,
  que crece con la actividad. Las dos partes del cociente tienen que alcanzar el umbral. La tabla por tipo
  muestra tambien la tasa de cada tipo.
- **Casos resueltos y % desestimados:** cuantos casos se confirmaron o desestimaron y que parte se desestimo
  (mide si la fiscalizacion automatica detecta bien o genera falsos positivos).
- **Tiempo de resolucion:** mediana de los dias entre la deteccion y el fundamento de resolucion. Mediana y
  no promedio, para que un caso trabado meses no la distorsione.
- En las tablas de viajes, el promedio de carga por viaje; cada fila tiene una barra proporcional.
- **Descarga en CSV** de cada tabla (`/estadisticas/csv?tabla=meses|rubros|tipos-carga|empresas-rubro|infracciones`, ruta del
  front): `;` como separador y BOM UTF-8 para Excel en es-UY; lo reservado sale como `Reservado`. Si un valor
  viejo empieza como una formula (`=`, `+`, `-`, `@`), se le antepone `'`.

No se publican maximos ni minimos (el viaje mas pesado, la empresa con mas infracciones): siempre
corresponden a una sola empresa.

| Metodo | Ruta (`frontofficeciudadanos-web/api`) | Que hace |
|---|---|---|
| `GET` | `/publico/empresas?nombre=...` | Busca por nombre publico o razon social (3 a 60 caracteres, hasta 10 resultados) |
| `GET` | `/publico/empresas/{nro}?matricula=...` | Validacion de la empresa y, opcional, de un vehiculo; 404 si no existe |
| `GET` | `/publico/estadisticas?anio=...` | Estadisticas agregadas del anio (por defecto el actual; 400 fuera de 2020..actual). El front por ahora no ofrece elegir el anio: solo hay datos del actual |

Como se protegen los datos (umbral de agregacion y demas reglas): ver *Proteccion de datos personales*.

Datos de demo: las empresas 3 a 5 (`Fletes del Norte`, `Cargas del Sur`, `Transporte Oriental`), con un
vehiculo habilitado y viajes finalizados cada una, y dos infracciones confirmadas. Con ellas el portal
muestra numeros reales y, a la vez, valores reservados: *Construcción* es solo de la empresa 5 (y arrastra a
*Forestal* por la supresion complementaria), asi que las dos figuran como *Reservado*, y los meses
con viajes de 2 empresas figuran como *Reservado*. Ver el escenario E12 de `README_ESCENARIOS.md`.

### Proteccion de datos personales (Ley 18.331)

carga.uy maneja datos de personas (cedula, nombres y fecha de nacimiento de responsables y choferes) y de
empresas (habilitacion, flota, viajes, infracciones). La Ley 18.331 protege a las personas fisicas y, en lo
que corresponda, a las juridicas. Las reglas que siguen se aplican en el backend, no en la interfaz:
ocultar algo en pantalla no protege nada si la API lo devuelve.

**1. Informacion publica (portal y reportes sin login): siempre agregada.**

- **Umbral minimo de agregacion: 3 empresas distintas.** Cada numero publicado (un total, un mes, un
  rubro, un tipo de infraccion, un indicador) tiene que juntar datos de al menos 3 empresas; si no, se
  publica como *Reservado*.
  - *Por que 3:* con 2 empresas, cualquiera de las dos resta sus propios datos del numero publicado y
    obtiene exactamente los de la otra. Con 3 o mas solo obtiene la suma de las demas, que no identifica a
    ninguna. Es el minimo que impide ese despeje.
  - *Vehiculos y choferes:* quedan cubiertos porque siempre pertenecen a una empresa. 3 empresas implican
    al menos 3 vehiculos y 3 choferes.
  - Se configura con `PORTAL_UMBRAL_EMPRESAS` en WildFly para hacerlo mas estricto, pero nunca baja de 3.
- **Supresion complementaria:** si en una tabla queda reservada una sola fila y se publica el total, se
  deduce restando. Por eso se reservan ademas las filas visibles mas chicas hasta que el conjunto de las
  reservadas tambien junte 3 empresas (o se reserva todo). Una fila en 0 se publica: no identifica a nadie.
- **Etiquetas:** meses, rubros, tipos de carga y tipos de infraccion son valores fijos del sistema: su nombre no
  identifica a nadie. Lo que no esta en un catalogo (texto libre viejo) no se muestra por nombre: va en *Otros*.
- **Indicadores:** la tasa de infracciones exige el umbral en sus dos partes (viajes e infracciones); el %
  de desestimados y la mediana de dias de resolucion, en los casos resueltos.
- **Sin extremos:** no se publican maximos ni minimos (el viaje mas pesado, la empresa con mas
  infracciones), porque siempre corresponden a una sola empresa.
- **En el backend:** la regla esta en `service/portalpublico/Agregacion` (sin estado, con tests) y la aplica
  `PortalPublicoService`. Lo reservado no viaja al navegador y el CSV sale de los mismos datos ya protegidos.
  Las consultas (`PortalPublicoDAO`) traen los datos por empresa solo para contar cuantas empresas aporta
  cada numero; eso no se publica.
- **Validacion de Permisos de Empresas:** es una consulta puntual (verificar a una transportista antes de
  contratarla), no un reporte estadistico. Responde solo si la empresa esta habilitada y, para una
  matricula de esa empresa, si puede circular. No da numeros de la empresa (cuantos vehiculos tiene, sus
  viajes o infracciones) ni datos de personas (responsable, choferes). Una matricula de otra empresa solo
  dice que no pertenece, sin revelar de quien es. La busqueda por nombre exige 3 letras y devuelve hasta 10.

**2. Informacion de cada empresa: solo para ella.**

- Cada operacion del Frontoffice Ciudadanos verifica en el servicio que quien llama sea responsable
  validado de la empresa (`AutorizacionResponsable`) y que el recurso sea de esa empresa. Una guia, un
  vehiculo o una infraccion de otra empresa responde 404, igual que si no existiera.
- La app movil del chofer ve solo sus viajes asignados: la identidad sale del token, no de lo que manda el
  cliente (`ViajesChoferService`).
- Los casos de fiscalizacion los ven los funcionarios del MTOP (`AutorizacionFuncionario`) y la empresa
  involucrada, esta recien desde que se le notifica.

**3. Datos de personas.**

- **Minimizacion:** al dar de alta un chofer, la empresa ve solo su primer nombre y primer apellido (no la
  fecha de nacimiento ni los demas datos que devuelve la PDI).
- **Registro de consultas a la PDI:** cada consulta de una cedula queda en `consultas_persona` (quien
  consulto, que cedula, fecha y hora).
- **Listado de usuarios del backoffice:** `GET /api/usuarios?pagina=1&tamanio=20` (solo `ADMINISTRADOR`) pagina por cedula a
  funcionarios, responsables, choferes, administradores y autoridades, una fila por persona con sus roles, nombres,
  apellidos y email. Filtros opcionales: `rol` (FUNCIONARIO, RESPONSABLE, CHOFER, ADMINISTRADOR, AUTORIDAD) y `ci`
  (comienzo de la cedula, acepta puntos y guion). Un ciudadano sin rol no aparece. `POST /api/usuarios` da de alta
  administradores y autoridades (cedula y contrasena inicial de 12 a 128 caracteres, guardada con PBKDF2) y funcionarios
  (cedula y email). Choferes y responsables no: tienen su propio alta (CU-02 y la gestion de empresas).
  `PUT /api/usuarios/{ci}/rol` reemplaza el rol de un funcionario, administrador o autoridad por otro de esos tres (pide
  contrasena o email si el rol nuevo los necesita). Un funcionario que deja de serlo queda inactivo (`funcionarios.activo`):
  conserva su historial pero no puede ingresar. El rol del backoffice se lee de la base en cada pedido, no del token.
  `DELETE /api/usuarios/{ci}` le quita todos esos roles: borra al administrador o autoridad y deja inactivo al funcionario
  (no toca sus roles de chofer o responsable). Un administrador no puede cambiar su rol ni eliminarse a si mismo. No incluye fecha de nacimiento, `sub` de gub.uy ni contrasena. Quien lo
  consulto queda en el log `http.request`.
- **Sesiones:** la sesion del front va en una cookie cifrada. La API key del backend solo la tiene el
  servidor de Next y nunca llega al navegador; el backend confia en `X-Usuario-Ci` por eso (si la API se
  abriera a otros clientes habria que pasar un token firmado).
- **Logs:** identifican al usuario por cedula y cada operacion por `correlationId`, para poder auditar quien
  hizo que. No registran nombres, fechas de nacimiento, textos de descargos ni lo que se busca en el portal
  publico.
- **Secretos:** credenciales y claves van en `.env` / `.env.local`, que no se commitean.

### Observabilidad

Todos los componentes escriben sus logs como **JSON de una linea** con los mismos campos:

```json
{"timestamp":"...","nivel":"INFO","componente":"frontofficefuncionarios","evento":"caso.resuelto","correlationId":"...","usuario":"...","responseCode":200,"roundtripSeconds":0.042,"backendRoundtripSeconds":0.012,"datos":{"caso":7}}
```

- **Clase comun:** `LogEstructurado` (libreria `Modelo`, paquete `tse.lab.log`) tiene los campos que comparten todos
  los modulos. Lo propio de cada uno va con `campo(...)`, dentro de `datos`. Cada modulo tiene su fabrica, que fija el
  componente: `LogCiudadanos`, `LogFuncionarios`, `LogBackoffice`, `LogFiscalizacion`. Los fronts usan `src/lib/log.ts`,
  con el mismo formato; la app movil, `LogMovil`.
- **Correlacion:** el front genera el `X-Correlation-Id` y lo manda al backend, que lo devuelve y lo pasa a la PDI. En
  la app movil es el `idEvento`. La fiscalizacion automatica lo deriva del evento (`evt-<matricula>-<fecha>`) y lo pasa
  por EJB remoto, asi que un caso creado se sigue desde la deteccion hasta su pista de auditoria.
- **Nivel:** el campo `nivel` (`INFO`, `WARN` o `ERROR`) va dentro del JSON, porque la consola de WildFly solo escribe
  el mensaje.
- **Pedidos HTTP:** cada backend deja un `http.request` con `responseCode` y `roundtripSeconds`, y cada front un
  `backend.llamada` con `backendRoundtripSeconds`. Lo que ningun recurso atrapo deja ademas un `http.error` con la
  pila y responde 500 (`UnhandledExceptionMapper`).
- **Pista de auditoria:** ademas de la tabla `auditoria_casos`, cada accion de fiscalizacion deja por consola una linea
  `evento=auditoria.caso` (`PistaAuditoria`) con usuario, accion, estados, fundamento y fecha. Se escribe cuando confirma la
  transaccion que guarda el registro, asi que cubre creacion, notificacion, descargo y resolucion, y una accion
  deshecha por un rollback no deja linea. Solo se agrega: la aplicacion no tiene forma
  de modificarla ni borrarla.
- **WildFly:** los loggers `tse.lab` salen por un handler propio, sin el prefijo del servidor
  (`wildfly/datasources.cli`).

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
