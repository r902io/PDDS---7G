-- corrige_almacenes: Q5 CAMBIO 08-sept (central 27,14 y este 57,27)
UPDATE almacen SET ubicacion_x = 27, ubicacion_y = 14 WHERE id_almacen = 'ALM-CENTRAL';
UPDATE almacen SET ubicacion_x = 57, ubicacion_y = 27 WHERE id_almacen = 'ALM-ESTE';
