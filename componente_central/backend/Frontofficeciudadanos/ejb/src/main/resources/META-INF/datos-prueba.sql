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
-- Frontoffice de funcionarios (CU Gestionar casos de fiscalizacion): el integrante de gub.uy testing tambien es
-- funcionario, y hay casos de fiscalizacion en distintos estados hasta que la fiscalizacion automatica los genere.
INSERT INTO funcionarios (ci, email) VALUES ('54074380', 'funcionario.prueba@mtop.gub.uy');
INSERT INTO nodos_perifericos (nombre, activo) VALUES ('Balanza Peaje Pando', true);
INSERT INTO nodos_balanza (id) SELECT id FROM nodos_perifericos WHERE nombre = 'Balanza Peaje Pando';
INSERT INTO eventos_pesajes (peso, matricula, fechaHora, id_nodo_balanza) VALUES (38500, 'SBA 1234', CURRENT_TIMESTAMP, (SELECT id FROM nodos_perifericos WHERE nombre = 'Balanza Peaje Pando'));
INSERT INTO casos_fiscalizacion (descripcion, tipoCaso, estado, fechaDeteccion, id_guia) VALUES ('Pesaje de 38500 kg supera el maximo declarado de 35000 kg', 'EXCESO_CARGA', 'EN_DESCARGO', CURRENT_TIMESTAMP, 'GV-MOVIL001');
INSERT INTO casos_eventos_pesajes (id_caso, id_evento_pesaje) SELECT c.id, p.id FROM casos_fiscalizacion c, eventos_pesajes p WHERE c.tipoCaso = 'EXCESO_CARGA' AND p.matricula = 'SBA 1234';
INSERT INTO descargos (descripcion, tipoCaso, estado, id_caso) SELECT 'La balanza del peaje estaba descalibrada ese dia', 'EXCESO_CARGA', 'PRESENTADO', id FROM casos_fiscalizacion WHERE tipoCaso = 'EXCESO_CARGA';
INSERT INTO casos_fiscalizacion (descripcion, tipoCaso, estado, fechaDeteccion, id_guia) VALUES ('La ruta reportada se aparta del recorrido Rivera - Montevideo', 'DESVIO_RUTA', 'NOTIFICADA', CURRENT_TIMESTAMP, 'GV-MOVIL002');
INSERT INTO casos_fiscalizacion (descripcion, tipoCaso, estado, fechaDeteccion, id_guia) VALUES ('Tracking del vehiculo sin viaje en curso', 'VIAJE_SIN_GUIA', 'DETECTADA', CURRENT_TIMESTAMP, 'GV-MOVIL002');
