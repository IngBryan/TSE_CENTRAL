# Diseño del set de pruebas

Fecha de revisión: 10 de noviembre de 2026. Rama: `test/pruebas-servicios-casosfisc-tracking-mobile`, base `a8d2605`.

Estado: implementados el ciclo de vida administrativo y el bloque de tracking con el alcance de pruebas unitarias y de servicios con H2 acordado. Las secciones 5 y 6 describen las pruebas y su ejecución. La sincronización móvil continúa pendiente. Este documento no acredita el 80 % global de cobertura.

## 1. Objetivo y fuente de los resultados esperados

La fuente principal es la letra del laboratorio, `obligatorio_tse_2026.docx.pdf`, en particular «Aspectos de Cubrimiento con Pruebas» y la sección 3.5. Se complementa con las decisiones de comportamiento confirmadas por el equipo. Los diagramas y README que contradigan esas decisiones necesitan revisión; no determinan por sí solos qué debe esperar una prueba.

La letra pide aproximadamente un 80 % de cubrimiento de la lógica mediante pruebas automatizadas, al menos una prueba por servicio y cobertura explícita del ciclo de vida, de las transiciones no previstas y de la idempotencia de tracking y sincronización móvil.

La interpretación de trabajo para «servicio» será una funcionalidad ofrecida por los componentes de la plataforma. La letra no identifica ese término con cada clase Java cuyo nombre termina en `Service`. El inventario de componentes y funcionalidades permitirá comprobar ese mínimo; los escenarios y la medición de cobertura determinarán cuánto más hace falta.

Cada prueba debe responder a esta cadena: **requisito → escenario → resultado esperado → comprobaciones → prueba que lo demuestra**. La cantidad de pruebas será consecuencia de esa cadena.

## 2. Decisiones de comportamiento confirmadas

| Tema | Comportamiento definido |
| --- | --- |
| Infracciones de un evento | Un evento puede respaldar varias infracciones aplicables. |
| Notificación | El paso a `NOTIFICADA` y la disponibilidad para la empresa en la aplicación constituyen la notificación. |
| Plazo de descargo | Es configurable; el valor por defecto actual es 10 días civiles de Uruguay. No se cuenta el día de notificación y se admite todo el último día. |
| Vencimiento sin descargo | El sistema pasa el caso a `EN_DESCARGO`, registra el motivo y permite al funcionario resolver. No crea un descargo ficticio. |
| Caso `VIAJE_SIN_GUIA` | Acumula GPS mientras no esté resuelto, incluso entre viajes físicos distintos. `EN_DESCARGO` todavía no es un estado final. |
| GPS después de una resolución | Un GPS nuevo válido sin Guía debe originar otro caso. La repetición de un GPS anterior conserva su asociación original. |
| Autorización existente en REST | Se conserva la ubicación actual de esas comprobaciones en Backoffice. Este trabajo no implica trasladarlas a servicios. |
| Frontend web | Está excluido deliberadamente de la medición de cobertura. |
| Mutación de código | Fuera del alcance acordado. |

La corrección de `VIAJE_SIN_GUIA` ya se incorporó desde `fix/viaje-sin-guia`. Los escenarios de GPS correspondientes se consideran al diseñar el bloque de tracking; no se reincorporan las adiciones puntuales de caracterización que se retiraron.

## 3. Organización general del set

### 3.1. Ciclo de vida de los casos

Cuatro recorridos completos: presentación de descargo o vencimiento sin presentación, seguidos de confirmación o desestimación. Se agregan comprobaciones del modelo, de las operaciones rechazadas, de la notificación y de la auditoría. La sección 4 desarrolla este bloque.

### 3.2. Eventos de tracking: recepción, procesamiento e idempotencia

| Escenario | Resultado que deberá demostrarse |
| --- | --- |
| GPS válido nuevo | Se conserva y procesa con su contexto temporal. |
| Repetición del mismo GPS | No se agregan efectos de negocio duplicados. |
| Mismo hecho con otro identificador de transporte | Sigue reconociéndose como el mismo hecho. |
| GPS diferentes fuera de orden | Se utiliza la fecha de medición para reconstruir la ruta. |
| Varias infracciones aplicables | Se conservan todas las aplicables sin duplicar evidencia dentro de cada caso. |
| Falla después de guardar el GPS | La recuperación encuentra el pendiente y lo procesa. |
| Falla después de registrar parte de las infracciones | El reintento completa lo pendiente y conserva lo ya registrado. |
| Falla antes de marcar el procesamiento terminado | El reprocesamiento conserva los efectos confirmados. |
| Entregas simultáneas del mismo evento | Se mantiene un único efecto por hecho y caso. |
| Caso sin Guía abierto o resuelto | Se respeta la política de acumulación, nuevo caso y repetición descrita en la sección 2. |

Estos escenarios no quedan cubiertos por comprobar únicamente que se llamó a un método. Cuando la garantía depende de persistencia, recuperación o concurrencia, se debe observar el resultado almacenado en los sistemas involucrados.

### 3.3. Sincronización con el componente móvil

| Escenario | Resultado que deberá demostrarse |
| --- | --- |
| Evento válido | Se guarda y aplica el efecto correspondiente. |
| Reenvío del mismo UUID | Se conserva el resultado original sin repetir el efecto. |
| Central confirma y se pierde la respuesta | El cliente reintenta con el mismo UUID y recupera el resultado. |
| La Guía cambia durante la desconexión | Se aplica la política de conflictos vigente; hay que revisar su fuente antes de fijar cada resultado esperado. |
| Dos envíos simultáneos | No se generan dos eventos ni dos modificaciones por el mismo UUID. |
| Reinicio sin conexión | Se conservan la Guía descargada y los eventos pendientes. |
| Recuperación de conectividad | Los pendientes se sincronizan conservando su identidad. |

Con los repositorios centrales y de nodos se puede probar la parte central. La persistencia y recuperación offline del cliente requieren el repositorio y las pruebas del componente móvil. No se darán por demostradas con pruebas exclusivas de central.

