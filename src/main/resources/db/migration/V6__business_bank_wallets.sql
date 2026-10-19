-- Se agregan las billeteras digitales a la lista de bancos donde un emprendimiento recibe los pagos (enum Bank)
ALTER TABLE businesses DROP CONSTRAINT businesses_bank_check;
ALTER TABLE businesses ADD CONSTRAINT businesses_bank_check
    CHECK (bank IN ('BROU','BHU','BANDES','BBVA','BTG_PACTUAL','CITIBANK','HERITAGE','ITAU',
                    'NACION_ARGENTINA','SANTANDER','SCOTIABANK',
                    'ANDA_DINERO_ELECTRONICO','MERCADO_PAGO','MIDINERO','OCA_BLUE','PREX'));
