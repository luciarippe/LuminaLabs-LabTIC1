-- V9: referencia de cada pago ante la pasarela (ver Payment.reference).
-- Antes era "PAY-" + id, pero con Mercado Pago eso no alcanza: todo el equipo usa la misma cuenta de prueba y
-- cada uno tiene su propia base, asi que dos pagos distintos podian llegar con la misma referencia (y Mercado Pago
-- devolvia el resultado del otro en vez de cobrar). Ahora la referencia lleva un UUID.

ALTER TABLE payments ADD COLUMN reference varchar(64);
-- Los pagos que ya existian conservan su referencia de antes
UPDATE payments SET reference = (CASE WHEN type = 'REFUND' THEN 'REF-' ELSE 'PAY-' END) || id;
ALTER TABLE payments ALTER COLUMN reference SET NOT NULL;
ALTER TABLE payments ADD CONSTRAINT uk_payments_reference UNIQUE (reference);