### 3.4. Presencia por componente y cobertura global

| Componente o funcionalidad | Flujo significativo de referencia |
| --- | --- |
| Frontoffice de ciudadanos | Consultar una infracción propia y presentar descargo. |
| Frontoffice de funcionarios | Consultar evidencia y resolver el caso con fundamento. |
| Backoffice | Actualizar una regla y comprobar su aplicación sin redespliegue. |
| Fiscalización automática | Procesar un hecho, registrar el caso y desencadenar su notificación. |
| Nodo de tracking | Publicar eventos y recuperar envíos pendientes. |
| Nodo de balanzas | Responder las consultas de pesajes realizadas desde central. |
| PDI | Responder una consulta y permitir verificar la reacción ante indisponibilidad. |
| Móvil | Conservar y sincronizar eventos offline. |

El objetivo aproximado del 80 % se evaluará sobre la lógica del sistema incluida en la medición acordada, sin el frontend web. Las tres suites principales no bastan por sí solas para declarar ese porcentaje. Las pruebas útiles de empresas, vehículos, Guías, usuarios, reportes y las demás funcionalidades también contribuyen.

Se documentarán el alcance y las exclusiones de la medición, el porcentaje obtenido y las consecuencias de los caminos relevantes que permanezcan sin probar. Cubrir líneas no demuestra por sí solo que se respeten los requisitos.

## 4. Primera etapa: ciclo de vida de los casos de fiscalización

### 4.1. Contrato de estados

| Estado actual | Estados siguientes permitidos |
| --- | --- |
| `DETECTADA` | `NOTIFICADA` |
| `NOTIFICADA` | `EN_DESCARGO` |
| `EN_DESCARGO` | `CONFIRMADA`, `DESESTIMADA` |
| `CONFIRMADA` | Ninguno |
| `DESESTIMADA` | Ninguno |

Hay 25 pares entre los cinco estados: cuatro transiciones permitidas y 21 rechazadas, incluidos los cinco intentos de pasar al mismo estado. El rechazo de una transición debe conservar el estado anterior.

**Prueba actual que lo comprueba:** `CasoFiscalizacionTest.todasLasCombinacionesDeEstados()`. Tiene dos bucles sobre `EstadoCaso.values()`, compara con un mapa explícito de transiciones esperadas y ejercita `CasoFiscalizacion.cambiarEstado(...)`. No consulta la base de datos ni ejecuta las operaciones de descargo o resolución.

Se conserva esta prueba. Si se separan sus combinaciones en casos parametrizados, el cambio debe mejorar la identificación del par que falla, manteniendo los mismos resultados esperados independientes del enum de producción.

### 4.2. Los cuatro recorridos completos

Todos comienzan con un caso nuevo creado mediante la operación de registro de evidencia y notificado mediante la operación correspondiente. El identificador debe ser el mismo durante todo el recorrido.

| ID | Entrada a `EN_DESCARGO` | Decisión del funcionario | Resultado final |
| --- | --- | --- | --- |
| CL-R01 | La empresa presenta un descargo dentro del plazo | `CONFIRMAR`, con fundamento | `CONFIRMADA`, con descargo conservado |
| CL-R02 | La empresa presenta un descargo dentro del plazo | `DESESTIMAR`, con fundamento | `DESESTIMADA`, con descargo conservado |
| CL-R03 | Vence el plazo sin descargo y actúa el sistema | `CONFIRMAR`, con fundamento | `CONFIRMADA`, sin descargo |
| CL-R04 | Vence el plazo sin descargo y actúa el sistema | `DESESTIMAR`, con fundamento | `DESESTIMADA`, sin descargo |

Los recorridos se ejecutarán mediante `CasosAutomaticosService.registrarEvidencia`, `CasosAutomaticosService.notificarCaso`, `InfraccionesService.presentarDescargo` o `InfraccionesService.vencerPlazo`, y `GestionCasosService.resolver`.

No se sustituirán esas operaciones por cambios directos de estado dentro de los recorridos completos. En una prueba enfocada exclusivamente en rechazar una resolución sí se puede preparar un estado inicial; esa prueba demuestra la regla local y se identifica como tal.

#### Datos de referencia

Se utilizará inicialmente un caso de exceso de carga con Guía, vehículo, empresa, responsable validado, funcionario y pesaje ya almacenado. Por ejemplo, vehículo de 9.000 kg más capacidad de 26.000 kg y pesaje de 38.500 kg. La evidencia tiene identidad y fecha conocidas.

El cálculo que detecta el exceso se prueba en el bloque del motor de fiscalización. Aquí el pesaje permite observar la conservación de la evidencia durante el trámite. El ejemplo con Guía evita decidir en esta etapa las diferencias detectadas entre letra y código respecto del pesaje sin Guía.

El flujo administrativo es común a los tipos de caso. No se multiplicarán automáticamente los cuatro recorridos por todos los tipos: las diferencias de creación, evidencia y acumulación tendrán escenarios específicos donde corresponda.

#### Qué se comprueba en cada etapa

| Etapa | Comprobaciones sobre el resultado y los datos guardados |
| --- | --- |
| Creación | Estado `DETECTADA`; identificador; empresa, vehículo y Guía correctos; evidencia vinculada; fecha de detección; registro de creación por `SISTEMA`. |
| Antes de notificar | La infracción todavía no aparece en las consultas de la empresa. |
| Notificación | Mismo caso en `NOTIFICADA`; aparece para el responsable de su empresa; otra empresa no accede; fecha de notificación y transición en la auditoría. |
| Presentación de descargo | Estado `EN_DESCARGO`; un descargo con texto, presentante y fecha; transición atribuida al responsable; lo presentado es consultable por el funcionario. |
| Vencimiento sin presentación | Estado `EN_DESCARGO`; ningún descargo creado; transición atribuida a `SISTEMA` con motivo de vencimiento; el funcionario puede resolver. |
| Resolución | Estado final esperado; decisión y fundamento correctos; funcionario y fecha de resolución; registro de auditoría con estado anterior y nuevo; resolución consultable por la empresa y el funcionario. |
| Conservación | Permanecen el identificador, la empresa, el vehículo, la Guía y la evidencia original. El descargo, cuando existe, se conserva. Los registros anteriores de auditoría no se sustituyen. |

