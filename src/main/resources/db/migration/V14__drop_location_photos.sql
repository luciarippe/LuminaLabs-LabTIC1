-- V14: las localidades ya no tienen foto. Alcanza con la foto de cada departamento (V12): se usa en "Destinos"
-- de la portada y como imagen de las actividades que no tienen fotos propias. Al borrarse la columna se borra
-- tambien su clave foranea (fk_locations_photo). Cuando se agrego esta migracion ninguna localidad tenia foto.
ALTER TABLE locations DROP COLUMN photo_id;
