/**
 * Tipo de vehículo deducido del código TTNN.
 *   TA = auto, TM = moto, TB = bicicleta.  Ej.: TA01, TM03, TB10.
 */

export type TipoVehiculo = 'AUTO' | 'MOTO' | 'BICICLETA';

const PREFIJOS: Record<string, TipoVehiculo> = {
  TA: 'AUTO',
  TM: 'MOTO',
  TB: 'BICICLETA',
};

/** Devuelve el tipo según el prefijo, o null si el código no sigue TTNN. */
export function tipoDesdeCodigo(idVehiculo: string | null | undefined): TipoVehiculo | null {
  if (!idVehiculo) return null;
  const codigo = idVehiculo.trim().toUpperCase();
  if (!/^T[AMB]\d+$/.test(codigo)) return null;
  return PREFIJOS[codigo.slice(0, 2)] ?? null;
}

export const ORDEN_TIPOS: TipoVehiculo[] = ['AUTO', 'MOTO', 'BICICLETA'];

export const ETIQUETA_TIPO: Record<TipoVehiculo, string> = {
  AUTO: 'Auto',
  MOTO: 'Moto',
  BICICLETA: 'Bicicleta',
};

export const ETIQUETA_TIPO_PLURAL: Record<TipoVehiculo, string> = {
  AUTO: 'Autos',
  MOTO: 'Motos',
  BICICLETA: 'Bicicletas',
};

/** Colores del prototipo: auto azul, moto violeta, bicicleta blanco. */
export const COLOR_TIPO: Record<TipoVehiculo, string> = {
  AUTO: '#60a5fa',
  MOTO: '#a78bfa',
  BICICLETA: '#e8eef7',
};

/** Color neutro para un código que no sigue el formato TTNN. */
export const COLOR_TIPO_DESCONOCIDO = '#a7b6c9';

export function colorDeVehiculo(idVehiculo: string): string {
  const t = tipoDesdeCodigo(idVehiculo);
  return t ? COLOR_TIPO[t] : COLOR_TIPO_DESCONOCIDO;
}

/** Etiquetas de los estados que el backend escribe en VehiculoSnapshot.estado. */
const ETIQUETA_ESTADO: Record<string, string> = {
  DISPONIBLE: 'Disponible',
  EN_RUTA: 'En ruta',
  EN_AVERIA: 'Averiado',
  EN_MANTENIMIENTO: 'En mantenimiento',
  RETORNANDO_ALMACEN: 'Retornando al almacén',
};

export function etiquetaEstadoVehiculo(estado: string | null | undefined): string {
  if (!estado) return 'No disponible';
  return ETIQUETA_ESTADO[estado] ?? estado;
}

export function estaAveriado(estado: string | null | undefined): boolean {
  return estado === 'EN_AVERIA';
}

export function estaEnMantenimiento(estado: string | null | undefined): boolean {
  return estado === 'EN_MANTENIMIENTO';
}