Se releerán los datos tras confirmar cada operación y limpiar el contexto de persistencia. Así se comprueba lo almacenado, además del objeto devuelto por el servicio.

En un recorrido sin incorporación de evidencia adicional, se comprobará la secuencia de cuatro registros de negocio: creación, notificación, entrada a descargo y resolución. Para cada uno se comprobarán caso, acción, actor, estados, fecha y correlación; en la resolución, además, el fundamento. La correlación corresponde a cada operación, no tiene que ser idéntica en todo el ciclo.

### 4.3. Plazo: valores concretos y fronteras

Se controlará el reloj de las pruebas. No se esperarán días reales ni se dependerá de la fecha de ejecución.

| ID | Condición con plazo de 10 días | Resultado esperado |
| --- | --- | --- |
| CL-P01 | Notificación el 1/11 en Uruguay | El último día es el 11/11. |
| CL-P02 | Notificación `2026-11-02T02:30:00Z`, todavía 1/11 en Uruguay | El último día sigue siendo el 11/11. |
| CL-P03 | Presentación el 11/11 a las 23:59:59 de Uruguay | Se acepta el descargo; el vencimiento automático todavía no procede. |
| CL-P04 | Presentación el 12/11 a las 00:00 de Uruguay | Se rechaza por plazo vencido; el vencimiento automático puede pasar el caso a `EN_DESCARGO`. |
| CL-P05 | Detección antigua y notificación reciente | El plazo se cuenta desde la notificación. |
| CL-P06 | Se presenta descargo a tiempo y después corre el vencimiento | Se conserva el descargo y no se repite la transición ni su auditoría. |
| CL-P07 | Se ejecuta nuevamente el vencimiento sobre un caso ya vencido | No agrega otra transición ni otro registro de vencimiento. |
| CL-P08 | Plazo configurado distinto de 10 días | El último día se ajusta a la configuración; la frontera de admisión y vencimiento sigue siendo la misma. |

Los resultados esperados usarán fechas explícitas, sin obtener el valor esperado llamando al mismo cálculo de plazo de producción que se quiere verificar.

### 4.4. Operaciones rechazadas y conservación del caso

Para cada rechazo se compara con una instantánea anterior: estado, descargo, resolución, evidencia y registros previos de auditoría. No se crea una resolución ni se registra una transición exitosa. Un log de intento rechazado no equivale a una transición del caso.

| ID | Operación o condición | Resultado esperado |
| --- | --- | --- |
| CL-G01 | Resolver desde `DETECTADA` o `NOTIFICADA`, con cualquiera de las dos decisiones | Rechazo por transición no permitida. |
| CL-G02 | Volver a resolver desde `CONFIRMADA` o `DESESTIMADA`, intentando confirmar o desestimar | Rechazo; se conserva la primera resolución y su fundamento. |
| CL-G03 | Presentar otro descargo después de uno aceptado | Rechazo por descargo ya presentado; se conserva el primero. |
| CL-G04 | Presentar descargo con el plazo vencido, antes y después de la ejecución del vencimiento | Rechazo por plazo vencido. |
| CL-G05 | Presentar descargo sobre un caso final que no tuvo descargo | Rechazo; no se reabre el caso. Probar ambos estados finales. |
| CL-G06 | Presentar descargo sobre un caso todavía `DETECTADA` | La empresa no accede al caso; no se salta la notificación. |
| CL-G07 | Responsable de otra empresa consulta o presenta descargo | Se rechaza el acceso sin modificar el caso ajeno. |
| CL-G08 | Persona que no es funcionario intenta resolver | Se rechaza la operación sin efectos sobre el caso. |
| CL-G09 | Resolución sin fundamento, con fundamento en blanco o que excede el máximo, usando una decisión válida | Rechazo de los datos; no se guarda resolución parcial. |
| CL-G10 | Decisión inválida, usando un fundamento válido | Rechazo de los datos; no cambia el estado. |
| CL-G11 | Descargo vacío, nulo o superior al máximo | Rechazo; no se crea un descargo ni se cambia de estado. |
| CL-G12 | Repetir la notificación de un caso notificado, en descargo o finalizado | No se retrocede a `NOTIFICADA` ni se duplica la notificación. |
| CL-G13 | Vencer un caso inexistente, detectado, ya en descargo, final o cuyo plazo todavía no terminó | No produce una transición de vencimiento. |

Las combinaciones tienen una razón: dos decisiones posibles, dos estados finales y dos momentos del rechazo por vencimiento. Se parametrizarán esas diferencias; no se agregarán variantes de datos sin una regla que las justifique.

### 4.5. Pruebas existentes y decisión de reformulación

La tabla registra la revisión inicial y su relación con la reformulación. El resultado de la ejecución se informa por separado en la sección 5.

