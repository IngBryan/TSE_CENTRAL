-- Migracion no destructiva para bases existentes con schema-generation=none.
-- Hacer backup y ejecutar una unica vez ANTES de desplegar la nueva version de Modelo.
-- Con drop-and-create, Hibernate crea las columnas desde el modelo; NO ejecutar este script.
BEGIN;
ALTER TABLE guias ADD COLUMN IF NOT EXISTS id_localidad_origen bigint;
ALTER TABLE guias ADD COLUMN IF NOT EXISTS id_localidad_destino bigint;
-- No se actualizan datos de guias historicas: sus IDs quedan NULL.
-- Los textos siguen utilizandose para lectura y solo se resuelven si el nombre es unico.
-- Editar una guia permitida puede asociar sus localidades por ID mas adelante.
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_guias_localidad_origen') THEN
    ALTER TABLE guias ADD CONSTRAINT fk_guias_localidad_origen
      FOREIGN KEY (id_localidad_origen) REFERENCES localidades(id);
  END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_guias_localidad_destino') THEN
    ALTER TABLE guias ADD CONSTRAINT fk_guias_localidad_destino
      FOREIGN KEY (id_localidad_destino) REFERENCES localidades(id);
  END IF;
END $$;
COMMIT;
