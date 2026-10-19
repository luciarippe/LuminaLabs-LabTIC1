-- El banco deja de ser texto libre y pasa a elegirse de la lista de bancos habilitados (enum Bank).
-- Los emprendimientos existentes se asignan segun el texto que tenian; si no se reconoce, quedan en BROU
-- (solo pasa con datos de prueba: el administrador puede revisarlos en el detalle del emprendimiento).
ALTER TABLE businesses ADD COLUMN bank varchar(30);

UPDATE businesses SET bank = CASE
    WHEN bank_name ILIKE '%hipotecario%' OR bank_name ILIKE '%bhu%' THEN 'BHU'
    WHEN bank_name ILIKE '%bandes%' THEN 'BANDES'
    WHEN bank_name ILIKE '%bbva%' THEN 'BBVA'
    WHEN bank_name ILIKE '%btg%' OR bank_name ILIKE '%hsbc%' THEN 'BTG_PACTUAL'
    WHEN bank_name ILIKE '%citi%' THEN 'CITIBANK'
    WHEN bank_name ILIKE '%heritage%' THEN 'HERITAGE'
    WHEN bank_name ILIKE '%ita%' THEN 'ITAU'
    WHEN bank_name ILIKE '%naci%n%' THEN 'NACION_ARGENTINA'
    WHEN bank_name ILIKE '%santander%' THEN 'SANTANDER'
    WHEN bank_name ILIKE '%scotia%' THEN 'SCOTIABANK'
    ELSE 'BROU'
END;

ALTER TABLE businesses ALTER COLUMN bank SET NOT NULL;
ALTER TABLE businesses ADD CONSTRAINT businesses_bank_check
    CHECK (bank IN ('BROU','BHU','BANDES','BBVA','BTG_PACTUAL','CITIBANK','HERITAGE','ITAU',
                    'NACION_ARGENTINA','SANTANDER','SCOTIABANK'));
ALTER TABLE businesses DROP COLUMN bank_name;