| Referencia | Prueba actual | Qué demuestra y decisión de diseño |
| --- | --- | --- |
| Modelo | `CasoFiscalizacionTest.todasLasCombinacionesDeEstados` | Los 25 pares en el modelo. Conservar. |
| Creación | `CasosAutomaticosServiceTest.creaElCasoDetectadoConElVehiculoYElEventoComoEvidencia`, `unCasoConGuiaQuedaAsociadoAEllaYAlPesaje` | Registro con persistencia y evidencia. Conservar como pruebas de la operación de creación. |
| Notificación | `CasosAutomaticosServiceTest.notificarPasaElCasoANotificadaYQuedaEnLaAuditoriaComoSistema`, `notificarUnCasoQueYaNoEstaDetectadoNoHaceNada` | Se conserva el paso local. La prueba de repetición se amplió a los cuatro estados posteriores a `DETECTADA`, CL-G12. |
| Visibilidad | `InfraccionesServiceTest.laEmpresaVeSoloSusCasosNotificadosConElPlazoDeDescargo`, `losCasosDeOtraEmpresaOSinNotificarNoExisten` | Se conserva el listado propio y el rechazo de consulta ajena o no notificada; se agregó el intento de presentar descargo antes de notificar, CL-G06. |
| Descargo | `InfraccionesServiceTest.presentarElDescargoPasaElCasoAEnDescargoYQuedaEnLaAuditoria`, `noSePuedePresentarDosVeces`, `elDescargoVacioEsInvalido`, `elDescargoMuyLargoEsInvalido`, `elDescargoSinDatosEsInvalido` | Se conserva la operación y su auditoría. Se separaron los datos inválidos y se reforzó la conservación del estado y del descargo original después de los rechazos. |
| Plazo | `InfraccionesServiceTest.elDiaDeLaNotificacionNoCuentaYElUltimoDiaEsDeUruguay`, `elUltimoDiaSePuedePresentarHastaLas2359`, `conElPlazoVencidoSeRechazaYElCasoNoCambia`, `unCasoDetectadoHaceTiempoCuentaDesdeQueSeNotifico`, `elUltimoDiaDelPlazoElCasoNoSeVence`, `elPlazoConfiguradoSeAplicaAlDescargoYAlVencimiento` | Se conservan las fronteras y la fecha inicial. Se agregaron plazos de 3 y 15 días, CL-P08. |
| Vencimiento | `InfraccionesServiceTest.alVencerElPlazoElCasoPasaAEnDescargoUnaSolaVezYQuedaEnLaAuditoria`, `unCasoConDescargoNoSeVence`, `elVencimientoNoModificaOtrosEstados`, `elVencimientoIgnoraUnCasoInexistente` | Se conserva el vencimiento válido y su repetición. Se completan los estados que no corresponden y el caso inexistente, CL-G13. |
| Resolución | `GestionCasosServiceTest.confirmarRegistraResolucionFundamentoYAuditoria`, `desestimarDejaElCasoDesestimado` | Se conservan como pruebas locales. Los recorridos completos aplican las mismas comprobaciones de evidencia, fundamento, actor y auditoría a las dos decisiones. |
| Resoluciones rechazadas | `GestionCasosServiceTest.noSeResuelveUnCasoQueNoEstaEnDescargo`, `unCasoResueltoNoSePuedeVolverAResolver`, `datosInvalidosNoModificanElCaso`, `quienNoEsFuncionarioNoAccedeANingunaOperacion` | Se conservan las restricciones. La segunda resolución ahora cubre ambos estados finales y ambas decisiones. Las entradas inválidas se prueban por separado y se conserva la comprobación de informar juntos los errores. |
| Auditoría | `GestionCasosServiceTest.laPistaDeAuditoriaNoSePuedeBorrar`, `CasoYAuditoriaTest.laAuditoriaDeCasosEsDeSoloAgregado` | Rechazo de borrado por JPA y protección de la entidad. Conservar; no equivalen a demostrar la secuencia de todo el trámite ni la protección de cualquier acceso SQL. |
| Ejecución del vencimiento | `VencimientoDescargoJobTest.correTodosLosDiasALas0005DeUruguayYUnaVezAlArrancar`, `siUnCasoFallaSigueConLosDemas`, `losQueYaNoCorrespondenNoSeCuentan` | Programación y coordinación con dobles de prueba. Conservar; no acreditan un temporizador ni locks ejecutándose realmente en WildFly/PostgreSQL. |
| Notificación desde fiscalización | `CaseAssignerTest.eventoPuedeRespaldarDistintasInfracciones` | Comprueba la coordinación con un almacenamiento falso. Conservar la comprobación de orquestación; no tomar el falso como prueba de persistencia o del ciclo administrativo real. |
| Recorridos CL-R01 a CL-R04 | `CicloVidaCasosFiscalizacionTest.cicloCompletoConservaEvidenciaYAuditaCadaTransicion` | Nueva suite parametrizada en los cuatro recorridos completos sobre el mismo caso. |

Archivos revisados:

- [CasoFiscalizacionTest.java](/home/pis/Desktop/tse_lab/TSE_CENTRAL/componente_central/backend/Modelo/src/test/java/tse/lab/model/CasoFiscalizacionTest.java).
- [CasoYAuditoriaTest.java](/home/pis/Desktop/tse_lab/TSE_CENTRAL/componente_central/backend/Modelo/src/test/java/tse/lab/model/CasoYAuditoriaTest.java).
- [CasosAutomaticosServiceTest.java](/home/pis/Desktop/tse_lab/TSE_CENTRAL/componente_central/backend/Frontofficefuncionarios/ejb/src/test/java/tse/lab/frontofficefuncionarios/service/gestioncasos/CasosAutomaticosServiceTest.java).
- [GestionCasosServiceTest.java](/home/pis/Desktop/tse_lab/TSE_CENTRAL/componente_central/backend/Frontofficefuncionarios/ejb/src/test/java/tse/lab/frontofficefuncionarios/service/gestioncasos/GestionCasosServiceTest.java).
- [InfraccionesServiceTest.java](/home/pis/Desktop/tse_lab/TSE_CENTRAL/componente_central/backend/Frontofficeciudadanos/ejb/src/test/java/tse/lab/frontofficeciudadanos/service/infraccionesydescargos/InfraccionesServiceTest.java).
- [VencimientoDescargoJobTest.java](/home/pis/Desktop/tse_lab/TSE_CENTRAL/componente_central/backend/Frontofficeciudadanos/ejb/src/test/java/tse/lab/frontofficeciudadanos/service/infraccionesydescargos/VencimientoDescargoJobTest.java).
- [CaseAssignerTest.java](/home/pis/Desktop/tse_lab/TSE_CENTRAL/componente_central/backend/Fiscalizacionautomatica/src/test/java/tse/lab/fiscalizacionautomatica/ruleengine/cases/CaseAssignerTest.java).

### 4.6. Cómo se organizará la reformulación

