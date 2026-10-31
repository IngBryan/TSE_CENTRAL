-- Datos de prueba SOLO PARA DESARROLLO. Hibernate los carga despues del drop-and-create de
-- persistence.xml (jakarta.persistence.sql-load-script-source), o sea en cada deploy.
-- Una sentencia por linea. Datos ficticios salvo la cedula del responsable, que es la del
-- integrante que prueba el login con gub.uy testing.
--
-- Empresa habilitada; la alta real la hace un funcionario del MTOP (frontoffice de funcionarios).
INSERT INTO empresas (nroEmpresa, nombrePublico, razonSocial, direccion, habilitada, version) VALUES (1, 'Transportes del Este', 'Transportes del Este S.A.', 'Ruta 8 km 29, Pando, Canelones', true, 0);
-- DEMO / Empresas: contraste con empresa no habilitada (sin identidad gub.uy artificial).
INSERT INTO empresas (nroEmpresa, nombrePublico, razonSocial, direccion, habilitada, version) VALUES (2, 'Logistica del Litoral', 'Logistica del Litoral S.R.L.', 'Ruta 3 km 390, Paysandu', false , 0);
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
INSERT INTO choferes (ci, nro_empresa, estado, fechaAlta) VALUES ('56087406', 1, 'HABILITADO', CURRENT_TIMESTAMP);
INSERT INTO choferes (ci, nro_empresa, estado, fechaAlta) VALUES ('54074380', 1, 'HABILITADO', CURRENT_TIMESTAMP);
INSERT INTO guias (id, rubroCliente, tipoCarga, pesoCargaKg, fecha, origen, destino, nro_empresa, id_vehiculo, id_chofer, estado, fechaAsignacion, version) VALUES ('GV-MOVIL001', 'Agropecuario', 'Granos a granel', 18000, CURRENT_DATE, 'Montevideo', 'Paysandu', 1, 'SBA1234', (SELECT id FROM choferes WHERE ci = '56087406' AND nro_empresa = 1), 'ASIGNADA', CURRENT_TIMESTAMP, 0);
INSERT INTO guias (id, rubroCliente, tipoCarga, pesoCargaKg, fecha, origen, destino, nro_empresa, id_vehiculo, id_chofer, estado, fechaAsignacion, version) VALUES ('GV-MOVIL002', 'Forestal', 'Rollizos de eucalipto', 24000, CURRENT_DATE, 'Rivera', 'Montevideo', 1, 'SAB5678', (SELECT id FROM choferes WHERE ci = '54074380' AND nro_empresa = 1), 'ASIGNADA', CURRENT_TIMESTAMP, 0);
-- DEMO / Chofer aun no validado: debe quedar fuera de las opciones de asignacion.
INSERT INTO ciudadanos (ci, estado, creado) VALUES ('22222222', 'PENDIENTE_VALIDACION', CURRENT_TIMESTAMP);
INSERT INTO choferes (ci, nro_empresa, estado, fechaAlta) VALUES ('22222222', 1, 'PENDIENTE_VALIDACION', CURRENT_TIMESTAMP);
-- DEMO / Guia nueva, vehiculo libre SDE3000; nunca se asigna inicialmente.
INSERT INTO guias (id, rubroCliente, tipoCarga, pesoCargaKg, fecha, origen, destino, nro_empresa, estado, version) VALUES ('GV-DEMO-ASIGNAR', 'Construccion', 'Materiales de obra', 12000, CURRENT_DATE + 1, 'Montevideo', 'Maldonado', 1, 'SIN_ASIGNAR', 0);
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
INSERT INTO guias (id, rubroCliente, tipoCarga, pesoCargaKg, fecha, origen, destino, nro_empresa, id_vehiculo, id_chofer, estado, fechaAsignacion, version) VALUES ('GV-DEMO-HIST-SBC', 'Agropecuario', 'Maiz a granel', 16000, CURRENT_DATE - 12, 'San Jose', 'Colonia', 1, 'SBC1234', (SELECT id FROM choferes WHERE ci = '11111111' AND nro_empresa = 1), 'FINALIZADA', CURRENT_TIMESTAMP - INTERVAL '13 days', 0);
-- Guia historica de SAB5678 para dos casos ya resueltos: no interfiere con detecciones en vivo.
INSERT INTO guias (id, rubroCliente, tipoCarga, pesoCargaKg, fecha, origen, destino, nro_empresa, id_vehiculo, id_chofer, estado, fechaAsignacion, version) VALUES ('GV-DEMO-HIST-SAB', 'Forestal', 'Rollizos', 24000, CURRENT_DATE - 5, 'Rivera', 'Tacuarembo', 1, 'SAB5678', (SELECT id FROM choferes WHERE ci = '11111111' AND nro_empresa = 1), 'FINALIZADA', CURRENT_TIMESTAMP - INTERVAL '6 days', 0);
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
-- Frontoffice de funcionarios (CU Gestionar casos de fiscalizacion): el integrante de gub.uy testing tambien es
-- funcionario, y hay casos de fiscalizacion en distintos estados hasta que la fiscalizacion automatica los genere.
INSERT INTO funcionarios (ci, email) VALUES ('54074380', 'funcionario.prueba@mtop.gub.uy');
-- Gestion de empresas: ciudadano ficticio ya validado y sin ningun rol, para elegirlo como responsable de una
-- empresa sin mezclarlo con los datos de los otros casos de prueba
INSERT INTO ciudadanos (ci, primerNombre, primerApellido, nombres, apellidos, estado, creado, ultimaValidacion) VALUES ('22222222', 'Laura', 'Gomez', 'Laura', 'Gomez', 'VALIDADO', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
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
INSERT INTO empresas (nroEmpresa, nombrePublico, razonSocial, direccion, habilitada, version) VALUES (3, 'Fletes del Norte', 'Fletes del Norte S.A.', 'Ruta 5 km 498, Rivera', true, 0);
INSERT INTO empresas (nroEmpresa, nombrePublico, razonSocial, direccion, habilitada, version) VALUES (4, 'Cargas del Sur', 'Cargas del Sur S.R.L.', 'Camino Maldonado 7500, Montevideo', true, 0);
INSERT INTO empresas (nroEmpresa, nombrePublico, razonSocial, direccion, habilitada, version) VALUES (5, 'Transporte Oriental', 'Transporte Oriental S.A.', 'Ruta 1 km 67, San Jose', true, 0);
INSERT INTO vehiculos (matricula, marca, modelo, peso, capacidadCargaKg, activo, nro_empresa) VALUES ('SEM3001', 'Scania', 'G 410', 8800, 26000, true, 3);
INSERT INTO vehiculos (matricula, marca, modelo, peso, capacidadCargaKg, activo, nro_empresa) VALUES ('SEM4001', 'Volvo', 'FM 440', 9100, 27000, true, 4);
INSERT INTO vehiculos (matricula, marca, modelo, peso, capacidadCargaKg, activo, nro_empresa) VALUES ('SEM5001', 'Iveco', 'S-Way', 8700, 25000, true, 5);
INSERT INTO permisos_circulacion (nro, fechaEmision, fechaVencimiento, matricula) VALUES ('PNC-SEM3-01', CURRENT_DATE - 60, CURRENT_DATE + 300, 'SEM3001');
INSERT INTO permisos_circulacion (nro, fechaEmision, fechaVencimiento, matricula) VALUES ('PNC-SEM4-01', CURRENT_DATE - 60, CURRENT_DATE + 300, 'SEM4001');
INSERT INTO permisos_circulacion (nro, fechaEmision, fechaVencimiento, matricula) VALUES ('PNC-SEM5-01', CURRENT_DATE - 60, CURRENT_DATE + 300, 'SEM5001');
INSERT INTO itv (fechaEmision, fechaVencimiento, matricula) VALUES (CURRENT_DATE - 60, CURRENT_DATE + 200, 'SEM3001');
INSERT INTO itv (fechaEmision, fechaVencimiento, matricula) VALUES (CURRENT_DATE - 60, CURRENT_DATE + 200, 'SEM4001');
INSERT INTO itv (fechaEmision, fechaVencimiento, matricula) VALUES (CURRENT_DATE - 60, CURRENT_DATE + 200, 'SEM5001');
INSERT INTO guias (id, rubroCliente, tipoCarga, pesoCargaKg, fecha, origen, destino, nro_empresa, id_vehiculo, estado, fechaAsignacion, version) VALUES ('GV-PUB-301', 'Agropecuario', 'Soja a granel', 22000, CURRENT_DATE - 20, 'Young', 'Nueva Palmira', 3, 'SEM3001', 'FINALIZADA', CURRENT_TIMESTAMP - INTERVAL '21 days', 0);
INSERT INTO guias (id, rubroCliente, tipoCarga, pesoCargaKg, fecha, origen, destino, nro_empresa, id_vehiculo, estado, fechaAsignacion, version) VALUES ('GV-PUB-302', 'Forestal', 'Rollizos de pino', 25000, CURRENT_DATE - 45, 'Rivera', 'Fray Bentos', 3, 'SEM3001', 'FINALIZADA', CURRENT_TIMESTAMP - INTERVAL '46 days', 0);
INSERT INTO guias (id, rubroCliente, tipoCarga, pesoCargaKg, fecha, origen, destino, nro_empresa, id_vehiculo, estado, fechaAsignacion, version) VALUES ('GV-PUB-401', 'Agropecuario', 'Arroz', 19000, CURRENT_DATE - 20, 'Treinta y Tres', 'Montevideo', 4, 'SEM4001', 'FINALIZADA', CURRENT_TIMESTAMP - INTERVAL '21 days', 0);
INSERT INTO guias (id, rubroCliente, tipoCarga, pesoCargaKg, fecha, origen, destino, nro_empresa, id_vehiculo, estado, fechaAsignacion, version) VALUES ('GV-PUB-402', 'Forestal', 'Madera aserrada', 26000, CURRENT_DATE - 75, 'Tacuarembo', 'Montevideo', 4, 'SEM4001', 'FINALIZADA', CURRENT_TIMESTAMP - INTERVAL '76 days', 0);
INSERT INTO guias (id, rubroCliente, tipoCarga, pesoCargaKg, fecha, origen, destino, nro_empresa, id_vehiculo, estado, fechaAsignacion, version) VALUES ('GV-PUB-501', 'Agropecuario', 'Trigo', 21000, CURRENT_DATE - 45, 'Mercedes', 'Montevideo', 5, 'SEM5001', 'FINALIZADA', CURRENT_TIMESTAMP - INTERVAL '46 days', 0);
INSERT INTO guias (id, rubroCliente, tipoCarga, pesoCargaKg, fecha, origen, destino, nro_empresa, id_vehiculo, estado, fechaAsignacion, version) VALUES ('GV-PUB-502', 'Construccion', 'Arena', 15000, CURRENT_DATE - 75, 'San Jose', 'Montevideo', 5, 'SEM5001', 'FINALIZADA', CURRENT_TIMESTAMP - INTERVAL '76 days', 0);
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
INSERT INTO empresas (nroEmpresa, nombrePublico, razonSocial, direccion, habilitada, version) VALUES (900, 'Prueba de carga', 'Prueba de carga S.A.', 'Ruta 1 km 0, Montevideo', true, 0);
INSERT INTO ciudadanos (ci, estado, creado) VALUES ('90000000', 'PENDIENTE_VALIDACION', CURRENT_TIMESTAMP);
INSERT INTO choferes (ci, nro_empresa, estado, fechaAlta) VALUES ('90000000', 900, 'HABILITADO', CURRENT_TIMESTAMP);
INSERT INTO vehiculos (matricula, marca, modelo, peso, capacidadCargaKg, activo, nro_empresa) SELECT 'LT' || LPAD(n::text, 5, '0'), 'Scania', 'R 450', 9000, 26000, true, 900 FROM generate_series(1, 500) AS n;
INSERT INTO permisos_circulacion (nro, fechaEmision, fechaVencimiento, matricula) SELECT 'PNC-LT' || LPAD(n::text, 5, '0'), CURRENT_DATE - 30, CURRENT_DATE + 335, 'LT' || LPAD(n::text, 5, '0') FROM generate_series(1, 500) AS n;
INSERT INTO itv (fechaEmision, fechaVencimiento, matricula) SELECT CURRENT_DATE - 30, CURRENT_DATE + 335, 'LT' || LPAD(n::text, 5, '0') FROM generate_series(1, 500) AS n;
INSERT INTO guias (id, rubroCliente, tipoCarga, pesoCargaKg, fecha, origen, destino, nro_empresa, id_vehiculo, id_chofer, estado, fechaAsignacion, version) SELECT 'GV-LT' || LPAD(n::text, 5, '0'), 'Agropecuario', 'Granos a granel', 20000, CURRENT_DATE, 'Montevideo', 'Paysandu', 900, 'LT' || LPAD(n::text, 5, '0'), (SELECT id FROM choferes WHERE ci = '90000000' AND nro_empresa = 900), 'EN_CURSO', CURRENT_TIMESTAMP, 0 FROM generate_series(1, 500) AS n;
INSERT INTO nodos_perifericos (tipo, id_externo, nombre, ubicacion, activo) SELECT 'TRACKING', 100000 + n, 'GPS LT' || LPAD(n::text, 5, '0'), 'Vehiculo LT' || LPAD(n::text, 5, '0'), true FROM generate_series(1, 500) AS n;
INSERT INTO nodos_tracking (id) SELECT id FROM nodos_perifericos WHERE tipo = 'TRACKING' AND id_externo BETWEEN 100001 AND 100500;
