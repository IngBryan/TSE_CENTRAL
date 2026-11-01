-- Se ejecuta despues de crear el esquema desde las entidades (create-source = metadata-then-script).
-- Una sentencia por linea.
--
-- Un vehiculo no puede tener dos casos abiertos (DETECTADA o NOTIFICADA) del mismo tipo. La fiscalizacion
-- automatica decide si crea un caso nuevo; si dos de sus pods lo intentan a la vez para el mismo vehiculo,
-- el segundo falla aca, su evento queda pendiente y al reintentarlo se suma al caso que ya existe.
CREATE UNIQUE INDEX uk_caso_abierto ON casos_fiscalizacion (id_vehiculo, tipoCaso) WHERE estado IN ('DETECTADA', 'NOTIFICADA');
--
-- Catalogos precargados: la letra los define como datos del sistema, sin funcionalidad de gestion. Las guias y
-- las empresas guardan el nombre; la clave foranea impide un valor que no este en el catalogo.
INSERT INTO rubros (nombre) VALUES ('Agropecuario'), ('Forestal'), ('Construccion'), ('Alimentos y bebidas'), ('Industria'), ('Comercio'), ('Combustibles y quimicos'), ('Otros');
INSERT INTO tipos_carga (nombre) VALUES ('Granos a granel'), ('Rollizos'), ('Madera aserrada'), ('Materiales de construccion'), ('Alimentos refrigerados'), ('Contenedores'), ('Combustibles y mercaderia peligrosa'), ('Carga general');
ALTER TABLE guias ADD CONSTRAINT fk_guia_rubro FOREIGN KEY (rubroCliente) REFERENCES rubros (nombre);
ALTER TABLE guias ADD CONSTRAINT fk_guia_tipo_carga FOREIGN KEY (tipoCarga) REFERENCES tipos_carga (nombre);
ALTER TABLE empresas ADD CONSTRAINT fk_empresa_rubro FOREIGN KEY (rubro) REFERENCES rubros (nombre);