1. Mantener una prueba pequeña del modelo para los 25 pares de estados.
2. Reutilizar las pruebas enfocadas en cada operación y completar las combinaciones faltantes de las tablas anteriores. Cada rechazo compara con el estado persistido previo.
3. Incorporar una suite de recorridos completos para CL-R01 a CL-R04. No duplicar en ella todos los formatos inválidos ni todas las reglas de detección.
4. Comprobar en esos recorridos lo que ve cada actor, la evidencia conservada, el descargo o su ausencia, la resolución y toda la secuencia de auditoría.
5. Mantener separadas las pruebas de coordinación del job y de notificación desde fiscalización; comprobar que su comportamiento coincide con los servicios reales que invocan.
6. Ejecutar las pruebas relevantes y registrar un comando reproducible desde los archivos versionados, sus requisitos y el resultado. La ejecución no debe depender de un POM guardado solamente en `/tmp` ni de una base personal previamente preparada.

La suite completa podrá usar una base de prueba con servicios y DAOs reales. Las actuales usan H2 temporal en memoria: las filas se crean para cada prueba y se eliminan al finalizar. Es adecuado para verificar este trámite y sus relaciones persistidas. No demuestra que una consulta específica de PostgreSQL, un bloqueo concurrente o las transacciones del contenedor se comporten igual.

Las pruebas del modelo no necesitan base de datos. Las pruebas del job pueden usar dobles para provocar fallos y observar la coordinación. Si se evalúan garantías propias de PostgreSQL o WildFly/JTA, se deberá ejecutar una prueba de integración en ese entorno y distinguir sus resultados de los de H2.

### 4.7. Observaciones que no se deben convertir en resultados esperados por accidente

- La prueba que llamaba «resuelto» a un caso preparado en `EN_DESCARGO` se reformuló en `CasosAutomaticosServiceTest.reprocesarGpsConservaSuCasoInclusoSiEstaResuelto`: ahora verifica `EN_DESCARGO` y los dos estados finales, con persistencia real sobre H2.
- Se retiró de `CaseAssignerTest` el `FakeStore` que trataba `EN_DESCARGO` como cierre. Esa clase prueba la orquestación mediante Mockito; la acumulación, las reentregas y la separación después de una resolución se comprueban en `CasosAutomaticosServiceTest`, usando el servicio y sus DAOs reales.
- Las consultas de casos de funcionarios revisadas escriben logs; la resolución inserta un `RegistroAuditoriaCaso`. La letra habla de toda acción del funcionario. La cobertura de la resolución no permite declarar cubierta automáticamente la auditoría de las consultas. Esa diferencia queda identificada para acordar su alcance antes de fijar una prueba sobre ella.
- La suite de recorridos administrativos, al invocar explícitamente la notificación, no demuestra por sí sola que el motor la desencadene automáticamente ni que el procesamiento sea asincrónico. Esas garantías se verifican en la coordinación de fiscalización y en el bloque de eventos.
- El fallback actual a la fecha de detección cuando falta la auditoría de notificación tiene una prueba propia. No sustituye la comprobación de que una notificación normal genere su registro y que el plazo se cuente desde él.

### 4.8. Criterio de finalización de esta etapa

El diseño del ciclo queda delimitado por CL-R01–CL-R04, la matriz del modelo, las fronteras del plazo y los rechazos identificados. Su implementación se considerará demostrada cuando los recorridos y las reglas se vinculen a pruebas ejecutadas, las combinaciones faltantes estén cubiertas y las comprobaciones incluyan persistencia, auditoría y conservación de evidencia.

Un fallo que contradiga el comportamiento confirmado se reportará como una diferencia de implementación; no se cambiará silenciosamente el resultado esperado para mantener las pruebas en verde. El estado de las pruebas y las limitaciones del entorno se reportarán por separado del porcentaje global de cobertura.

## 5. Implementación del ciclo y explicación de las pruebas

### 5.1. Recorridos completos: CL-R01 a CL-R04

Archivo nuevo: [CicloVidaCasosFiscalizacionTest.java](/home/pis/Desktop/tse_lab/TSE_CENTRAL/componente_central/backend/Frontofficefuncionarios/ejb/src/test/java/tse/lab/frontofficefuncionarios/service/gestioncasos/CicloVidaCasosFiscalizacionTest.java).

Un método parametrizado se ejecuta cuatro veces, una por fila de la sección 4.2. Cada ejecución parte de una base vacía y crea sus propios datos: empresa, responsable, otra empresa con su responsable, funcionario, vehículo, Guía, balanza y pesaje.

Luego ejecuta el trámite real a través de los tres servicios. Comprueba en cada etapa el caso y su pesaje guardados, la visibilidad para la empresa y la auditoría. Para simular el paso de los días se controla el reloj de `InfraccionesService`; no se altera el estado por SQL ni se espera tiempo real. La fecha límite se ancla a la notificación realmente producida por el servicio; las pruebas de fechas concretas de la sección 4.3 verifican por separado la regla del cálculo.

En la rama con descargo, verifica que el funcionario vea el texto y el presentante. En la rama por vencimiento, verifica que no se cree un descargo. En ambas, la resolución debe tener fundamento, funcionario y fecha, y ser consultable por los dos actores. El final tiene exactamente un caso y una resolución, y cero o un descargo según el recorrido.

La auditoría se compara por valores, no solo por cantidad: cada fila tiene identidad, caso, acción, actor, estado anterior y nuevo, fundamento, fecha y correlación. Las filas de etapas anteriores deben permanecer idénticas al agregar las siguientes.

### 5.2. Reglas de presentación y vencimiento

`InfraccionesServiceTest` conserva las pruebas del responsable, notificaciones y plazos, y completa estos grupos:

| Grupo | Qué comprueba |
| --- | --- |
| CL-P01 a CL-P07 | Día inicial, zona de Uruguay, último segundo permitido, primer día vencido, fecha de notificación frente a detección, descargo ya presentado y repetición del vencimiento. Se reutilizan las pruebas existentes. |
| CL-P08 | Con plazos de 3 y 15 días, prueba el último segundo permitido y el primer instante fuera del plazo. Verifica el vencimiento mostrado, la presentación efectiva y el procesamiento del vencimiento. El plazo se suministra al constructor de prueba; no se está probando la lectura de variables de entorno. |
| CL-G03 | Un segundo descargo se rechaza conservando el identificador y el texto del primero, el estado y los registros de auditoría. |
| CL-G04 | El descargo tardío se rechaza tanto antes como después del procesamiento del vencimiento. |
| CL-G05 | En ambos estados finales, un caso sin descargo no puede reabrirse mediante una presentación. |
| CL-G06 y CL-G07 | Ni un caso todavía detectado ni uno de otra empresa permiten la presentación; se conserva el caso. |
| CL-G11 | Rechazo de descripción nula, vacía, en blanco, superior al máximo y solicitud sin datos. Cada intento conserva el estado, el descargo y la auditoría previos. |
| CL-G13 | El vencimiento no actúa sobre los otros cuatro estados ni sobre un identificador inexistente. Las pruebas existentes cubren el plazo todavía vigente y el descargo ya presentado. |

Las pruebas locales de estados finales preparan ese estado como condición inicial. Su objetivo es verificar el rechazo de una operación. Los cuatro recorridos completos demuestran cómo se llega a esos estados usando los servicios.

### 5.3. Reglas de resolución y notificación

| Grupo | Qué comprueba |
| --- | --- |
| CL-G01 | Desde `DETECTADA` o `NOTIFICADA` se rechazan las dos decisiones de resolución. |
| CL-G02 | Se ejecuta una primera resolución real y luego se intenta confirmar o desestimar nuevamente. Hay cuatro combinaciones entre la decisión original y la nueva. Deben conservarse estado, resolución original, fundamento, descargo, pesaje y registros de auditoría. |
| CL-G08 | Quien no es funcionario no puede consultar ni resolver mediante las operaciones revisadas. Se conservan las pruebas existentes. |
| CL-G09 | Para cada decisión válida, se rechazan cuatro clases de fundamento: nulo, vacío, en blanco y superior al máximo. Son ocho ejecuciones. |
| CL-G10 | Una decisión inválida con un fundamento válido se rechaza por la decisión. También se mantiene la solicitud sin datos y la comprobación de que varios errores se informen juntos. |
| CL-G12 | Repetir la notificación desde `NOTIFICADA`, `EN_DESCARGO`, `CONFIRMADA` o `DESESTIMADA` conserva identidad, estado, pesaje y registros previos; no se duplica la notificación ni se retrocede. |

Las pruebas locales existentes de confirmación, desestimación, consulta de evidencia y pista de auditoría se mantienen. La prueba de borrado comprueba el rechazo mediante JPA; no representa una garantía contra SQL administrativo externo a la aplicación.

### 5.4. Modelo y coordinación del vencimiento

`CasoFiscalizacionTest` se conserva: son cuatro métodos de prueba. Uno recorre los 25 pares de estados; los otros verifican el estado inicial y su fecha, la marca de notificación vista y qué estado admite resolución. Por eso el informe cuenta cuatro pruebas aunque uno de los métodos examine 25 combinaciones.

`VencimientoDescargoJobTest` se conserva con sus diez pruebas. Verifica programación diaria e inicial, logs de programación y de una corrida vacía, desactivación, continuidad si falla un caso, exclusión de casos que ya no corresponden, adquisición y liberación del lock, omisión de una réplica mientras otra tiene el lock, omisión por indisponibilidad de la base y liberación del lock si falla la corrida. El servicio y el acceso JDBC se sustituyen por dobles: se prueba la coordinación y las llamadas esperadas, no un lock real de PostgreSQL ni el disparo de un timer del servidor.

### 5.5. Ejecución reproducible y alcance

Las pruebas son JUnit y Maven Surefire las descubre junto con las demás pruebas de cada módulo. Se ejecutan mediante el `clean verify` que ya utiliza el pipeline; no requieren un script ni una activación especial.

Para repetir localmente la selección concreta de seis clases validada en esta etapa, con Java 24 y Maven, desde la raíz del repositorio:

```bash
mvn -B -f pruebas/pom.xml \
    -pl :frontofficefuncionarios-ejb -am \
    -Dtest=CasoFiscalizacionTest,CasosAutomaticosServiceTest,InfraccionesServiceTest,VencimientoDescargoJobTest,GestionCasosServiceTest,CicloVidaCasosFiscalizacionTest \
    -Dsurefire.failIfNoSpecifiedTests=false \
    clean verify
```

El POM agregador [pruebas/pom.xml](/home/pis/Desktop/tse_lab/TSE_CENTRAL/pruebas/pom.xml) permite compilar las dependencias locales de esta selección desde el mismo checkout. Es una facilidad para esa ejecución local; las pruebas siguen perteneciendo a sus módulos habituales. El pipeline existente instala las dependencias y ejecuta Maven en esos módulos. Con las dependencias externas ya descargadas se puede agregar `-o` al comando anterior.

La dependencia adicional del EJB completo de Ciudadanos tiene alcance `test` en el POM de Funcionarios. Permite invocar sus operaciones reales de descargo y vencimiento durante la prueba. No se cambió código de producción ni configuración de la base de la aplicación.

Los tests de servicios usan bases H2 temporales en memoria con creación y eliminación de tablas. El callback de logging de auditoría posterior al commit usa un doble al no existir JTA; las filas de auditoría sí se persisten y se consultan realmente. Los rechazos que necesitan rollback lo ejecutan mediante las transacciones locales del soporte de pruebas.

En la primera etapa se ejecutó toda `CasosAutomaticosServiceTest` como regresión del servicio modificado, incluyendo pruebas previas de GPS, circulación y evidencias. La sección 6 registra la reformulación posterior del bloque de tracking y las comprobaciones agregadas en esa clase.

Tampoco se acreditan con esta ejecución la entrega de mensajes, la sincronización móvil, la asincronía real, el disparo del temporizador, la concurrencia en PostgreSQL ni las transacciones de WildFly. La diferencia sobre auditoría de consultas sigue identificada; el ciclo implementado comprueba las acciones que cambian el caso.

