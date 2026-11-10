-- Datos de prueba SOLO PARA DESARROLLO. Hibernate los carga despues del drop-and-create de
-- persistence.xml (jakarta.persistence.sql-load-script-source), o sea en cada deploy.
-- Una sentencia por linea. Datos ficticios salvo la cedula del responsable, que es la del
-- integrante que prueba el login con gub.uy testing.
--
-- Catalogo de localidades del entorno academico; coordenadas centrales WGS84 aproximadas.
-- Son puntos de referencia viales a verificar con OSRM; no representan domicilios ni depositos.
INSERT INTO localidades (id, nombre, departamento, latitud, longitud) VALUES (1, 'Montevideo', 'Montevideo', -34.9033, -56.1882);
INSERT INTO localidades (id, nombre, departamento, latitud, longitud) VALUES (2, 'Paysandu', 'Paysandu', -32.3171, -58.0807);
INSERT INTO localidades (id, nombre, departamento, latitud, longitud) VALUES (3, 'Rivera', 'Rivera', -30.9053, -55.5508);
INSERT INTO localidades (id, nombre, departamento, latitud, longitud) VALUES (4, 'Maldonado', 'Maldonado', -34.8979, -54.9502);
INSERT INTO localidades (id, nombre, departamento, latitud, longitud) VALUES (5, 'Tacuarembo', 'Tacuarembo', -31.7188, -55.9793);
INSERT INTO localidades (id, nombre, departamento, latitud, longitud) VALUES (6, 'Durazno', 'Durazno', -33.3589, -56.4992);
INSERT INTO localidades (id, nombre, departamento, latitud, longitud) VALUES (7, 'Mercedes', 'Soriano', -33.2524, -58.0305);
INSERT INTO localidades (id, nombre, departamento, latitud, longitud) VALUES (8, 'Nueva Palmira', 'Colonia', -33.87031, -58.41176);
INSERT INTO localidades (id, nombre, departamento, latitud, longitud) VALUES (13, 'Dolores', 'Soriano', -33.53009, -58.21701);
-- Empresa habilitada; la alta real la hace un funcionario del MTOP (frontoffice de funcionarios).
INSERT INTO empresas (nroEmpresa, nombrePublico, razonSocial, direccion, habilitada, fechaAlta, version) VALUES (1, 'Transportes del Este', 'Transportes del Este S.A.', 'Ruta 8 km 29, Pando, Canelones', true, CURRENT_DATE - 400, 0);
-- DEMO / Empresas: contraste con empresa no habilitada (sin identidad gub.uy artificial).
INSERT INTO empresas (nroEmpresa, nombrePublico, razonSocial, direccion, habilitada, fechaAlta, version) VALUES (2, 'Logistica del Litoral', 'Logistica del Litoral S.R.L.', 'Ruta 3 km 390, Paysandu', false , CURRENT_DATE - 300, 0);
-- Ciudadanos que tienen un rol. Quedan pendientes sin cuenta gub.uy: su primer ingreso los vincula y valida
-- contra la PDI (CU-01 FA2). Los roles (responsables, choferes) los referencian por ci.
INSERT INTO ciudadanos (ci, estado, creado) VALUES ('54074380', 'PENDIENTE_VALIDACION', CURRENT_TIMESTAMP);
-- Responsable de la empresa 1.
INSERT INTO responsables (ci, nro_empresa) VALUES ('54074380', 1);
-- Vehiculos de la empresa (matricula normalizada, como la mandan balanzas y tracking). SBA1234 queda
-- habilitado (permiso e ITV vigentes); SAB5678 sin permiso ni ITV, para ver el caso no habilitado.
INSERT INTO vehiculos (matricula, marca, modelo, peso, capacidadCargaKg, activo, nro_empresa) VALUES ('SBA1234', 'Scania', 'R 450', 9000, 26000, true, 1);
INSERT INTO vehiculos (matricula, marca, modelo, peso, capacidadCargaKg, activo, nro_empresa) VALUES ('SAB5678', 'Volvo', 'FH 500', 9500, 28000, true, 1);
-- DEMO / Gestion de vehiculos: SDE3000 activo y libre para asignar una guia; SDF3000 baja logica.
INSERT INTO vehiculos (matricula, marca, modelo, peso, capacidadCargaKg, activo, nro_empresa) VALUES ('SDE3000', 'Iveco', 'Daily', 6500, 14000, true, 1);
INSERT INTO vehiculos (matricula, marca, modelo, peso, capacidadCargaKg, activo, nro_empresa) VALUES ('SDF3000', 'Mercedes-Benz', 'Atego', 7200, 18000, false, 1);
-- Vehiculo ajeno para comprobar aislamiento; sin permiso/ITV ni viaje activo.
INSERT INTO vehiculos (matricula, marca, modelo, peso, capacidadCargaKg, activo, nro_empresa) VALUES ('SCL2001', 'Iveco', 'Stralis', 8000, 22000, true, 2);
INSERT INTO permisos_circulacion (nro, fechaEmision, fechaVencimiento, matricula) VALUES ('PNC-0001', CURRENT_DATE - 30, CURRENT_DATE + 335, 'SBA1234');
INSERT INTO itv (fechaEmision, fechaVencimiento, matricula) VALUES (CURRENT_DATE - 30, CURRENT_DATE + 180, 'SBA1234');
-- DEMO / ITV y permisos: SDE3000 habilitado y disponible para asignacion. SAB5678 sigue sin habilitacion.
INSERT INTO permisos_circulacion (nro, fechaEmision, fechaVencimiento, matricula) VALUES ('PNC-SDE-01', CURRENT_DATE - 50, CURRENT_DATE + 250, 'SDE3000');
INSERT INTO itv (fechaEmision, fechaVencimiento, matricula) VALUES (CURRENT_DATE - 50, CURRENT_DATE + 250, 'SDE3000');
-- App movil del chofer: Bryan (56087406, existe en la PDI) y el propio responsable (54074380, para probar con
-- su usuario de gub.uy testing) como choferes habilitados de la empresa 1, cada uno con una guia ASIGNADA.
INSERT INTO ciudadanos (ci, estado, creado) VALUES ('56087406', 'VALIDADO', CURRENT_TIMESTAMP);
INSERT INTO choferes (ci, nro_empresa, estado, fechaAlta) VALUES ('56087406', 1, 'HABILITADO', CURRENT_TIMESTAMP);
INSERT INTO choferes (ci, nro_empresa, estado, fechaAlta) VALUES ('54074380', 1, 'HABILITADO', CURRENT_TIMESTAMP);
INSERT INTO guias (id, rubroCliente, tipoCarga, pesoCargaKg, fecha, origen, destino, nro_empresa, id_vehiculo, id_chofer, estado, fechaAsignacion, version) VALUES ('GV-MOVIL001', 'Agropecuario', 'Granos a granel', 18000, CURRENT_DATE, 'Montevideo', 'Paysandu', 1, 'SBA1234', (SELECT id FROM choferes WHERE ci = '56087406' AND nro_empresa = 1), 'ASIGNADA', CURRENT_TIMESTAMP, 0);
INSERT INTO guias (id, rubroCliente, tipoCarga, pesoCargaKg, fecha, origen, destino, nro_empresa, id_vehiculo, id_chofer, estado, fechaAsignacion, version) VALUES ('GV-MOVIL002', 'Forestal', 'Rollizos de eucalipto', 24000, CURRENT_DATE, 'Rivera', 'Montevideo', 1, 'SAB5678', (SELECT id FROM choferes WHERE ci = '54074380' AND nro_empresa = 1), 'ASIGNADA', CURRENT_TIMESTAMP, 0);
-- DEMO / Chofer aun no validado: debe quedar fuera de las opciones de asignacion.
INSERT INTO ciudadanos (ci, estado, creado) VALUES ('22222222', 'PENDIENTE_VALIDACION', CURRENT_TIMESTAMP);
INSERT INTO choferes (ci, nro_empresa, estado, fechaAlta) VALUES ('22222222', 1, 'PENDIENTE_VALIDACION', CURRENT_TIMESTAMP);
-- DEMO / Guia nueva, vehiculo libre SDE3000; nunca se asigna inicialmente.
INSERT INTO guias (id, rubroCliente, tipoCarga, pesoCargaKg, fecha, origen, destino, nro_empresa, estado, version) VALUES ('GV-DEMO-ASIGNAR', 'Construcción', 'Materiales de obra', 12000, CURRENT_DATE + 1, 'Montevideo', 'Maldonado', 1, 'SIN_ASIGNAR', 0);
-- DEMO / Guia cancelada e historial de modificaciones, sin interferir con las guias de la app.
INSERT INTO guias (id, rubroCliente, tipoCarga, pesoCargaKg, fecha, origen, destino, nro_empresa, id_vehiculo, id_chofer, estado, fechaAsignacion, version) VALUES ('GV-DEMO-CANCELADA', 'Forestal', 'Madera aserrada', 12000, CURRENT_DATE - 2, 'Tacuarembo', 'Durazno', 1, 'SAB5678', (SELECT id FROM choferes WHERE ci = '54074380' AND nro_empresa = 1), 'CANCELADA', CURRENT_TIMESTAMP - INTERVAL '3 days', 1);
INSERT INTO modificaciones_guia (id_guia, tipo, ciResponsable, fecha, cambios, motivo, versionAnterior, versionNueva) VALUES ('GV-DEMO-CANCELADA', 'CANCELACION', '54074380', CURRENT_TIMESTAMP - INTERVAL '2 days', 'estado: ASIGNADA -> CANCELADA', 'El cliente suspendio el traslado.', 0, 1);
-- Nodo de balanza activo para el poller de fiscalizacion automatica: id_externo es el id de la balanza en la
-- API de balanzas (TSE_NODOS, balanza 4 "Ruta 8 - Km 150"); el id lo genera la base.
INSERT INTO nodos_perifericos (tipo, id_externo, nombre, ubicacion, activo) VALUES ('BALANZA', 4, 'Ruta 8 - Km 150', 'Ruta 8 - Km 150', true);
INSERT INTO nodos_balanza (id) SELECT id FROM nodos_perifericos WHERE tipo = 'BALANZA' AND id_externo = 4;
-- Fiscalizacion automatica (Viaje sin guia): vehiculos y GPS del nodo de tracking (TSE_NODOS: GPS 1 = SBC1234,
-- GPS 2 = STU5678; el GPS 3 no se carga, asi sus eventos se descartan). SBC1234 no tiene guia: sus eventos
-- deben detectar Viaje sin guia. STU5678 tiene una guia EN_CURSO, con un chofer ficticio propio para no sumarle
-- viajes a los choferes de la app movil: no debe detectar nada.
INSERT INTO vehiculos (matricula, marca, modelo, peso, capacidadCargaKg, nro_empresa) VALUES ('SBC1234', 'Mercedes-Benz', 'Actros', 8500, 25000, 1);
INSERT INTO vehiculos (matricula, marca, modelo, peso, capacidadCargaKg, nro_empresa) VALUES ('STU5678', 'Volvo', 'FH 460', 9200, 27000, 1);
-- DEMO / Diferencia entre vigencia y vencimiento para Gestion de Vehiculos.
-- SBC1234 no tiene viaje activo, y se conserva libre para generar un caso REAL de VIAJE_SIN_GUIA.
INSERT INTO permisos_circulacion (nro, fechaEmision, fechaVencimiento, matricula) VALUES ('PNC-SBC-01', CURRENT_DATE - 400, CURRENT_DATE - 5, 'SBC1234');
INSERT INTO itv (fechaEmision, fechaVencimiento, matricula) VALUES (CURRENT_DATE - 400, CURRENT_DATE - 5, 'SBC1234');
-- STU5678 tiene viaje activo: los pesajes por encima de 36200 kg activan la regla EXCESO_CARGA.
INSERT INTO permisos_circulacion (nro, fechaEmision, fechaVencimiento, matricula) VALUES ('PNC-STU-01', CURRENT_DATE - 100, CURRENT_DATE + 250, 'STU5678');
INSERT INTO itv (fechaEmision, fechaVencimiento, matricula) VALUES (CURRENT_DATE - 100, CURRENT_DATE + 250, 'STU5678');
INSERT INTO ciudadanos (ci, estado, creado) VALUES ('11111111', 'PENDIENTE_VALIDACION', CURRENT_TIMESTAMP);
INSERT INTO choferes (ci, nro_empresa, estado, fechaAlta) VALUES ('11111111', 1, 'HABILITADO', CURRENT_TIMESTAMP);
INSERT INTO guias (id, rubroCliente, tipoCarga, pesoCargaKg, fecha, origen, destino, nro_empresa, id_vehiculo, id_chofer, estado, fechaAsignacion, version) VALUES ('GV-FISC001', 'Agropecuario', 'Soja a granel', 20000, CURRENT_DATE, 'Mercedes', 'Nueva Palmira', 1, 'STU5678', (SELECT id FROM choferes WHERE ci = '11111111' AND nro_empresa = 1), 'EN_CURSO', CURRENT_TIMESTAMP, 0);
-- DEMO / Guia finalizada: sirve para mostrar historia sin hacer activo el viaje de SBC1234.
INSERT INTO guias (id, rubroCliente, tipoCarga, pesoCargaKg, fecha, origen, destino, nro_empresa, id_vehiculo, id_chofer, estado, fechaAsignacion, version) VALUES ('GV-DEMO-HIST-SBC', 'Agropecuario', 'Maíz a granel', 16000, CURRENT_DATE - 12, 'San Jose', 'Colonia', 1, 'SBC1234', (SELECT id FROM choferes WHERE ci = '11111111' AND nro_empresa = 1), 'FINALIZADA', CURRENT_TIMESTAMP - INTERVAL '13 days', 0);
-- Guia historica de SAB5678 para dos casos ya resueltos: no interfiere con detecciones en vivo.
INSERT INTO guias (id, rubroCliente, tipoCarga, pesoCargaKg, fecha, origen, destino, nro_empresa, id_vehiculo, id_chofer, estado, fechaAsignacion, version) VALUES ('GV-DEMO-HIST-SAB', 'Forestal', 'Rollizos', 24000, CURRENT_DATE - 5, 'Rivera', 'Tacuarembo', 1, 'SAB5678', (SELECT id FROM choferes WHERE ci = '11111111' AND nro_empresa = 1), 'FINALIZADA', CURRENT_TIMESTAMP - INTERVAL '6 days', 0);
-- DEMO / Eventos sinteticos de Mobile para guias historicas FINALIZADA.
-- Completa las ventanas temporales de asociacion GPS.
-- Idempotente: no duplica eventos INICIO/FIN ya aplicados.

