-- ajusta_experimentacion_colapso
-- El plazo se cumple cuando termina la atención de 1h, no solo cuando el vehículo llega.
UPDATE perfil_parametro
SET valor = 'true'
WHERE clave = 'operacion.servicioDentroPlazo';

-- La presión de COLAPSO_LOGISTICO aumenta dentro de cada corrida.
-- Se agregan los parámetros a todos los perfiles existentes sin pisar un valor ya definido.
INSERT IGNORE INTO perfil_parametro(perfil, clave, valor)
SELECT DISTINCT perfil, 'experimento.colapso.crecimientoDiario', '0.05'
FROM perfil_parametro;

INSERT IGNORE INTO perfil_parametro(perfil, clave, valor)
SELECT DISTINCT perfil, 'experimento.colapso.maxDias', '365'
FROM perfil_parametro;
