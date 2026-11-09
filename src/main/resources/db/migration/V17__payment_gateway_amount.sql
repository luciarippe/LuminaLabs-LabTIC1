-- V17: monto del movimiento tal como lo informa la pasarela (Mercado Pago o la simulada), con signo.
-- En una devolucion es negativo: por ejemplo -1800.00 cuando se le devuelven $ 1.800 al turista.
-- null en los cobros y en las devoluciones que todavia no se confirmaron.
ALTER TABLE payments ADD COLUMN gateway_amount numeric(12, 2);
