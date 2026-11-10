-- Ejecutar antes de desplegar Modelo y los tres EAR (schema-generation=none).
-- Cambio de contrasena obligatorio en el primer ingreso al backoffice. Los usuarios existentes no quedan
-- obligados (DEFAULT FALSE): solo los que se den de alta, o pasen de funcionario al backoffice, despues.
ALTER TABLE usuarios_backoffice
    ADD COLUMN IF NOT EXISTS debe_cambiar_contrasena boolean NOT NULL DEFAULT FALSE;
