-- Se ejecuta tras crear el esquema desde entidades (metadata-then-script).
-- La Guia identifica el caso abierto si existe; sin Guia, identifica el vehiculo.

CREATE UNIQUE INDEX uk_caso_abierto_guia ON casos_fiscalizacion (id_guia, tipoCaso) WHERE id_guia IS NOT NULL AND estado IN ('DETECTADA', 'NOTIFICADA');

-- Para VIAJE_SIN_GUIA el caso pertenece a una circulacion, no a una matricula generica.
CREATE UNIQUE INDEX uk_caso_abierto_sin_guia ON casos_fiscalizacion (id_vehiculo, tipoCaso) WHERE id_guia IS NULL AND tipoCaso <> 'VIAJE_SIN_GUIA' AND estado IN ('DETECTADA', 'NOTIFICADA');

-- Una sola observacion pendiente o circulacion activa por vehiculo.
CREATE UNIQUE INDEX uk_circulacion_vigente_vehiculo ON circulaciones_sin_guia (id_vehiculo) WHERE estado IN ('PENDIENTE_MOVIMIENTO', 'ACTIVA');

--
-- Catalogos precargados del sistema (la letra los considera datos precargados, sin ABM). Van aca y no en
-- datos-prueba.sql porque no son datos de prueba: tienen que estar en cualquier despliegue.
-- Rubros de las empresas transportistas (una empresa puede tener varios: tabla empresa_rubros).
INSERT INTO rubros (id, nombre) VALUES (1, 'Agropecuario'), (2, 'Forestal'), (3, 'Construccion'), (4, 'Industrial'), (5, 'Alimentos y bebidas'), (6, 'Combustibles y quimicos'), (7, 'Comercio y distribucion'), (8, 'Ganadero'), (9, 'Mineria'), (10, 'Contenedores y portuario');
-- Tipos de carga de las guias: al crear o editar una guia se elige uno de estos.
INSERT INTO tipos_carga (id, nombre) VALUES (1, 'Granos a granel'), (2, 'Soja a granel'), (3, 'Maiz a granel'), (4, 'Trigo'), (5, 'Arroz'), (6, 'Rollizos'), (7, 'Rollizos de eucalipto'), (8, 'Rollizos de pino'), (9, 'Madera aserrada'), (10, 'Materiales de obra'), (11, 'Arena'), (12, 'Ganado en pie'), (13, 'Contenedor'), (14, 'Carga refrigerada'), (15, 'Combustibles liquidos'), (16, 'Carga general');
