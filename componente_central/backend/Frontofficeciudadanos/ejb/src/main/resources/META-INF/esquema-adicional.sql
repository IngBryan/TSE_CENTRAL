-- Se ejecuta despues de crear el esquema desde las entidades (create-source = metadata-then-script).
-- Una sentencia por linea.
--
-- Un vehiculo no puede tener dos casos abiertos (DETECTADA o NOTIFICADA) del mismo tipo. La fiscalizacion
-- automatica decide si crea un caso nuevo; si dos de sus pods lo intentan a la vez para el mismo vehiculo,
-- el segundo falla aca, su evento queda pendiente y al reintentarlo se suma al caso que ya existe.
CREATE UNIQUE INDEX uk_caso_abierto ON casos_fiscalizacion (id_vehiculo, tipoCaso) WHERE estado IN ('DETECTADA', 'NOTIFICADA');
--
-- Catalogos precargados del sistema (la letra los considera datos precargados, sin ABM). Van aca y no en
-- datos-prueba.sql porque no son datos de prueba: tienen que estar en cualquier despliegue.
-- Rubros de las empresas transportistas (una empresa puede tener varios: tabla empresa_rubros).
INSERT INTO rubros (id, nombre) VALUES (1, 'Agropecuario'), (2, 'Forestal'), (3, 'Construccion'), (4, 'Industrial'), (5, 'Alimentos y bebidas'), (6, 'Combustibles y quimicos'), (7, 'Comercio y distribucion'), (8, 'Ganadero'), (9, 'Mineria'), (10, 'Contenedores y portuario');
-- Tipos de carga de las guias: al crear o editar una guia se elige uno de estos.
INSERT INTO tipos_carga (id, nombre) VALUES (1, 'Granos a granel'), (2, 'Soja a granel'), (3, 'Maiz a granel'), (4, 'Trigo'), (5, 'Arroz'), (6, 'Rollizos'), (7, 'Rollizos de eucalipto'), (8, 'Rollizos de pino'), (9, 'Madera aserrada'), (10, 'Materiales de obra'), (11, 'Arena'), (12, 'Ganado en pie'), (13, 'Contenedor'), (14, 'Carga refrigerada'), (15, 'Combustibles liquidos'), (16, 'Carga general');
