-- Se ejecuta tras crear el esquema desde entidades (metadata-then-script).
-- La Guía identifica el caso abierto si existe; sin Guía, identifica el vehículo.
CREATE UNIQUE INDEX uk_caso_abierto_guia ON casos_fiscalizacion (id_guia, tipoCaso) WHERE id_guia IS NOT NULL AND estado IN ('DETECTADA', 'NOTIFICADA');
-- Para VIAJE_SIN_GUIA el caso pertenece a una circulación y no a una matrícula genérica.
CREATE UNIQUE INDEX uk_caso_abierto_sin_guia ON casos_fiscalizacion (id_vehiculo, tipoCaso)
 WHERE id_guia IS NULL AND tipoCaso <> 'VIAJE_SIN_GUIA' AND estado IN ('DETECTADA', 'NOTIFICADA');
-- Una sola observación pendiente o circulación activa por vehículo (independiente del estado administrativo).
CREATE UNIQUE INDEX uk_circulacion_vigente_vehiculo ON circulaciones_sin_guia (id_vehiculo)
 WHERE estado IN ('PENDIENTE_MOVIMIENTO', 'ACTIVA');
