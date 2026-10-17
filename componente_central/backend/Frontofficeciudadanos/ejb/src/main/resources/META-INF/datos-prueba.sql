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
-- Responsable de la empresa 1.
INSERT INTO responsables (ci, nro_empresa) VALUES ('54074380', 1);
-- Vehiculos de la empresa (matricula normalizada, como la mandan balanzas y tracking). SBA1234 queda
-- habilitado (permiso e ITV vigentes); SAB5678 sin permiso ni ITV, para ver el caso no habilitado.
INSERT INTO vehiculos (matricula, marca, modelo, peso, capacidadCargaKg, activo, nro_empresa) VALUES ('SBA1234', 'Scania', 'R 450', 9000, 26000, true, 1);
INSERT INTO vehiculos (matricula, marca, modelo, peso, capacidadCargaKg, activo, nro_empresa) VALUES ('SAB5678', 'Volvo', 'FH 500', 9500, 28000, true, 1);
INSERT INTO permisos_circulacion (nro, fechaEmision, fechaVencimiento, matricula) VALUES ('PNC-0001', CURRENT_DATE - 30, CURRENT_DATE + 335, 'SBA1234');
INSERT INTO itv (fechaVencimiento, matricula) VALUES (CURRENT_DATE + 180, 'SBA1234');
-- Sin choferes ni guias precargados: los choferes se dan de alta desde el frontoffice (CU-02).
