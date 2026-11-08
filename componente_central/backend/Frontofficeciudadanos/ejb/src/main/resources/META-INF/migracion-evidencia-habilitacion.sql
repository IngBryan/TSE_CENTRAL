-- Para bases existentes con schema-generation=none; ejecutar antes de desplegar los nuevos EAR.
-- Con generación del esquema desde JPA, la tabla se crea desde CasoFiscalizacion.
BEGIN;
CREATE TABLE IF NOT EXISTS casos_evidencias_habilitacion (
    id_caso bigint NOT NULL REFERENCES casos_fiscalizacion(id),
    matricula varchar(255) NOT NULL,
    fecha_hora timestamp(6) with time zone NOT NULL,
    origen varchar(255) NOT NULL,
    nroPermiso varchar(255),
    emisionPermiso date,
    vencimientoPermiso date,
    emisionItv date,
    vencimientoItv date,
    CONSTRAINT uk_caso_prueba_habilitacion UNIQUE (id_caso, matricula, fecha_hora, origen)
);
COMMIT;
