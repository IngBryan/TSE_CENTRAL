-- Datos de prueba SOLO PARA DESARROLLO. Hibernate los carga despues del drop-and-create de
-- persistence.xml (jakarta.persistence.sql-load-script-source), o sea en cada deploy.
-- Una sentencia por linea. Datos ficticios salvo la cedula del responsable, que es la del
-- integrante que prueba el login con gub.uy testing.
--
-- Empresa habilitada; la alta real la hace un funcionario del MTOP (frontoffice de funcionarios).
INSERT INTO empresas (nroEmpresa, nombrePublico, razonSocial, direccion, habilitada) VALUES (1, 'Transportes del Este', 'Transportes del Este S.A.', 'Ruta 8 km 29, Pando, Canelones', true);
-- Ciudadanos que tienen un rol. Quedan pendientes sin cuenta gub.uy: su primer ingreso los vincula y valida
-- contra la PDI (CU-01 FA2). Los roles (responsables, choferes) los referencian por ci.
INSERT INTO ciudadanos (ci, estado, creado) VALUES ('54074380', 'PENDIENTE_VALIDACION', CURRENT_TIMESTAMP);
INSERT INTO ciudadanos (ci, estado, creado) VALUES ('56087406', 'PENDIENTE_VALIDACION', CURRENT_TIMESTAMP);
-- Responsable de la empresa 1.
INSERT INTO responsables (ci, nro_empresa) VALUES ('54074380', 1);
-- Vehiculos de la empresa, para probar la asignacion de guias (CU-07) hasta que exista la gestion de vehiculos.
INSERT INTO vehiculos (matricula, marca, modelo, peso, capacidadCargaKg, nro_empresa) VALUES ('SBA 1234', 'Scania', 'R 450', 9000, 26000, 1);
INSERT INTO vehiculos (matricula, marca, modelo, peso, capacidadCargaKg, nro_empresa) VALUES ('SAB 5678', 'Volvo', 'FH 500', 9500, 28000, 1);
-- App movil del chofer: Bryan (56087406, existe en la PDI) y el propio responsable (54074380, para probar con
-- su usuario de gub.uy testing) como choferes habilitados de la empresa 1, cada uno con una guia ASIGNADA.
INSERT INTO choferes (ci, nro_empresa, estado, fechaAlta) VALUES ('56087406', 1, 'HABILITADO', CURRENT_TIMESTAMP);
INSERT INTO choferes (ci, nro_empresa, estado, fechaAlta) VALUES ('54074380', 1, 'HABILITADO', CURRENT_TIMESTAMP);
INSERT INTO guias (id, rubroCliente, tipoCarga, pesoCargaKg, fecha, origen, destino, nro_empresa, id_vehiculo, id_chofer, estado, fechaAsignacion, version) VALUES ('GV-MOVIL001', 'Agropecuario', 'Granos a granel', 18000, CURRENT_DATE, 'Montevideo', 'Paysandu', 1, 'SBA 1234', (SELECT id FROM choferes WHERE ci = '56087406' AND nro_empresa = 1), 'ASIGNADA', CURRENT_TIMESTAMP, 0);
INSERT INTO guias (id, rubroCliente, tipoCarga, pesoCargaKg, fecha, origen, destino, nro_empresa, id_vehiculo, id_chofer, estado, fechaAsignacion, version) VALUES ('GV-MOVIL002', 'Forestal', 'Rollizos de eucalipto', 24000, CURRENT_DATE, 'Rivera', 'Montevideo', 1, 'SAB 5678', (SELECT id FROM choferes WHERE ci = '54074380' AND nro_empresa = 1), 'ASIGNADA', CURRENT_TIMESTAMP, 0);
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
INSERT INTO ciudadanos (ci, estado, creado) VALUES ('11111111', 'PENDIENTE_VALIDACION', CURRENT_TIMESTAMP);
INSERT INTO choferes (ci, nro_empresa, estado, fechaAlta) VALUES ('11111111', 1, 'HABILITADO', CURRENT_TIMESTAMP);
INSERT INTO guias (id, rubroCliente, tipoCarga, pesoCargaKg, fecha, origen, destino, nro_empresa, id_vehiculo, id_chofer, estado, fechaAsignacion, version) VALUES ('GV-FISC001', 'Agropecuario', 'Soja a granel', 20000, CURRENT_DATE, 'Mercedes', 'Nueva Palmira', 1, 'STU5678', (SELECT id FROM choferes WHERE ci = '11111111' AND nro_empresa = 1), 'EN_CURSO', CURRENT_TIMESTAMP, 0);
INSERT INTO nodos_perifericos (tipo, id_externo, nombre, ubicacion, activo) VALUES ('TRACKING', 1, 'GPS SBC1234', 'Vehiculo SBC1234', true);
INSERT INTO nodos_tracking (id) SELECT id FROM nodos_perifericos WHERE tipo = 'TRACKING' AND id_externo = 1;
INSERT INTO nodos_perifericos (tipo, id_externo, nombre, ubicacion, activo) VALUES ('TRACKING', 2, 'GPS STU5678', 'Vehiculo STU5678', true);
INSERT INTO nodos_tracking (id) SELECT id FROM nodos_perifericos WHERE tipo = 'TRACKING' AND id_externo = 2;
-- Administrador de prueba para el Backoffice.
-- CI: 99999999
-- Contrasena: AdminPrueba2026!
INSERT INTO usuarios_backoffice (ci, contrasena) VALUES ('99999999', 'pbkdf2-sha256:600000:XVb3i-u34NGRGh8VpALJ9A:PR0-5o08j6128R_8qPTGWP3rPuwuDBHRBrVpEloWM74');
INSERT INTO administradores (ci) VALUES ('99999999');