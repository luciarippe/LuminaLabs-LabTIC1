-- V13: el administrador puede bloquear cuentas y ocultar valoraciones. Nada se borra: las dos cosas se deshacen.
-- Bloquear: la persona no puede iniciar sesion (y se cierran sus sesiones abiertas). null = activa.
ALTER TABLE users ADD COLUMN blocked_at timestamp(6);
ALTER TABLE users ADD COLUMN blocked_reason varchar(500);

-- Ocultar: la valoracion deja de mostrarse y de contar en el promedio. null = visible.
ALTER TABLE ratings ADD COLUMN hidden_at timestamp(6);
ALTER TABLE ratings ADD COLUMN hidden_reason varchar(500);
