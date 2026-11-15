-- Aplicar en una base existente antes de desplegar. No altera casos ni reglas del motor.
BEGIN;
CREATE TABLE IF NOT EXISTS configuracion_descargos (
    id BIGINT PRIMARY KEY CHECK (id = 1),
    revision BIGINT NOT NULL,
    plazo_dias INTEGER NOT NULL CHECK (plazo_dias BETWEEN 1 AND 365),
    actualizada_en TIMESTAMP WITH TIME ZONE NOT NULL,
    usuario VARCHAR(64) NOT NULL,
    fundamento VARCHAR(500) NOT NULL
);
INSERT INTO configuracion_descargos (id, revision, plazo_dias, actualizada_en, usuario, fundamento) VALUES (1, 1, 10, CURRENT_TIMESTAMP, 'SISTEMA', 'Configuración inicial') ON CONFLICT (id) DO NOTHING;
COMMIT;
