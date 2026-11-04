import { type SemaforoFiltro, type AlmacenOperativo } from '../types/api';

export type EstadoSemaforoAlmacen = 'VERDE' | 'AMBAR' | 'ROJO' | 'VACIO';

export function obtenerColorSemaforoAlmacen(a: AlmacenOperativo): EstadoSemaforoAlmacen {
  // Excepción Central o sin límite
  if (a.tipo === 'CENTRAL' || a.capacidadMaxima === null) return 'VERDE';

  const stock = a.stockActual ?? 0;
  if (stock === 0) return 'VACIO';

  const porcentaje = stock / a.capacidadMaxima;
  if (porcentaje >= 0.8) return 'ROJO';   // Ej. 1,000 / 1,000 = 100% -> ROJO
  if (porcentaje >= 0.5) return 'AMBAR';
  return 'VERDE';
}

export function obtenerColorHexAlmacen(semaforo: EstadoSemaforoAlmacen): string {
  switch (semaforo) {
    case 'ROJO': return '#ef4444';
    case 'AMBAR': return '#f59e0b';
    case 'VACIO': return '#9ca3af';
    case 'VERDE': default: return '#22c55e';
  }
}


/**
 * Calcula el estado de semáforo de un almacén según su capacidad y stock actual.
 * D07: Clasificación por semáforo de almacenes.
 */
export function obtenerEstadoSemaforoAlmacen(almacen: AlmacenOperativo): SemaforoFiltro {
  // Almacén central o ilimitado
  if (almacen.tipo === 'CENTRAL' || almacen.capacidadMaxima === null || almacen.stockActual === null) {
    return 'VERDE';
  }

  if (almacen.stockActual === 0) return 'VACIO';

  const porcentaje = (almacen.stockActual / almacen.capacidadMaxima) * 100;
  if (porcentaje > 70) return 'ROJO';
  if (porcentaje > 30) return 'AMARILLO';
  return 'VERDE';
}

/**
 * D07: Determina si un almacén debe visibilizarse según el semáforo seleccionado.
 */
export function esAlmacenVisible(
    almacen: AlmacenOperativo,
    semaforosSeleccionados: SemaforoFiltro[]
): boolean {
  const estado = obtenerEstadoSemaforoAlmacen(almacen);
  return semaforosSeleccionados.includes(estado);
}