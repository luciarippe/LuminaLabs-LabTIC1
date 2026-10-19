-- Redes sociales del emprendimiento (opcionales): se muestran en el detalle de sus actividades.
-- Se guardan como direcciones completas (https://...), ya armadas a partir de lo que escribio el prestador.
ALTER TABLE businesses ADD COLUMN website_url varchar(255);
ALTER TABLE businesses ADD COLUMN instagram_url varchar(255);
ALTER TABLE businesses ADD COLUMN facebook_url varchar(255);
ALTER TABLE businesses ADD COLUMN tiktok_url varchar(255);
