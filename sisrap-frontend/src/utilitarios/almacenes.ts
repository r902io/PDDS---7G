import type { AlmacenOperativo } from '../types/api';

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