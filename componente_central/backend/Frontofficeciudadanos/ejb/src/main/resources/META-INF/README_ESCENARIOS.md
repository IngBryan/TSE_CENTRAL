# carga.uy — Escenarios recomendados para la primera demo del MVP

**Alcance:** primera presentación, con los datos de `datos-prueba.sql` y el mock de Balanzas controlado. Cada escenario indica acciones y qué se debe comprobar. Los resultados son **esperados** y deben validarse en la ejecución local; no constituyen evidencia de pruebas de integración ya realizadas.

**Orden sugerido:** E01 → E10 → E02 → E03 → E04 → E05 → E06 → E07 → E08 → E09. E03 y E07 dependen de la autenticación y la PDI disponibles en el ambiente; si no están listos, pueden realizarse por separado.

## E01 — Empresas y acceso a datos

**Datos:** Empresa 1, `Transportes del Este` (habilitada); Empresa 2, `Logistica del Litoral` (no habilitada).

1. Entrar al Frontoffice de Funcionarios y consultar el listado de empresas.
2. Identificar las dos empresas y comparar su estado de habilitación.
3. Examinar los vehículos de Empresa 1 y localizar `SBA1234`, `SAB5678` y `SDE3000`.
4. Desde un perfil responsable de Empresa 1, intentar acceder a `SCL2001`, perteneciente a Empresa 2.
5. **Verificar:** ambas empresas son consultables por el funcionario; la operación sobre el vehículo ajeno no se autoriza en el servicio.

## E10 — Gestión de vehículos, permisos e ITV

**Datos:** `SBA1234` habilitado; `SAB5678` activo pero sin permiso ni ITV; `SDE3000` habilitado y libre; `SDF3000` inactivo.

1. Entrar al Frontoffice Ciudadanos y abrir **Vehículos**.
2. Contrastar los cuatro vehículos, sus estados y sus permisos/ITV.
3. Dar de alta `SDX9000` como vehículo de prueba, indicando marca, modelo, peso y capacidad válidos.
4. Agregar un Permiso Nacional de Circulación y una ITV vigentes; comprobar el estado resultante.
5. Modificar un dato del vehículo y probar un alta duplicada usando otra representación de su matrícula (por ejemplo, con guion o espacios).
6. Opcionalmente, dar de baja `SDX9000` si todavía no tiene viaje activo.
7. **Verificar:** normalización de matrícula, control de duplicados, consulta de habilitación y baja lógica. **No afirmar** que la asignación exige permisos/ITV vigentes: esa validación no está incorporada al selector de guías.

## E02 — Asignación de guía a vehículo y chofer

**Datos:** `GV-DEMO-ASIGNAR`, Empresa 1, estado `SIN_ASIGNAR`; vehículo libre `SDE3000`.

1. Entrar al Frontoffice Ciudadanos y abrir `GV-DEMO-ASIGNAR`.
2. Comprobar que aún no tiene vehículo ni chofer asignados.
3. Comprobar que `SCL2001` (otra empresa), `SDF3000` (inactivo) y el chofer `22222222` (pendiente de validación) no son opciones válidas.
4. Asignar `SDE3000` y un chofer habilitado de Empresa 1.
5. Confirmar y volver a abrir la guía.
6. **Verificar:** estado `ASIGNADA`, vínculo a matrícula/chofer y rechazo de una segunda asignación sobre la misma guía.

## E03 — Móvil: consulta, offline e idempotencia

**Datos:** guías `GV-MOVIL001` y `GV-MOVIL002`, asignadas a choferes de Empresa 1.

1. Ingresar en la aplicación móvil con un chofer cuya identidad pueda autenticarse en el entorno de pruebas.
2. Descargar su guía asignada y comprobar que los detalles quedan disponibles localmente.
3. Desconectar Internet y registrar un evento `INICIO`.
4. Confirmar que el evento queda pendiente en el dispositivo.
5. Reconectar y sincronizar el evento a través de la API REST móvil del Central.
6. Reenviar **el mismo identificador de evento**.
7. **Verificar:** la guía pasa a `EN_CURSO` una única vez y el reenvío se reconoce como duplicado. Este escenario requiere que el login móvil esté disponible; los ciudadanos del seed no garantizan por sí solos autenticación en gub.uy.

## E04 — Tracking: detección automática de viaje sin guía

**Datos:** GPS 1 / `SBC1234` sin viaje activo; GPS 2 / `STU5678` con `GV-FISC001` en `EN_CURSO`.

1. Consultar la bandeja y comprobar que no existe previamente un caso abierto `VIAJE_SIN_GUIA` para `SBC1234`.
2. Confirmar los estados de las guías de ambos vehículos.
3. Activar la generación de posiciones de Tracking (ruta `PUT /api/tracking-on`, body `{"trackingOn":true}`, con Bearer OAuth de Tracking).
4. Observar la publicación de eventos en RabbitMQ y su consumo por Fiscalización Automática.
5. Consultar el caso nuevo de `SBC1234` y su evidencia de posiciones.
6. Verificar que `STU5678` no genera el mismo tipo de infracción.
7. Desactivar Tracking (`{"trackingOn":false}`).
8. **Verificar:** caso nuevo `VIAJE_SIN_GUIA` para `SBC1234`, estado `NOTIFICADA`, registro de auditoría y ausencia de casos duplicados para el mismo hecho. La transición de estado no equivale a un aviso enviado a la empresa.

## E05 — Balanzas: pesaje conforme, exceso real e idempotencia

