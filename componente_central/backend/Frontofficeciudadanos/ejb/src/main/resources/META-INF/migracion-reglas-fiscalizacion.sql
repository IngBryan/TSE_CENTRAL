-- Ejecutar antes de desplegar todos los EAR y el daemon (schema-generation=none).
BEGIN;
CREATE TABLE IF NOT EXISTS reglas_fiscalizacion_revisiones (
    revision bigint PRIMARY KEY,
    tolerancia_porcentaje numeric(5,2) NOT NULL CHECK (tolerancia_porcentaje BETWEEN 0 AND 20),
    tolerancia_kg integer NOT NULL CHECK (tolerancia_kg BETWEEN 0 AND 5000),
    distancia_metros integer NOT NULL CHECK (distancia_metros BETWEEN 1 AND 10000),
    ventana_segundos integer NOT NULL CHECK (ventana_segundos BETWEEN 1 AND 600),
    creada_en timestamptz NOT NULL, usuario varchar(64) NOT NULL, fundamento varchar(500) NOT NULL,
    correlation_id varchar(64), publicada_en timestamptz
);
CREATE TABLE IF NOT EXISTS reglas_fiscalizacion_vigente (
    id bigint PRIMARY KEY CHECK (id=1), revision bigint NOT NULL REFERENCES reglas_fiscalizacion_revisiones,
    version bigint NOT NULL DEFAULT 0
);
CREATE TABLE IF NOT EXISTS casos_evidencias_reglas (
    id_caso bigint NOT NULL REFERENCES casos_fiscalizacion,
    matricula varchar(255) NOT NULL, fecha_hora timestamptz NOT NULL, origen varchar(255) NOT NULL,
    revision bigint NOT NULL REFERENCES reglas_fiscalizacion_revisiones, peso_base_kg bigint,
    UNIQUE (id_caso,matricula,fecha_hora,origen)
);
ALTER TABLE eventos_pesajes ADD COLUMN IF NOT EXISTS revision_reglas bigint;
INSERT INTO reglas_fiscalizacion_revisiones (revision,tolerancia_porcentaje,tolerancia_kg,distancia_metros,ventana_segundos,creada_en,usuario,fundamento)
VALUES (1,0,0,2000,30,CURRENT_TIMESTAMP,'SISTEMA','Configuración inicial') ON CONFLICT DO NOTHING;
INSERT INTO reglas_fiscalizacion_vigente (id,revision,version) VALUES (1,1,0) ON CONFLICT DO NOTHING;
COMMIT;
