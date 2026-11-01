-- Se ejecuta tras crear el esquema desde entidades (metadata-then-script).
-- La Guía identifica el caso abierto si existe; sin Guía, identifica el vehículo.
CREATE UNIQUE INDEX uk_caso_abierto_guia ON casos_fiscalizacion (id_guia, tipoCaso) WHERE id_guia IS NOT NULL AND estado IN ('DETECTADA', 'NOTIFICADA');
CREATE UNIQUE INDEX uk_caso_abierto_sin_guia ON casos_fiscalizacion (id_vehiculo, tipoCaso) WHERE id_guia IS NULL AND estado IN ('DETECTADA', 'NOTIFICADA');