WITH eventos_demo (
    idevento,
    id_guia,
    tipo,
    desplazamiento,
    estado_resultante
) AS (
    VALUES
    (
        'de000000-0000-4000-8000-000000000101',
        'GV-DEMO-HIST-SBC',
        'INICIO',
        INTERVAL '1 hour',
        'EN_CURSO'
    ),
    (
        'de000000-0000-4000-8000-000000000102',
        'GV-DEMO-HIST-SBC',
        'FIN',
        INTERVAL '1 day',
        'FINALIZADA'
    ),
    (
        'de000000-0000-4000-8000-000000000201',
        'GV-DEMO-HIST-SAB',
        'INICIO',
        INTERVAL '1 hour',
        'EN_CURSO'
    ),
    (
        'de000000-0000-4000-8000-000000000202',
        'GV-DEMO-HIST-SAB',
        'FIN',
        INTERVAL '1 day',
        'FINALIZADA'
    )
)
INSERT INTO eventos_viaje (
    idevento,
    id_guia,
    cichofer,
    tipo,
    fechahoradispositivo,
    fecharecepcion,
    descripcion,
    resultado,
    estadoguiaresultante
)
SELECT
    e.idevento,
    g.id,
    ch.ci,
    e.tipo,
    g.fechaAsignacion + e.desplazamiento,
    g.fechaAsignacion + e.desplazamiento + INTERVAL '1 minute',
    'DEMO: evento sintetico de Mobile para historial de viaje',
    'APLICADO',
    e.estado_resultante
