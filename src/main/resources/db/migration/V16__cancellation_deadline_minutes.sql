-- V16: el plazo de cancelacion pasa de horas a minutos, como la duracion y el limite para reservar.
-- Todos los tiempos se eligen en horas, de a 30 minutos, asi que el plazo puede ser por ejemplo 1,5 h (90 minutos).
ALTER TABLE activities ADD COLUMN cancellation_deadline_minutes integer;
UPDATE activities SET cancellation_deadline_minutes = cancellation_deadline_hours * 60;
ALTER TABLE activities ALTER COLUMN cancellation_deadline_minutes SET NOT NULL;
ALTER TABLE activities DROP COLUMN cancellation_deadline_hours;