### 5.6. Resultado verificado de la primera etapa

El 10/11/2026 se ejecutó mediante Maven `clean verify` la selección de seis clases indicada arriba, en modo offline, con Java 24 y Maven 3.6.3. Resultado: **BUILD SUCCESS**, 137 ejecuciones, cero fallos, cero errores y cero omitidas. El envoltorio shell usado inicialmente para invocar Maven fue eliminado; no forma parte del mecanismo de pruebas.

| Clase | Ejecuciones | Papel en esta etapa |
| --- | ---: | --- |
| `CasoFiscalizacionTest` | 4 | Modelo de estados existente, conservado. Uno de sus métodos verifica 25 pares. |
| `CicloVidaCasosFiscalizacionTest` | 4 | Recorridos completos nuevos, CL-R01 a CL-R04. |
| `InfraccionesServiceTest` | 32 | Presentación, visibilidad, notificaciones y plazo; pruebas conservadas, reformuladas y agregadas. |
| `GestionCasosServiceTest` | 34 | Consultas, resolución, rechazos y auditoría; pruebas conservadas y reformuladas. |
| `CasosAutomaticosServiceTest` | 53 | Registro de casos y evidencia, notificación y regresiones previas del servicio. |
| `VencimientoDescargoJobTest` | 10 | Coordinación existente del procesamiento de vencimientos, conservada. |
| **Total** | **137** | Incluye pruebas anteriores; no son 137 pruebas nuevas. |

Los informes JUnit se generan en `target/surefire-reports` de Modelo y de los EJB de Ciudadanos y Funcionarios. Los informes JaCoCo generados por esta selección no constituyen una medición global del sistema. La cobertura global se evaluará con el alcance completo del set.

## 6. Tracking: procesamiento, reintentos e idempotencia

### 6.1. Alcance acordado

Se utilizan JUnit, Mockito, el almacenamiento en memoria ya existente para las pruebas de tracking y H2 para las operaciones persistidas de casos. No se agregan Testcontainers, servidores ni scripts de ejecución. El requisito de idempotencia se distribuye entre recepción, procesamiento y operaciones sobre los casos; una prueba del asignador no pretende demostrar el comportamiento de una base de datos.

La identidad del hecho es matrícula y fecha de medición. El identificador del mensaje y el identificador externo se conservan para trazabilidad, pero cambiarlos no convierte un GPS repetido en un hecho nuevo. Otro instante de medición sí representa otro evento aunque reutilice un identificador externo.

### 6.2. Matriz de escenarios y pruebas

| ID | Escenario y resultado esperado | Pruebas que lo demuestran |
| --- | --- | --- |
| TR-R01 | Evento válido: conserva campos, instante de medición y se entrega a detección. | `TrackingEventReceiverTest.savesEventWithMessageDataAndHandsItToTheDetection`, `readsDatesInUruguayTime`. |
| TR-R02 | Reentrega con igual matrícula e instante: se conserva el original y se despacha una sola vez, incluso si cambian los identificadores de transporte. | `TrackingEventReceiverTest.discardsRedeliveryOfSameEventWithoutDetectingItAgain`, `sameLicensePlateAndTimeIsTheSameEventEvenWithAnotherExternalId`, `externalIdDoesNotIdentifyEvent`. |
| TR-R03 | Dos receptores concurrentes para el mismo hecho: un resultado guardado, otro duplicado y un único despacho. | `TrackingEventReceiverTest.dosReceptoresConcurrentesDespachanUnaSolaVezElMismoHecho`. El DAO de esta prueba es el doble concurrente en memoria. |
| TR-F01 | Se guarda el evento y falla el despacho. La reentrega resulta duplicada; la recuperación encuentra el pendiente original y lo procesa. | `TrackingDetectionWorkerTest.guardadoAntesDeFallarElDespachoSeRecuperaAunqueLaReentregaSeaDuplicada`. Usa receptor y worker reales, con DAO en memoria. |
| TR-F02 | Falla la asignación o la marca final: el evento no se marca detectado. Otro worker lo recupera y procesa conservando su identidad. | `TrackingDetectionWorkerTest.falloDeProcesamientoSeReintentaSinPerderLaIdentidad`, dos ejecuciones. |
| TR-F03 | Cola llena: el evento rechazado por la cola permanece pendiente y se procesa al recuperar. | `TrackingDetectionWorkerTest.colaLlenaDejaElEventoPendienteYLaRecuperacionLoProcesa`. |
| TR-F04 | Falla la consulta de pendientes: la siguiente recuperación sigue funcionando. | `TrackingDetectionWorkerTest.falloAlConsultarPendientesNoImpideLaSiguienteRecuperacion`. |
| TR-Q01 | Mientras un evento está en curso, otra entrega o la recuperación no lo encolan nuevamente en ese worker. | `TrackingDetectionWorkerTest.eventoEnCursoNoSeEncolaOtraVezPorEntregaNiPorRecuperacion`. La clave incluye matrícula e instante, no el identificador de transporte. |
| TR-P01 | Un evento tiene varias infracciones: se solicitan todas. Si falla una después de otra notificada, el reintento vuelve a registrar y notifica únicamente lo pendiente. | `CaseAssignerTest.eventoPuedeRespaldarDistintasInfracciones`, `reintentoTrasFalloParcialCompletaSituacionPendiente`. Las pruebas del servicio con H2 comprueban por separado que repetir el registro conserve casos y evidencias. |
| TR-P02 | La notificación falla: el asignador propaga el error para que el evento permanezca pendiente y pueda reintentarse. | `CaseAssignerTest.falloDeNotificacionSePropagaParaReintentar`. |
| TR-N01 | Solo se solicita notificación si el servicio devuelve `DETECTADA`; se respetan los otros cuatro estados y la circulación todavía pendiente de movimiento. | `CaseAssignerTest.soloNotificaCasosDetectados`, cinco ejecuciones, y `unaCirculacionPendienteNoSeNotifica`. |
| TR-C01 | Reprocesar un GPS conserva su caso, evidencia y auditoría, incluso en `EN_DESCARGO`, `CONFIRMADA` o `DESESTIMADA`. | `CasosAutomaticosServiceTest.reprocesarGpsConservaSuCasoInclusoSiEstaResuelto`, tres ejecuciones sobre H2. |
| TR-C02 | Un GPS nuevo válido después de cualquiera de las dos resoluciones crea otro caso. Repetir un GPS anterior sigue devolviendo el caso anterior. | `CasosAutomaticosServiceTest.gpsNuevoTrasResolucionCreaOtroCasoYLaReentregaConservaElAnterior`, dos ejecuciones sobre H2. Verifica claves separadas, estados, auditoría anterior y períodos de circulación. |
| TR-C03 | Mientras el caso sin Guía no esté resuelto, los GPS nuevos siguen acumulándose, incluso entre viajes y estando en descargo. El mismo GPS puede respaldar infracciones diferentes sin repetirse dentro de cada caso. | Pruebas conservadas de `CasosAutomaticosServiceTest`: `variosViajesTrasDiasSinGpsSiguenAcumulandoEvidenciaEnElMismoCaso`, `nuevosViajesSinGuiaSeAgreganAlMismoCasoAunqueEsteEnDescargo`, `variasInfraccionesCompartenGpsSinDuplicarloEnCadaCaso`. |
| TR-T01 | El historial y el desvío usan el tiempo de medición y la Guía histórica, no el orden de recepción ni solo el estado actual del viaje. | Pruebas conservadas de `HistorialGpsServiceTest`, `ContextoEventoSourceTest` y `RouteDeviationRuleTest`. |
| TR-M01 | El DAO solicita índice único por matrícula/instante e índice parcial de pendientes; distingue duplicado de otros errores al guardar individualmente o en lotes. | `MongoTrackingEventDAOTest.solicitaIndiceUnicoPorHechoEIndiceParcialDePendientes`, `unDuplicadoEnInsercionIndividualSeInformaSinOcultarOtrosErrores` y pruebas de lotes conservadas. La colección MongoDB es un mock. |
| TR-A01 | El consumidor confirma mensajes guardados o duplicados, descarta inválidos y solicita reencolar el lote cuando falla la recepción. | Ocho pruebas conservadas de `TrackingConsumerTest`, usando un canal RabbitMQ simulado. |