**Datos:** balanza externa `4` activa; `STU5678` con guía `GV-FISC001` `EN_CURSO`; peso propio 9.200 kg + capacidad 27.000 kg = **máximo 36.200 kg**.

1. Verificar que no exista ya un caso abierto `EXCESO_CARGA` para `STU5678`.
2. Registrar por el nuevo `POST /api/scales/4/weighings` el pesaje conforme:

   ```json
   {"licensePlate":"STU5678","weight":32100}
   ```

3. Ejecutar una vez `docker compose run --rm --build fiscalizacionautomatica-poller` desde `TSE_CENTRAL`.
4. **Verificar:** el pesaje se ingiere; no se abre un caso de exceso.
5. Registrar **otro** pesaje en el mismo endpoint:

   ```json
   {"licensePlate":"STU5678","weight":39000}
   ```

6. Ejecutar nuevamente `docker compose run --rm fiscalizacionautomatica-poller`.
7. **Verificar:** se genera un caso `EXCESO_CARGA` en `NOTIFICADA`, vinculado a `STU5678` y `GV-FISC001`, con evidencia del pesaje y un exceso de **2.800 kg**.
8. Ejecutar el poller otra vez **sin repetir el POST**.
9. **Verificar:** no se duplican el pesaje ni el caso por reprocesamiento. Cada POST crea un evento distinto y **no** sirve para probar la idempotencia.
10. Dejar el caso generado en `NOTIFICADA`; **no crearle un descargo ni cambiarle el estado manualmente**.

## E06 — Resolución de un caso precargado en descargo

**Este escenario es independiente de E05.** El caso `DEMO DESCARGO` de `SBA1234` ya existe en `EN_DESCARGO` con una evidencia de **38.500 kg** frente a un máximo de **35.000 kg** y el texto «La balanza del peaje estaba descalibrada ese día».

1. Entrar al Frontoffice Funcionarios y abrir la bandeja.
2. Buscar el caso cuya descripción comienza por `DEMO DESCARGO:`; comprobar el vehículo `SBA1234` y su estado `EN_DESCARGO`.
3. Consultar la evidencia del pesaje y el descargo asociado.
4. Seleccionar la acción de resolver; elegir `CONFIRMADA` o `DESESTIMADA` y escribir un fundamento.
5. Confirmar la resolución desde la aplicación.
6. Volver al caso y consultar estado, fundamento y auditoría.
7. Intentar resolver nuevamente el caso terminado.
8. **Verificar:** resolución y auditoría reales; segundo intento rechazado. **No se demuestra** aquí la presentación de descargo desde Ciudadanos; ese flujo corresponde a la segunda demo.

## E07 — PDI: validación y comportamiento ante fallas

**Datos PDI mock:** adultos `11111111` y `22222222`; menor `33333333` (entre otros). La PDI utiliza SOAP y su propio conjunto de datos.

1. Invocar una validación nueva para un ciudadano adulto todavía pendiente y comprobar su respuesta.
2. Invocar una validación de un ciudadano menor; comprobar que no queda habilitado como chofer.
3. Desde la administración SOAP de la PDI, configurar la simulación en modo `LENTO` o `CAIDO`.
4. Iniciar **otra** validación de identidad, para evitar reutilizar una validación previamente exitosa.
5. **Verificar:** la falta de respuesta o demora no vuelve indisponible el Central y se aplica el comportamiento degradado definido.
6. Restaurar la simulación a `NORMAL`.

## E08 — Resiliencia: Balanzas no disponible

1. Con Balanzas funcionando, realizar una consulta exitosa desde el poller.
2. Detener únicamente el servicio/contenedor `balanzas`.
3. Ejecutar nuevamente el poller.
4. **Verificar:** falla controlada de consulta al nodo, registro del error y disponibilidad de las funciones ajenas a Balanzas.
5. Reanudar `balanzas`.
6. Registrar **un nuevo** pesaje mediante POST y ejecutar de nuevo el poller.
7. **Verificar:** la comunicación se recupera. Los pesajes creados por POST antes del reinicio desaparecen del mock, porque se guardan en memoria; los cuatro pesajes históricos fijos vuelven a cargarse.

## E09 — Administración: alta de nodo periférico y permisos

**Datos:** en Central están registrados Balanzas `1` (inactiva), `4` (activa), y Tracking `1` y `2`. La balanza `2` (`Ruta 5 - Km 95`) existe en el mock pero todavía no en Central.

1. Entrar al Backoffice con el administrador de prueba.
2. Abrir Gestión de Nodos Periféricos y localizar los nodos existentes.
3. Dar de alta un nodo tipo `BALANZA`, `id_externo = 2`, nombre `Ruta 5 - Km 95`; dejarlo activo.
4. **Verificar:** queda registrado en Central.
5. Intentar dar de alta otra `BALANZA` con `id_externo = 2`.
6. **Verificar:** rechazo por duplicación del par tipo/identificador externo.
7. Intentar la misma operación con un usuario de rol `AUTORIDAD`.
8. **Verificar:** la autorización del servicio no permite gestionar nodos a ese rol. Conviene presentar E09 al final porque incorporar la balanza 2 cambia qué nodos consulta el poller.

---

**Criterio de la primera demo:** mostrar detección automática de exceso y viaje sin guía sobre eventos reales; demostrar **por separado** resolución de un caso ya precargado en `EN_DESCARGO`. No realizar SQL manual para presentar descargos ni afirmar que las reglas de desvío y habilitación vencida ya detectan automáticamente infracciones.
