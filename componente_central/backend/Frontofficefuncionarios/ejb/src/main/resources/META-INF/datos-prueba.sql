-- Datos de prueba SOLO PARA DESARROLLO. Hibernate los carga despues del drop-and-create de
-- persistence.xml (jakarta.persistence.sql-load-script-source), o sea en cada deploy.
-- Una sentencia por linea.
--
-- Responsable de la empresa 1 (la empresa la carga Frontofficeciudadanos). Su PerfilCiudadano NO se
-- precarga: lo crea su primer ingreso con gub.uy (CU-01 FA2). La cedula es la del integrante que prueba
-- el login con gub.uy testing.
INSERT INTO responsables (ci, email, nro_empresa) VALUES ('54074380', NULL, 1);