### 6.3. Criterio de reformulación

El antiguo `FakeStore` del asignador contenía una segunda implementación de las reglas de casos y una interpretación desactualizada de `EN_DESCARGO`. Se eliminó. `CaseAssignerTest` comprueba ahora las operaciones y los argumentos solicitados al servicio, los errores propagados y cuándo notifica. Las pruebas de servicio con H2 comprueban la identidad del caso y su evidencia guardada.

Los fallos del worker se provocan en puntos definidos. La ejecución concurrente se coordina con latches y esperas acotadas; no utiliza `Thread.sleep`. La prueba de saturación mantiene una evaluación bloqueada, llena la cola y comprueba que el evento restante se recupere después.

No se cambian reglas de producción para que una prueba pase. En las pruebas locales de reentrega se prepara un caso en el estado que interesa; los recorridos de la sección 5 ya comprueban la llegada a los estados finales mediante las operaciones de negocio.

### 6.4. Ejecución y límites

Las clases se descubren con el Maven habitual de Fiscalización y Funcionarios y se ejecutan en sus pipelines existentes. Para repetir la selección del bloque desde la raíz del checkout:

```bash
mvn -B -f pruebas/pom.xml \
    -pl :fiscalizacionautomatica,:frontofficefuncionarios-ejb -am \
    -Dtest=TrackingDetectionWorkerTest,TrackingEventReceiverTest,TrackingConsumerTest,CaseAssignerTest,MongoTrackingEventDAOTest,CasosAutomaticosServiceTest,HistorialGpsServiceTest,ContextoEventoSourceTest,RouteDeviationRuleTest \
    -Dsurefire.failIfNoSpecifiedTests=false \
    verify
```

La idempotencia se verifica como contrato de la lógica y como resultado persistido de las operaciones de casos sobre H2. Los mocks del driver verifican qué se solicita a MongoDB y cómo se interpretan sus errores; no ejecutan un índice real. La prueba del receptor concurrente usa un DAO en memoria y la del worker comprueba exclusión de un evento dentro de una instancia. No constituyen una prueba de concurrencia entre pods sobre MongoDB/PostgreSQL ni de redelivery de un broker real. Se mantiene ese límite sin ampliar la infraestructura de pruebas, según lo acordado.

La sincronización móvil permanece como el siguiente bloque pendiente. El porcentaje aproximado del 80 % corresponde al conjunto de la lógica y no se deduce de esta selección de pruebas.

### 6.5. Resultado verificado del bloque de tracking

El 10/11/2026 se repitió la selección de la sección 6.4 con Java 24 y Maven 3.6.3, agregando `-o` para utilizar las dependencias ya descargadas. Resultado: **BUILD SUCCESS**, 159 ejecuciones, cero fallos, cero errores y cero omitidas.

| Clase | Ejecuciones |
| --- | ---: |
| `TrackingEventReceiverTest` | 19 |
| `TrackingConsumerTest` | 8 |
| `MongoTrackingEventDAOTest` | 10 |
| `TrackingDetectionWorkerTest` | 11 |
| `CaseAssignerTest` | 19 |
| `HistorialGpsServiceTest` | 5 |
| `ContextoEventoSourceTest` | 5 |
| `RouteDeviationRuleTest` | 25 |
| `CasosAutomaticosServiceTest` | 57 |
| **Total** | **159** |

Incluye pruebas conservadas, reformuladas y agregadas; no son 159 pruebas nuevas. Parte de la selección se solapa con la primera etapa: los totales de las dos ejecuciones no se suman como pruebas distintas. La clase de casos pasó de 53 a 57 ejecuciones por las combinaciones de reentrega y GPS nuevo posteriores a la resolución.
