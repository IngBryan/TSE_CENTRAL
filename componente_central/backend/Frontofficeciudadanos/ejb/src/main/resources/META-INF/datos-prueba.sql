-- Datos de prueba SOLO PARA DESARROLLO. Hibernate los carga despues del drop-and-create de
-- persistence.xml (jakarta.persistence.sql-load-script-source), o sea en cada deploy.
-- Una sentencia por linea. Datos ficticios salvo la cedula del responsable, que es la del
-- integrante que prueba el login con gub.uy testing.
--
-- Empresa habilitada; la alta real la hace un funcionario del MTOP (frontoffice de funcionarios).
INSERT INTO empresas (nroEmpresa, nombrePublico, razonSocial, direccion, habilitada) VALUES (1, 'Transportes del Este', 'Transportes del Este S.A.', 'Ruta 8 km 29, Pando, Canelones', true);
-- Responsable de la empresa. Su PerfilCiudadano NO se precarga: lo crea su primer ingreso con gub.uy (CU-01 FA2).
INSERT INTO responsables (ci, email, nro_empresa) VALUES ('54074380', NULL, 1);
-- Vehiculos de la empresa, para probar la asignacion de guias (CU-07) hasta que exista la gestion de vehiculos.
INSERT INTO vehiculos (matricula, marca, modelo, peso, capacidadCargaKg, nro_empresa) VALUES ('SBA 1234', 'Scania', 'R 450', 9000, 26000, 1);
INSERT INTO vehiculos (matricula, marca, modelo, peso, capacidadCargaKg, nro_empresa) VALUES ('SAB 5678', 'Volvo', 'FH 500', 9500, 28000, 1);
