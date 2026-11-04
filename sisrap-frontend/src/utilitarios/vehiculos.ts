/**
 * Tipo de vehículo deducido del código TTNN.
 *   TA = auto, TM = moto, TB = bicicleta.  Ej.: TA01, TM03, TB10.
 */
import type { TipoAveria, VehiculoSnapshot, TipoFlota, SemaforoFiltro, VehiculoOperativo } from '../types/api';
import type { NombreForma } from '../components/iconos/formas';

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

export const CAMPOS_ORDEN_UNIDADES = [
  'horaSalida',
  'horaLlegadaEstimada',
  'distanciaPorRecorrerKm',
  'distanciaRecorridaKm',
] as const;

export type CampoOrdenUnidad = (typeof CAMPOS_ORDEN_UNIDADES)[number];
export type SentidoOrden = 'asc' | 'desc';

/**
 * Las unidades sin dato van al final en ambos sentidos. El código desempata
 * para que el orden no salte entre un snapshot y el siguiente.
 */
export function ordenarUnidades(
  vehiculos: VehiculoSnapshot[],
  campo: CampoOrdenUnidad,
  sentido: SentidoOrden
): VehiculoSnapshot[] {
  const factor = sentido === 'asc' ? 1 : -1;
  return [...vehiculos].sort((a, b) => {
    const va = a[campo] ?? null;
    const vb = b[campo] ?? null;
    if (va == null && vb != null) return 1;
    if (vb == null && va != null) return -1;
    if (va != null && vb != null && va !== vb) return (va < vb ? -1 : 1) * factor;
    return a.idVehiculo.localeCompare(b.idVehiculo);
  });
}

export function estaAveriado(estado: string | null | undefined): boolean {
  return estado === 'EN_AVERIA';
}

export function estaEnMantenimiento(estado: string | null | undefined): boolean {
  return estado === 'EN_MANTENIMIENTO';
}
/**
 * Retorna la forma de la avería según el tipo (1 = Leve 2h, 2 = Moderada sig. turno, 3 = Grave 2 días).
 */
export function obtenerFormaAveria(tipoAveria?: TipoAveria | string | number | null): NombreForma {
  if (!tipoAveria) return 'averiaTipo1';
  const val = String(tipoAveria).toUpperCase();
  if (val.includes('3') || val.includes('MAYOR')) return 'averiaTipo3';
  if (val.includes('2') || val.includes('INTERMEDIA')) return 'averiaTipo2';
  return 'averiaTipo1';
}
export function obtenerEtiquetaAveria(tipoAveria?: string | number | null): string {
  if (!tipoAveria) return 'Tipo 1 (Leve - 2h)';
  const val = String(tipoAveria).toUpperCase();
  if (val.includes('3') || val.includes('MAYOR')) return 'Tipo 3 (Grave - 2 días)';
  if (val.includes('2') || val.includes('INTERMEDIA')) return 'Tipo 2 (Moderada - Sig. turno)';
  return 'Tipo 1 (Leve - 2h)';
}

export function resolverFormaAveria(
    tipoAveria?: string | number | null
): 'averiaTipo1' | 'averiaTipo2' | 'averiaTipo3' {
  if (!tipoAveria) return 'averiaTipo1';

  const val = String(tipoAveria).toUpperCase();
  if (val.includes('3') || val.includes('MAYOR')) return 'averiaTipo3';
  if (val.includes('2') || val.includes('INTERMEDIA')) return 'averiaTipo2';
  return 'averiaTipo1';
}
/**
 * Retorna el código Hexadecimal asociado al tipo de avería para insignias y tablas.
 */
export function obtenerColorAveriaHex(tipoAveria?: string | null): string {  const num = Number(tipoAveria);
  if (num === 3) return '#e11d48'; // Rojo Carmín
  if (num === 2) return '#f97316'; // Naranja
  return '#f59e0b';               // Ámbar
}


/**
 * Calcula el estado de semáforo/ocupación de un vehículo según su carga actual y capacidad.
 * D06: Clasificación por semáforo (Vacío, Verde <50%, Amarillo 50-79%, Rojo >=80%).
 */
export function obtenerEstadoSemaforoVehiculo(
    cargaActual: number,
    capacidadMax: number
): SemaforoFiltro {
  if (cargaActual === 0) return 'VACIO';
  if (capacidadMax <= 0) return 'VERDE';

  const porcentaje = (cargaActual / capacidadMax) * 100;
  if (porcentaje >= 80) return 'ROJO';
  if (porcentaje >= 50) return 'AMARILLO';
  return 'VERDE';
}

/**
 * D05 & D06: Determina si un VehiculoSnapshot o VehiculoOperativo debe renderizarse en el mapa.
 */
export function esVehiculoVisible(
    vehiculo: VehiculoSnapshot | VehiculoOperativo,
    tiposSeleccionados: TipoFlota[],
    semaforosSeleccionados: SemaforoFiltro[]
): boolean {
  // Normalizar el tipo de vehículo a TipoFlota ('AUTO' | 'MOTO' | 'BICICLETA')
  const tipoStr = ('tipo' in vehiculo ? vehiculo.tipo : tipoDesdeCodigo(vehiculo.idVehiculo) ?? '').toUpperCase();

  let tipoFlota: TipoFlota = 'AUTO';
  if (tipoStr.includes('MOTO')) tipoFlota = 'MOTO';
  else if (tipoStr.includes('BICI')) tipoFlota = 'BICICLETA';

  // 1. Filtro D05: Tipo de transporte
  if (!tiposSeleccionados.includes(tipoFlota)) {
    return false;
  }

  // Obtenemos la carga y capacidad según la interfaz recibida
  const carga = 'cargaActualPaquetes' in vehiculo ? vehiculo.cargaActualPaquetes : 0;
  const capacidad = vehiculo.capacidadPaquetes || 0;

  // 2. Filtro D06: Semáforo + Vacío
  const estadoSemaforo = obtenerEstadoSemaforoVehiculo(carga, capacidad);
  return semaforosSeleccionados.includes(estadoSemaforo);
}