FROM eventos_demo e
JOIN guias g ON g.id = e.id_guia
JOIN choferes ch ON ch.id = g.id_chofer
WHERE g.estado = 'FINALIZADA'
  AND g.fechaAsignacion IS NOT NULL
  AND NOT EXISTS (
      SELECT 1
      FROM eventos_viaje existente
      WHERE existente.id_guia = g.id
        AND existente.tipo = e.tipo
        AND existente.resultado = 'APLICADO'
  )
ON CONFLICT (idevento) DO NOTHING;

INSERT INTO nodos_perifericos (tipo, id_externo, nombre, ubicacion, activo) VALUES ('TRACKING', 1, 'GPS SBC1234', 'Vehiculo SBC1234', true);
INSERT INTO nodos_tracking (id) SELECT id FROM nodos_perifericos WHERE tipo = 'TRACKING' AND id_externo = 1;
INSERT INTO nodos_perifericos (tipo, id_externo, nombre, ubicacion, activo) VALUES ('TRACKING', 2, 'GPS STU5678', 'Vehiculo STU5678', true);
INSERT INTO nodos_tracking (id) SELECT id FROM nodos_perifericos WHERE tipo = 'TRACKING' AND id_externo = 2;
-- Administrador de prueba para el Backoffice.
-- CI: 99999999
-- Contrasena: AdminPrueba2026!
INSERT INTO usuarios_backoffice (ci, contrasena) VALUES ('99999999', 'pbkdf2-sha256:600000:XVb3i-u34NGRGh8VpALJ9A:PR0-5o08j6128R_8qPTGWP3rPuwuDBHRBrVpEloWM74');
INSERT INTO administradores (ci) VALUES ('99999999');
-- DEMO: rol Autoridad para verificar que autenticacion no concede permisos de administrador.
-- Se reutiliza el hash de prueba existente solo dentro del entorno de desarrollo.
INSERT INTO usuarios_backoffice (ci, contrasena) SELECT '88888888', contrasena FROM usuarios_backoffice WHERE ci = '99999999';
INSERT INTO autoridades (ci) VALUES ('88888888');
-- DEMO: autoridad recien dada de alta, con el cambio de contrasena obligatorio pendiente (misma contrasena de prueba).
INSERT INTO usuarios_backoffice (ci, contrasena, debe_cambiar_contrasena) SELECT '77777777', contrasena, TRUE FROM usuarios_backoffice WHERE ci = '99999999';
INSERT INTO autoridades (ci) VALUES ('77777777');
-- Frontoffice de funcionarios (CU Gestionar casos de fiscalizacion): el integrante de gub.uy testing tambien es
-- funcionario, y hay casos de fiscalizacion en distintos estados hasta que la fiscalizacion automatica los genere.
INSERT INTO funcionarios (ci, email) VALUES ('54074380', 'funcionario.prueba@mtop.gub.uy');
-- Gestion de empresas: ciudadano ficticio ya validado y sin ningun rol, para elegirlo como responsable de una
-- empresa sin mezclarlo con los datos de los otros casos de prueba
INSERT INTO ciudadanos (ci, primerNombre, primerApellido, nombres, apellidos, estado, creado, ultimaValidacion) VALUES ('44444444', 'Laura', 'Gomez', 'Laura', 'Gomez', 'VALIDADO', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
-- Balanza solo para la evidencia de los casos: inactiva, asi el poller no la consulta en la API de balanzas.
INSERT INTO nodos_perifericos (tipo, id_externo, nombre, ubicacion, activo) VALUES ('BALANZA', 1, 'Balanza Peaje Pando', 'Peaje Pando', false);
INSERT INTO nodos_balanza (id) SELECT id FROM nodos_perifericos WHERE tipo = 'BALANZA' AND id_externo = 1;
-- Casos historicos PREFABRICADOS para la bandeja; detectado = true evita reevaluar pesajes ya tratados.
INSERT INTO eventos_pesajes (matricula, fecha_hora, id_externo, peso, detectado, id_nodo_balanza) VALUES ('SBA1234', CURRENT_TIMESTAMP - INTERVAL '3 hours', 1001, 38500, true, (SELECT id FROM nodos_perifericos WHERE tipo = 'BALANZA' AND id_externo = 1));
INSERT INTO casos_fiscalizacion (descripcion, tipoCaso, estado, fechaDeteccion, id_guia, id_vehiculo, notificacion_vista) VALUES ('DEMO DESCARGO: pesaje de 38500 kg supera el maximo de 35000 kg', 'EXCESO_CARGA', 'EN_DESCARGO', CURRENT_TIMESTAMP - INTERVAL '2 hours 59 minutes', 'GV-MOVIL001', 'SBA1234', true);
INSERT INTO casos_eventos_pesajes (id_caso, matricula, fecha_hora) SELECT c.id, p.matricula, p.fecha_hora FROM casos_fiscalizacion c, eventos_pesajes p WHERE c.tipoCaso = 'EXCESO_CARGA' AND p.matricula = 'SBA1234';
INSERT INTO descargos (descripcion, tipoCaso, estado, fechaPresentacion, ciPresentante, id_caso) SELECT 'La balanza del peaje estaba descalibrada ese dia', 'EXCESO_CARGA', 'PRESENTADO', fechaDeteccion + INTERVAL '2 minutes', '54074380', id FROM casos_fiscalizacion WHERE tipoCaso = 'EXCESO_CARGA';
INSERT INTO casos_fiscalizacion (descripcion, tipoCaso, estado, fechaDeteccion, id_guia, id_vehiculo, notificacion_vista) VALUES ('DEMO DESVIO: ruta reportada se aparta del recorrido Rivera - Montevideo', 'DESVIO_RUTA', 'NOTIFICADA', CURRENT_TIMESTAMP - INTERVAL '1 hour', 'GV-MOVIL002', 'SAB5678', false);
INSERT INTO casos_fiscalizacion (descripcion, tipoCaso, estado, fechaDeteccion, id_guia, id_vehiculo, notificacion_vista) VALUES ('DEMO PREFABRICADA: actividad sin guia de SCL2001', 'VIAJE_SIN_GUIA', 'DETECTADA', CURRENT_TIMESTAMP - INTERVAL '8 hours', NULL, 'SCL2001', false);

-- DEMO / Auditoria coherente de los casos precargados, distinguida del log generado en vivo.
INSERT INTO auditoria_casos (id_caso, usuario, accion, estadoAnterior, estadoNuevo, fundamento, fechaHora, correlationId) SELECT id, 'SISTEMA', 'CREACION', NULL, 'DETECTADA', 'Caso precargado para demo', fechaDeteccion, 'seed-demo' FROM casos_fiscalizacion WHERE descripcion LIKE 'DEMO DESCARGO:%' OR descripcion LIKE 'DEMO DESVIO:%' OR descripcion LIKE 'DEMO PREFABRICADA:%';
INSERT INTO auditoria_casos (id_caso, usuario, accion, estadoAnterior, estadoNuevo, fundamento, fechaHora, correlationId) SELECT id, 'SISTEMA', 'CAMBIO_ESTADO', 'DETECTADA', 'NOTIFICADA', 'Notificacion de demostracion', fechaDeteccion + INTERVAL '1 minute', 'seed-demo' FROM casos_fiscalizacion WHERE descripcion LIKE 'DEMO DESCARGO:%' OR descripcion LIKE 'DEMO DESVIO:%';
INSERT INTO auditoria_casos (id_caso, usuario, accion, estadoAnterior, estadoNuevo, fundamento, fechaHora, correlationId) SELECT id, 'SISTEMA', 'CAMBIO_ESTADO', 'NOTIFICADA', 'EN_DESCARGO', 'Descargo de demostracion', fechaDeteccion + INTERVAL '2 minutes', 'seed-demo' FROM casos_fiscalizacion WHERE descripcion LIKE 'DEMO DESCARGO:%';
-- Caso ilustrativo de habilitaciones: la regla automatica aun no existe.
INSERT INTO casos_fiscalizacion (descripcion, tipoCaso, estado, fechaDeteccion, id_guia, id_vehiculo, notificacion_vista) VALUES ('DEMO HABILITACION: SBC1234 tiene permiso e ITV vencidos', 'VEHICULO_SIN_HABILITACION', 'DETECTADA', CURRENT_TIMESTAMP - INTERVAL '4 hours', 'GV-DEMO-HIST-SBC', 'SBC1234', false);
INSERT INTO auditoria_casos (id_caso, usuario, accion, estadoAnterior, estadoNuevo, fundamento, fechaHora, correlationId) SELECT id, 'SISTEMA', 'CREACION', NULL, 'DETECTADA', 'Caso precargado: regla no implementada', fechaDeteccion, 'seed-demo' FROM casos_fiscalizacion WHERE descripcion LIKE 'DEMO HABILITACION:%';
-- DEMO / Dos casos cerrados de EXCESO_CARGA; base para fundamentos y consulta de auditoria.
-- Se conserva la balanza 1 desactivada, de modo que el poller no reimporte estos hechos historicos.
INSERT INTO eventos_pesajes (matricula, fecha_hora, id_externo, peso, detectado, id_nodo_balanza) VALUES ('SAB5678', CURRENT_TIMESTAMP - INTERVAL '48 hours', 2001, 39500, true, (SELECT id FROM nodos_perifericos WHERE tipo = 'BALANZA' AND id_externo = 1));
INSERT INTO casos_fiscalizacion (descripcion, tipoCaso, estado, fechaDeteccion, id_guia, id_vehiculo, notificacion_vista) VALUES ('DEMO CONFIRMADA: exceso de 2000 kg en pesaje historico', 'EXCESO_CARGA', 'CONFIRMADA', CURRENT_TIMESTAMP - INTERVAL '47 hours', 'GV-DEMO-HIST-SAB', 'SAB5678', true);
INSERT INTO casos_eventos_pesajes (id_caso, matricula, fecha_hora) SELECT c.id, p.matricula, p.fecha_hora FROM casos_fiscalizacion c JOIN eventos_pesajes p ON p.id_externo = 2001 WHERE c.descripcion = 'DEMO CONFIRMADA: exceso de 2000 kg en pesaje historico';
INSERT INTO fundamentos_resolucion (descripcion, fechaHora, ci_funcionario, id_caso) SELECT 'Se confirma el exceso de 2000 kg respecto del maximo declarado.', CURRENT_TIMESTAMP - INTERVAL '46 hours', '54074380', id FROM casos_fiscalizacion WHERE descripcion = 'DEMO CONFIRMADA: exceso de 2000 kg en pesaje historico';
INSERT INTO eventos_pesajes (matricula, fecha_hora, id_externo, peso, detectado, id_nodo_balanza) VALUES ('SAB5678', CURRENT_TIMESTAMP - INTERVAL '24 hours', 2002, 37600, true, (SELECT id FROM nodos_perifericos WHERE tipo = 'BALANZA' AND id_externo = 1));
INSERT INTO casos_fiscalizacion (descripcion, tipoCaso, estado, fechaDeteccion, id_guia, id_vehiculo, notificacion_vista) VALUES ('DEMO DESESTIMADA: diferencia marginal de 100 kg', 'EXCESO_CARGA', 'DESESTIMADA', CURRENT_TIMESTAMP - INTERVAL '23 hours', 'GV-DEMO-HIST-SAB', 'SAB5678', true);
INSERT INTO casos_eventos_pesajes (id_caso, matricula, fecha_hora) SELECT c.id, p.matricula, p.fecha_hora FROM casos_fiscalizacion c JOIN eventos_pesajes p ON p.id_externo = 2002 WHERE c.descripcion = 'DEMO DESESTIMADA: diferencia marginal de 100 kg';
INSERT INTO fundamentos_resolucion (descripcion, fechaHora, ci_funcionario, id_caso) SELECT 'Se desestima al comprobar un error de calibracion de la balanza.', CURRENT_TIMESTAMP - INTERVAL '22 hours', '54074380', id FROM casos_fiscalizacion WHERE descripcion = 'DEMO DESESTIMADA: diferencia marginal de 100 kg';
INSERT INTO auditoria_casos (id_caso, usuario, accion, estadoAnterior, estadoNuevo, fundamento, fechaHora, correlationId) SELECT id, 'SISTEMA', 'CREACION', NULL, 'DETECTADA', 'Caso precargado', fechaDeteccion, 'seed-demo' FROM casos_fiscalizacion WHERE descripcion LIKE 'DEMO CONFIRMADA:%' OR descripcion LIKE 'DEMO DESESTIMADA:%';
INSERT INTO auditoria_casos (id_caso, usuario, accion, estadoAnterior, estadoNuevo, fundamento, fechaHora, correlationId) SELECT id, 'SISTEMA', 'CAMBIO_ESTADO', 'DETECTADA', 'NOTIFICADA', NULL, fechaDeteccion + INTERVAL '5 minutes', 'seed-demo' FROM casos_fiscalizacion WHERE descripcion LIKE 'DEMO CONFIRMADA:%' OR descripcion LIKE 'DEMO DESESTIMADA:%';
INSERT INTO auditoria_casos (id_caso, usuario, accion, estadoAnterior, estadoNuevo, fundamento, fechaHora, correlationId) SELECT id, 'SISTEMA', 'CAMBIO_ESTADO', 'NOTIFICADA', 'EN_DESCARGO', NULL, fechaDeteccion + INTERVAL '10 minutes', 'seed-demo' FROM casos_fiscalizacion WHERE descripcion LIKE 'DEMO CONFIRMADA:%' OR descripcion LIKE 'DEMO DESESTIMADA:%';
INSERT INTO auditoria_casos (id_caso, usuario, accion, estadoAnterior, estadoNuevo, fundamento, fechaHora, correlationId) SELECT c.id, '54074380', 'RESOLUCION', 'EN_DESCARGO', 'CONFIRMADA', f.descripcion, f.fechaHora, 'seed-demo' FROM casos_fiscalizacion c JOIN fundamentos_resolucion f ON f.id_caso = c.id WHERE c.descripcion LIKE 'DEMO CONFIRMADA:%';
INSERT INTO auditoria_casos (id_caso, usuario, accion, estadoAnterior, estadoNuevo, fundamento, fechaHora, correlationId) SELECT c.id, '54074380', 'RESOLUCION', 'EN_DESCARGO', 'DESESTIMADA', f.descripcion, f.fechaHora, 'seed-demo' FROM casos_fiscalizacion c JOIN fundamentos_resolucion f ON f.id_caso = c.id WHERE c.descripcion LIKE 'DEMO DESESTIMADA:%';
-- DEMO / Portal publico: tres empresas habilitadas mas, con un vehiculo habilitado y viajes FINALIZADOS cada una,
-- para que las estadisticas publicas junten el umbral de 3 empresas. Construccion es solo de la empresa 5: se
-- reserva, y con ella el rubro mas chico de los visibles (supresion complementaria): las dos van sumadas en "Otros rubros".
INSERT INTO empresas (nroEmpresa, nombrePublico, razonSocial, direccion, habilitada, fechaAlta, version) VALUES (3, 'Fletes del Norte', 'Fletes del Norte S.A.', 'Ruta 5 km 498, Rivera', true, CURRENT_DATE - 200, 0);
INSERT INTO empresas (nroEmpresa, nombrePublico, razonSocial, direccion, habilitada, fechaAlta, version) VALUES (4, 'Cargas del Sur', 'Cargas del Sur S.R.L.', 'Camino Maldonado 7500, Montevideo', true, CURRENT_DATE - 150, 0);
INSERT INTO empresas (nroEmpresa, nombrePublico, razonSocial, direccion, habilitada, fechaAlta, version) VALUES (5, 'Transporte Oriental', 'Transporte Oriental S.A.', 'Ruta 1 km 67, San Jose', true, CURRENT_DATE - 100, 0);
INSERT INTO vehiculos (matricula, marca, modelo, peso, capacidadCargaKg, activo, nro_empresa) VALUES ('SEM3001', 'Scania', 'G 410', 8800, 26000, true, 3);
INSERT INTO vehiculos (matricula, marca, modelo, peso, capacidadCargaKg, activo, nro_empresa) VALUES ('SEM4001', 'Volvo', 'FM 440', 9100, 27000, true, 4);
INSERT INTO vehiculos (matricula, marca, modelo, peso, capacidadCargaKg, activo, nro_empresa) VALUES ('SEM5001', 'Iveco', 'S-Way', 8700, 25000, true, 5);
INSERT INTO permisos_circulacion (nro, fechaEmision, fechaVencimiento, matricula) VALUES ('PNC-SEM3-01', CURRENT_DATE - 60, CURRENT_DATE + 300, 'SEM3001');
INSERT INTO permisos_circulacion (nro, fechaEmision, fechaVencimiento, matricula) VALUES ('PNC-SEM4-01', CURRENT_DATE - 60, CURRENT_DATE + 300, 'SEM4001');
INSERT INTO permisos_circulacion (nro, fechaEmision, fechaVencimiento, matricula) VALUES ('PNC-SEM5-01', CURRENT_DATE - 60, CURRENT_DATE + 300, 'SEM5001');
INSERT INTO itv (fechaEmision, fechaVencimiento, matricula) VALUES (CURRENT_DATE - 60, CURRENT_DATE + 200, 'SEM3001');
INSERT INTO itv (fechaEmision, fechaVencimiento, matricula) VALUES (CURRENT_DATE - 60, CURRENT_DATE + 200, 'SEM4001');
INSERT INTO itv (fechaEmision, fechaVencimiento, matricula) VALUES (CURRENT_DATE - 60, CURRENT_DATE + 200, 'SEM5001');

-- DEMO / Fiscalizacion de habilitaciones (frontoffice de funcionarios): la empresa 6 tiene un vehiculo en cada
-- estado del permiso y la ITV. Sin viajes ni GPS: no cambia las estadisticas del portal ni genera casos.
-- Al dia: 3 habilitados (2 por vencer), 4 no habilitados y 1 dado de baja.
INSERT INTO empresas (nroEmpresa, nombrePublico, razonSocial, direccion, habilitada, fechaAlta, version) VALUES (6, 'Fletes del Centro', 'Fletes del Centro S.A.', 'Ruta 5 km 183, Durazno', true, CURRENT_DATE - 30, 0);
INSERT INTO vehiculos (matricula, marca, modelo, peso, capacidadCargaKg, activo, nro_empresa) VALUES ('SFH1001', 'Volvo', 'FH 540', 9400, 27000, true, 6);
INSERT INTO vehiculos (matricula, marca, modelo, peso, capacidadCargaKg, activo, nro_empresa) VALUES ('SFH1002', 'Scania', 'R 500', 9200, 26000, true, 6);
INSERT INTO vehiculos (matricula, marca, modelo, peso, capacidadCargaKg, activo, nro_empresa) VALUES ('SFH1003', 'Iveco', 'S-Way', 8700, 25000, true, 6);
INSERT INTO vehiculos (matricula, marca, modelo, peso, capacidadCargaKg, activo, nro_empresa) VALUES ('SFH1004', 'Mercedes-Benz', 'Actros', 8500, 25000, true, 6);
INSERT INTO vehiculos (matricula, marca, modelo, peso, capacidadCargaKg, activo, nro_empresa) VALUES ('SFH1005', 'DAF', 'XF 480', 8900, 26000, true, 6);
INSERT INTO vehiculos (matricula, marca, modelo, peso, capacidadCargaKg, activo, nro_empresa) VALUES ('SFH1006', 'Volvo', 'FM 460', 9100, 27000, true, 6);
INSERT INTO vehiculos (matricula, marca, modelo, peso, capacidadCargaKg, activo, nro_empresa) VALUES ('SFH1007', 'Scania', 'G 410', 8800, 26000, true, 6);
INSERT INTO vehiculos (matricula, marca, modelo, peso, capacidadCargaKg, activo, nro_empresa) VALUES ('SFH1008', 'Iveco', 'Tector', 6800, 15000, false, 6);
-- SFH1001 al dia, con una ITV anterior ya vencida (historial)
INSERT INTO permisos_circulacion (nro, fechaEmision, fechaVencimiento, matricula) VALUES ('PNC-SFH-01', CURRENT_DATE - 60, CURRENT_DATE + 300, 'SFH1001');
INSERT INTO itv (fechaEmision, fechaVencimiento, matricula) VALUES (CURRENT_DATE - 425, CURRENT_DATE - 60, 'SFH1001');
INSERT INTO itv (fechaEmision, fechaVencimiento, matricula) VALUES (CURRENT_DATE - 60, CURRENT_DATE + 200, 'SFH1001');
-- SFH1002 habilitado con el permiso por vencer (12 dias)
INSERT INTO permisos_circulacion (nro, fechaEmision, fechaVencimiento, matricula) VALUES ('PNC-SFH-02', CURRENT_DATE - 353, CURRENT_DATE + 12, 'SFH1002');
INSERT INTO itv (fechaEmision, fechaVencimiento, matricula) VALUES (CURRENT_DATE - 30, CURRENT_DATE + 335, 'SFH1002');
-- SFH1003 habilitado con la ITV que vence hoy
INSERT INTO permisos_circulacion (nro, fechaEmision, fechaVencimiento, matricula) VALUES ('PNC-SFH-03', CURRENT_DATE - 90, CURRENT_DATE + 270, 'SFH1003');
INSERT INTO itv (fechaEmision, fechaVencimiento, matricula) VALUES (CURRENT_DATE - 365, CURRENT_DATE, 'SFH1003');
-- SFH1004 con el permiso vencido ayer
INSERT INTO permisos_circulacion (nro, fechaEmision, fechaVencimiento, matricula) VALUES ('PNC-SFH-04', CURRENT_DATE - 366, CURRENT_DATE - 1, 'SFH1004');
INSERT INTO itv (fechaEmision, fechaVencimiento, matricula) VALUES (CURRENT_DATE - 100, CURRENT_DATE + 265, 'SFH1004');
-- SFH1005 con la ITV vencida hace 20 dias
INSERT INTO permisos_circulacion (nro, fechaEmision, fechaVencimiento, matricula) VALUES ('PNC-SFH-05', CURRENT_DATE - 120, CURRENT_DATE + 240, 'SFH1005');
INSERT INTO itv (fechaEmision, fechaVencimiento, matricula) VALUES (CURRENT_DATE - 385, CURRENT_DATE - 20, 'SFH1005');
-- SFH1006 con un permiso que todavia no empezo (desde dentro de 10 dias)
INSERT INTO permisos_circulacion (nro, fechaEmision, fechaVencimiento, matricula) VALUES ('PNC-SFH-06', CURRENT_DATE + 10, CURRENT_DATE + 375, 'SFH1006');
INSERT INTO itv (fechaEmision, fechaVencimiento, matricula) VALUES (CURRENT_DATE - 15, CURRENT_DATE + 350, 'SFH1006');
-- SFH1007 con permiso vigente pero sin ninguna ITV registrada
INSERT INTO permisos_circulacion (nro, fechaEmision, fechaVencimiento, matricula) VALUES ('PNC-SFH-07', CURRENT_DATE - 45, CURRENT_DATE + 320, 'SFH1007');
-- SFH1008 dado de baja, con los papeles vencidos
INSERT INTO permisos_circulacion (nro, fechaEmision, fechaVencimiento, matricula) VALUES ('PNC-SFH-08', CURRENT_DATE - 700, CURRENT_DATE - 335, 'SFH1008');
INSERT INTO itv (fechaEmision, fechaVencimiento, matricula) VALUES (CURRENT_DATE - 700, CURRENT_DATE - 335, 'SFH1008');
INSERT INTO guias (id, rubroCliente, tipoCarga, pesoCargaKg, fecha, origen, destino, nro_empresa, id_vehiculo, estado, fechaAsignacion, version) VALUES ('GV-PUB-301', 'Agropecuario', 'Soja a granel', 22000, CURRENT_DATE - 20, 'Young', 'Nueva Palmira', 3, 'SEM3001', 'FINALIZADA', CURRENT_TIMESTAMP - INTERVAL '21 days', 0);
INSERT INTO guias (id, rubroCliente, tipoCarga, pesoCargaKg, fecha, origen, destino, nro_empresa, id_vehiculo, estado, fechaAsignacion, version) VALUES ('GV-PUB-302', 'Forestal', 'Rollizos de pino', 25000, CURRENT_DATE - 45, 'Rivera', 'Fray Bentos', 3, 'SEM3001', 'FINALIZADA', CURRENT_TIMESTAMP - INTERVAL '46 days', 0);
INSERT INTO guias (id, rubroCliente, tipoCarga, pesoCargaKg, fecha, origen, destino, nro_empresa, id_vehiculo, estado, fechaAsignacion, version) VALUES ('GV-PUB-401', 'Agropecuario', 'Arroz', 19000, CURRENT_DATE - 20, 'Treinta y Tres', 'Montevideo', 4, 'SEM4001', 'FINALIZADA', CURRENT_TIMESTAMP - INTERVAL '21 days', 0);
INSERT INTO guias (id, rubroCliente, tipoCarga, pesoCargaKg, fecha, origen, destino, nro_empresa, id_vehiculo, estado, fechaAsignacion, version) VALUES ('GV-PUB-402', 'Forestal', 'Madera aserrada', 26000, CURRENT_DATE - 75, 'Tacuarembo', 'Montevideo', 4, 'SEM4001', 'FINALIZADA', CURRENT_TIMESTAMP - INTERVAL '76 days', 0);
INSERT INTO guias (id, rubroCliente, tipoCarga, pesoCargaKg, fecha, origen, destino, nro_empresa, id_vehiculo, estado, fechaAsignacion, version) VALUES ('GV-PUB-501', 'Agropecuario', 'Trigo', 21000, CURRENT_DATE - 45, 'Mercedes', 'Montevideo', 5, 'SEM5001', 'FINALIZADA', CURRENT_TIMESTAMP - INTERVAL '46 days', 0);
INSERT INTO guias (id, rubroCliente, tipoCarga, pesoCargaKg, fecha, origen, destino, nro_empresa, id_vehiculo, estado, fechaAsignacion, version) VALUES ('GV-PUB-502', 'Construcción', 'Arena', 15000, CURRENT_DATE - 75, 'San Jose', 'Montevideo', 5, 'SEM5001', 'FINALIZADA', CURRENT_TIMESTAMP - INTERVAL '76 days', 0);
-- Infracciones confirmadas de las empresas 3 y 4: con la confirmada de la empresa 1, EXCESO_CARGA junta 3 empresas.
INSERT INTO casos_fiscalizacion (descripcion, tipoCaso, estado, fechaDeteccion, id_guia, id_vehiculo, notificacion_vista) VALUES ('DEMO PUBLICO: exceso de 1500 kg', 'EXCESO_CARGA', 'CONFIRMADA', CURRENT_TIMESTAMP - INTERVAL '20 days', 'GV-PUB-301', 'SEM3001', true);
INSERT INTO casos_fiscalizacion (descripcion, tipoCaso, estado, fechaDeteccion, id_guia, id_vehiculo, notificacion_vista) VALUES ('DEMO PUBLICO: exceso de 900 kg', 'EXCESO_CARGA', 'CONFIRMADA', CURRENT_TIMESTAMP - INTERVAL '20 days', 'GV-PUB-401', 'SEM4001', true);
INSERT INTO fundamentos_resolucion (descripcion, fechaHora, ci_funcionario, id_caso) SELECT 'Se confirma el exceso registrado en balanza.', fechaDeteccion + INTERVAL '2 days', '54074380', id FROM casos_fiscalizacion WHERE descripcion LIKE 'DEMO PUBLICO:%';
INSERT INTO auditoria_casos (id_caso, usuario, accion, estadoAnterior, estadoNuevo, fundamento, fechaHora, correlationId) SELECT id, 'SISTEMA', 'CREACION', NULL, 'DETECTADA', 'Caso precargado', fechaDeteccion, 'seed-demo' FROM casos_fiscalizacion WHERE descripcion LIKE 'DEMO PUBLICO:%';
INSERT INTO auditoria_casos (id_caso, usuario, accion, estadoAnterior, estadoNuevo, fundamento, fechaHora, correlationId) SELECT id, 'SISTEMA', 'CAMBIO_ESTADO', 'DETECTADA', 'NOTIFICADA', NULL, fechaDeteccion + INTERVAL '1 minute', 'seed-demo' FROM casos_fiscalizacion WHERE descripcion LIKE 'DEMO PUBLICO:%';
INSERT INTO auditoria_casos (id_caso, usuario, accion, estadoAnterior, estadoNuevo, fundamento, fechaHora, correlationId) SELECT id, 'SISTEMA', 'CAMBIO_ESTADO', 'NOTIFICADA', 'EN_DESCARGO', NULL, fechaDeteccion + INTERVAL '1 day', 'seed-demo' FROM casos_fiscalizacion WHERE descripcion LIKE 'DEMO PUBLICO:%';
INSERT INTO auditoria_casos (id_caso, usuario, accion, estadoAnterior, estadoNuevo, fundamento, fechaHora, correlationId) SELECT c.id, '54074380', 'RESOLUCION', 'EN_DESCARGO', 'CONFIRMADA', f.descripcion, f.fechaHora, 'seed-demo' FROM casos_fiscalizacion c JOIN fundamentos_resolucion f ON f.id_caso = c.id WHERE c.descripcion LIKE 'DEMO PUBLICO:%';
-- Tracking load test fleet (load-tests/tracking-generator): 500 vehicles LT00001..LT00500 of company 900, each with
-- valid permit and ITV, an EN_CURSO guide (so its events don't open trip-without-guide cases) and its own active GPS,
-- id_externo 100001..100500 (LT00001 = GPS 100001).
INSERT INTO empresas (nroEmpresa, nombrePublico, razonSocial, direccion, habilitada, fechaAlta, version) VALUES (900, 'Prueba de carga', 'Prueba de carga S.A.', 'Ruta 1 km 0, Montevideo', true, CURRENT_DATE - 7, 0);
INSERT INTO ciudadanos (ci, estado, creado) VALUES ('90000000', 'PENDIENTE_VALIDACION', CURRENT_TIMESTAMP);
INSERT INTO choferes (ci, nro_empresa, estado, fechaAlta) VALUES ('90000000', 900, 'HABILITADO', CURRENT_TIMESTAMP);
INSERT INTO vehiculos (matricula, marca, modelo, peso, capacidadCargaKg, activo, nro_empresa) SELECT 'LT' || LPAD(n::text, 5, '0'), 'Scania', 'R 450', 9000, 26000, true, 900 FROM generate_series(1, 500) AS n;
INSERT INTO permisos_circulacion (nro, fechaEmision, fechaVencimiento, matricula) SELECT 'PNC-LT' || LPAD(n::text, 5, '0'), CURRENT_DATE - 30, CURRENT_DATE + 335, 'LT' || LPAD(n::text, 5, '0') FROM generate_series(1, 500) AS n;
INSERT INTO itv (fechaEmision, fechaVencimiento, matricula) SELECT CURRENT_DATE - 30, CURRENT_DATE + 335, 'LT' || LPAD(n::text, 5, '0') FROM generate_series(1, 500) AS n;
INSERT INTO guias (id, rubroCliente, tipoCarga, pesoCargaKg, fecha, origen, destino, nro_empresa, id_vehiculo, id_chofer, estado, fechaAsignacion, version) SELECT 'GV-LT' || LPAD(n::text, 5, '0'), 'Agropecuario', 'Granos a granel', 20000, CURRENT_DATE, 'Montevideo', 'Paysandu', 900, 'LT' || LPAD(n::text, 5, '0'), (SELECT id FROM choferes WHERE ci = '90000000' AND nro_empresa = 900), 'EN_CURSO', CURRENT_TIMESTAMP, 0 FROM generate_series(1, 500) AS n;
INSERT INTO nodos_perifericos (tipo, id_externo, nombre, ubicacion, activo) SELECT 'TRACKING', 100000 + n, 'GPS LT' || LPAD(n::text, 5, '0'), 'Vehiculo LT' || LPAD(n::text, 5, '0'), true FROM generate_series(1, 500) AS n;
INSERT INTO nodos_tracking (id) SELECT id FROM nodos_perifericos WHERE tipo = 'TRACKING' AND id_externo BETWEEN 100001 AND 100500;
-- Rubros de las empresas de prueba (catalogo precargado en esquema-adicional.sql)
INSERT INTO empresa_rubros (nro_empresa, id_rubro) VALUES (1, 1), (1, 2), (1, 3), (2, 1), (3, 1), (3, 2), (4, 1), (4, 5), (5, 1), (5, 3), (6, 7), (900, 1);

-- rutas autorizadas GV-FISC001
INSERT INTO rutas_referencia (sha256_geojson, geometria_geojson, distancia_metros, fecha_creacion) VALUES ('abee05fd98240cea3fcac714cfced2f32590fe71785892df45f92a32ec1a33f8', '{"type":"LineString","coordinates":[[-58.0305,-33.252398],[-58.029805,-33.25229],[-58.029591,-33.253224],[-58.029379,-33.25416],[-58.029159,-33.255109],[-58.028949,-33.256016],[-58.02875,-33.256945],[-58.028699,-33.257193],[-58.028677,-33.257298],[-58.028596,-33.257695],[-58.028557,-33.257884],[-58.029581,-33.258021],[-58.030816,-33.258171],[-58.031976,-33.258311],[-58.031853,-33.259237],[-58.031883,-33.259326],[-58.0318,-33.260131],[-58.031778,-33.260376],[-58.031771,-33.260457],[-58.031775,-33.260547],[-58.031787,-33.260626],[-58.031809,-33.260712],[-58.031885,-33.260972],[-58.031964,-33.261206],[-58.032456,-33.262814],[-58.032629,-33.263372],[-58.032684,-33.263548],[-58.033106,-33.264885],[-58.033207,-33.265205],[-58.033279,-33.265426],[-58.033523,-33.266147],[-58.033484,-33.266215],[-58.033654,-33.266773],[-58.033742,-33.267018],[-58.034057,-33.268161],[-58.034233,-33.268943],[-58.034381,-33.269584],[-58.034585,-33.270509],[-58.034738,-33.271154],[-58.035032,-33.272386],[-58.035578,-33.274087],[-58.03696,-33.27812],[-58.036945,-33.278168],[-58.037014,-33.278259],[-58.037757,-33.280466],[-58.038014,-33.281225],[-58.038165,-33.281526],[-58.038406,-33.28197],[-58.038717,-33.282369],[-58.038915,-33.282562],[-58.039189,-33.282786],[-58.039659,-33.28311],[-58.040949,-33.283851],[-58.041481,-33.284154],[-58.045939,-33.286737],[-58.048041,-33.288283],[-58.048206,-33.288407],[-58.048952,-33.28897],[-58.05048,-33.290128],[-58.053876,-33.292664],[-58.054075,-33.292826],[-58.054349,-33.293086],[-58.054519,-33.293287],[-58.054714,-33.293526],[-58.054935,-33.293825],[-58.055082,-33.294063],[-58.055187,-33.294258],[-58.055348,-33.294607],[-58.055485,-33.29499],[-58.055558,-33.29529],[-58.055627,-33.295764],[-58.055684,-33.296271],[-58.055751,-33.29675],[-58.055832,-33.297381],[-58.055911,-33.298004],[-58.055996,-33.298757],[-58.056069,-33.29923],[-58.056153,-33.299884],[-58.056235,-33.30056],[-58.056306,-33.301325],[-58.056373,-33.301875],[-58.056491,-33.30279],[-58.056593,-33.303566],[-58.056703,-33.304477],[-58.056804,-33.305182],[-58.056881,-33.305909],[-58.057004,-33.306888],[-58.057063,-33.307323],[-58.057154,-33.307844],[-58.057307,-33.308604],[-58.057436,-33.30903],[-58.057476,-33.309165],[-58.060494,-33.31943],[-58.060786,-33.320097],[-58.060947,-33.320462],[-58.061108,-33.320804],[-58.061282,-33.321125],[-58.06146,-33.321438],[-58.061652,-33.321737],[-58.061854,-33.322031],[-58.062316,-33.322647],[-58.064344,-33.325327],[-58.0693,-33.331753],[-58.069976,-33.332638],[-58.070151,-33.332902],[-58.070303,-33.333163],[-58.070421,-33.333378],[-58.070526,-33.333595],[-58.070612,-33.333817],[-58.070686,-33.33404],[-58.070738,-33.334212],[-58.070783,-33.334395],[-58.070816,-33.334578],[-58.070838,-33.334762],[-58.071431,-33.343251],[-58.071498,-33.343817],[-58.071597,-33.344315],[-58.071732,-33.344746],[-58.071953,-33.345207],[-58.072218,-33.345648],[-58.072481,-33.345985],[-58.074422,-33.348472],[-58.074546,-33.34863],[-58.077787,-33.352703],[-58.079716,-33.35503],[-58.084057,-33.360268],[-58.091679,-33.369208],[-58.096466,-33.374822],[-58.096943,-33.375382],[-58.09717,-33.37565],[-58.097398,-33.375911],[-58.097637,-33.376147],[-58.097885,-33.376376],[-58.098138,-33.376593],[-58.098397,-33.376799],[-58.100453,-33.378268],[-58.1047,-33.381235],[-58.113249,-33.387214],[-58.113743,-33.387592],[-58.114222,-33.388005],[-58.114593,-33.388374],[-58.114915,-33.38872],[-58.11522,-33.389075],[-58.115562,-33.38953],[-58.115877,-33.390007],[-58.116024,-33.39027],[-58.116189,-33.390565],[-58.116826,-33.391694],[-58.117085,-33.392174],[-58.117886,-33.393626],[-58.118103,-33.394069],[-58.118304,-33.394556],[-58.118455,-33.395041],[-58.118634,-33.395695],[-58.118702,-33.396091],[-58.11876,-33.396487],[-58.118824,-33.397265],[-58.119106,-33.400528],[-58.119202,-33.401571],[-58.119401,-33.403733],[-58.119487,-33.404207],[-58.119581,-33.404676],[-58.119686,-33.405088],[-58.119808,-33.405477],[-58.120325,-33.407205],[-58.121397,-33.410742],[-58.121602,-33.411186],[-58.121821,-33.411569],[-58.122065,-33.411933],[-58.122433,-33.412405],[-58.123767,-33.41386],[-58.126608,-33.416902],[-58.130004,-33.420515],[-58.132098,-33.422795],[-58.133079,-33.423818],[-58.135336,-33.426241],[-58.137567,-33.428625],[-58.140626,-33.431896],[-58.141151,-33.432438],[-58.141694,-33.432951],[-58.142131,-33.43332],[-58.142355,-33.433502],[-58.142875,-33.433912],[-58.144045,-33.43479],[-58.148007,-33.437761],[-58.150552,-33.439678],[-58.155565,-33.443452],[-58.156204,-33.443945],[-58.156825,-33.444418],[-58.157448,-33.44494],[-58.157709,-33.44518],[-58.157963,-33.445434],[-58.158212,-33.445709],[-58.1585,-33.446044],[-58.158903,-33.446602],[-58.159737,-33.447854],[-58.160765,-33.449431],[-58.161476,-33.450466],[-58.167233,-33.459193],[-58.169066,-33.461912],[-58.170155,-33.46326],[-58.171109,-33.464455],[-58.171981,-33.46559],[-58.172489,-33.466275],[-58.172728,-33.466653],[-58.172918,-33.467038],[-58.173086,-33.467461],[-58.173221,-33.46791],[-58.173276,-33.46833],[-58.173305,-33.46855],[-58.173185,-33.471836],[-58.173139,-33.473076],[-58.173037,-33.475141],[-58.172937,-33.47723],[-58.172849,-33.479061],[-58.172808,-33.479923],[-58.172676,-33.481401],[-58.171984,-33.484457],[-58.171517,-33.486215],[-58.171387,-33.486842],[-58.171306,-33.487431],[-58.171305,-33.487474],[-58.171287,-33.488001],[-58.171307,-33.488537],[-58.17137,-33.48912],[-58.171451,-33.489683],[-58.171553,-33.490097],[-58.171683,-33.490509],[-58.171981,-33.491293],[-58.172626,-33.4929],[-58.172915,-33.493617],[-58.172963,-33.493736],[-58.173902,-33.496062],[-58.174413,-33.497136],[-58.174774,-33.497714],[-58.177079,-33.500607],[-58.177684,-33.501378],[-58.178893,-33.502962],[-58.179783,-33.504001],[-58.180283,-33.504439],[-58.180785,-33.504879],[-58.181691,-33.505498],[-58.182727,-33.506121],[-58.183359,-33.506497],[-58.183783,-33.506749],[-58.185322,-33.507665],[-58.187582,-33.50901],[-58.188179,-33.509365],[-58.188953,-33.509826],[-58.189703,-33.510273],[-58.190421,-33.510646],[-58.190938,-33.51102],[-58.193183,-33.513344],[-58.193828,-33.514011],[-58.194418,-33.514649],[-58.19478,-33.515035],[-58.195034,-33.515306],[-58.195713,-33.51603],[-58.196018,-33.516275],[-58.196222,-33.516369],[-58.196448,-33.516473],[-58.197502,-33.516698],[-58.197938,-33.51679],[-58.199148,-33.517048],[-58.200786,-33.51739],[-58.201953,-33.517623],[-58.202722,-33.517721],[-58.204231,-33.518041],[-58.204211,-33.518119],[-58.204279,-33.518632],[-58.204408,-33.519736],[-58.20448,-33.519942],[-58.20486,-33.520881],[-58.206276,-33.52364],[-58.206293,-33.523817],[-58.206257,-33.523938],[-58.206161,-33.524044],[-58.206049,-33.524132],[-58.204062,-33.524812],[-58.200859,-33.525901],[-58.200697,-33.526024],[-58.200655,-33.52613],[-58.200656,-33.526291],[-58.202116,-33.531921],[-58.20237,-33.532865],[-58.202828,-33.534455],[-58.203025,-33.535137],[-58.20322,-33.535818],[-58.203382,-33.536362],[-58.203433,-33.536503],[-58.203479,-33.536614],[-58.203541,-33.536722],[-58.20363,-33.536851],[-58.206468,-33.540842],[-58.20682,-33.541317],[-58.206932,-33.541436],[-58.207052,-33.541549],[-58.207233,-33.541706],[-58.207381,-33.541824],[-58.207539,-33.541932],[-58.20775,-33.542069],[-58.207927,-33.542167],[-58.208081,-33.542249],[-58.209079,-33.54272],[-58.209743,-33.543019],[-58.210388,-33.543305],[-58.21079,-33.543498],[-58.210849,-33.54352],[-58.210908,-33.543534],[-58.210974,-33.543545],[-58.211773,-33.543637],[-58.211938,-33.543675],[-58.212159,-33.543731],[-58.21293,-33.543913],[-58.213537,-33.544045],[-58.21409,-33.544106],[-58.214569,-33.544115],[-58.214981,-33.544074],[-58.215364,-33.543999],[-58.216474,-33.543683],[-58.218404,-33.543032],[-58.219152,-33.542802],[-58.219935,-33.542564],[-58.220545,-33.542414],[-58.222027,-33.542091],[-58.222653,-33.541939],[-58.224427,-33.541531],[-58.224999,-33.541481],[-58.227371,-33.541518],[-58.227712,-33.541531],[-58.228252,-33.54158],[-58.228846,-33.541624],[-58.229988,-33.541708],[-58.235552,-33.546126],[-58.235792,-33.546316],[-58.236555,-33.546929],[-58.237043,-33.547316],[-58.237334,-33.547546],[-58.238648,-33.548588],[-58.240256,-33.549862],[-58.241659,-33.55098],[-58.241868,-33.551147],[-58.248722,-33.556612],[-58.252357,-33.559516],[-58.253967,-33.560801],[-58.256849,-33.563103],[-58.258259,-33.564228],[-58.260394,-33.565933],[-58.261705,-33.56698],[-58.267757,-33.571812],[-58.269278,-33.573026],[-58.275436,-33.577925],[-58.2805,-33.581956],[-58.282957,-33.583949],[-58.285736,-33.586326],[-58.287805,-33.58805],[-58.288461,-33.588597],[-58.292457,-33.591923],[-58.293564,-33.592866],[-58.296339,-33.595254],[-58.304122,-33.603008],[-58.314224,-33.613036],[-58.325762,-33.624565],[-58.325905,-33.624661],[-58.327265,-33.625985],[-58.328176,-33.626977],[-58.32824,-33.627095],[-58.330005,-33.62881],[-58.330546,-33.629493],[-58.330872,-33.630112],[-58.331012,-33.630803],[-58.331078,-33.63219],[-58.331111,-33.63273],[-58.331663,-33.647623],[-58.331666,-33.647703],[-58.332204,-33.661458],[-58.332225,-33.661989],[-58.332931,-33.680234],[-58.333097,-33.685293],[-58.333146,-33.685726],[-58.333269,-33.68614],[-58.333528,-33.686713],[-58.333757,-33.687268],[-58.334487,-33.688981],[-58.334734,-33.689576],[-58.338007,-33.697197],[-58.338124,-33.697533],[-58.338173,-33.697886],[-58.338181,-33.69834],[-58.337429,-33.708393],[-58.336642,-33.718375],[-58.336642,-33.721642],[-58.336653,-33.734442],[-58.33667,-33.73668],[-58.336673,-33.736956],[-58.336672,-33.745882],[-58.336686,-33.746242],[-58.336748,-33.746522],[-58.338961,-33.751784],[-58.340307,-33.754985],[-58.346893,-33.770643],[-58.347106,-33.771128],[-58.349802,-33.776901],[-58.353708,-33.785046],[-58.36437,-33.807378],[-58.364417,-33.807474],[-58.368029,-33.814617],[-58.378736,-33.836735],[-58.383157,-33.843429],[-58.383354,-33.843728],[-58.384383,-33.845286],[-58.386785,-33.849048],[-58.387089,-33.849622],[-58.387176,-33.849742],[-58.387364,-33.850237],[-58.387443,-33.850531],[-58.387627,-33.851963],[-58.388207,-33.851951],[-58.388383,-33.851949],[-58.388509,-33.851976],[-58.388633,-33.852025],[-58.388755,-33.85211],[-58.3892,-33.852723],[-58.389426,-33.85299],[-58.389655,-33.853243],[-58.390017,-33.853598],[-58.390231,-33.853799],[-58.393564,-33.856575],[-58.395372,-33.858064],[-58.396727,-33.859185],[-58.397482,-33.859834],[-58.398315,-33.860511],[-58.399089,-33.861149],[-58.399887,-33.861813],[-58.400679,-33.862461],[-58.401455,-33.863109],[-58.403041,-33.864425],[-58.403845,-33.865095],[-58.404629,-33.865752],[-58.406219,-33.867057],[-58.407016,-33.867704],[-58.407804,-33.868369],[-58.408598,-33.86902],[-58.409382,-33.869681],[-58.41017,-33.870333],[-58.410962,-33.87098],[-58.411742,-33.870315],[-58.411754,-33.870305]]}', 83455.8, CURRENT_TIMESTAMP);
INSERT INTO guias_rutas_autorizadas (guia_id, ruta_referencia_id, numero_alternativa) VALUES ('GV-FISC001', (SELECT id FROM rutas_referencia WHERE sha256_geojson = 'abee05fd98240cea3fcac714cfced2f32590fe71785892df45f92a32ec1a33f8'), 1);

-- Reglas de fiscalización: revisión inicial y referencia global.
INSERT INTO reglas_fiscalizacion_revisiones (revision,tolerancia_porcentaje,tolerancia_kg,distancia_metros,ventana_segundos,creada_en,usuario,fundamento) VALUES (1,0,0,2000,30,CURRENT_TIMESTAMP,'SISTEMA','Configuración inicial');
INSERT INTO reglas_fiscalizacion_vigente (id,revision,version) VALUES (1,1,0);
