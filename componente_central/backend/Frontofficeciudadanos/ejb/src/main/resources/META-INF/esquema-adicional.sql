-- Se ejecuta despues de crear el esquema desde las entidades (create-source = metadata-then-script).
-- Una sentencia por linea.
--
-- Un vehiculo no puede tener dos casos abiertos (DETECTADA o NOTIFICADA) del mismo tipo. La fiscalizacion
-- automatica decide si crea un caso nuevo; si dos de sus pods lo intentan a la vez para el mismo vehiculo,
-- el segundo falla aca, su evento queda pendiente y al reintentarlo se suma al caso que ya existe.
CREATE UNIQUE INDEX uk_caso_abierto ON casos_fiscalizacion (id_vehiculo, tipoCaso) WHERE estado IN ('DETECTADA', 'NOTIFICADA');
