/**
 * Tipo de vehículo deducido del código TTNN.
 *   TA = auto, TM = moto, TB = bicicleta.  Ej.: TA01, TM03, TB10.
 */
import type { TipoAveria, VehiculoSnapshot, TipoFlota, SemaforoFiltro, VehiculoOperativo } from '../types/api';
import type { NombreForma } from '../components/iconos/formas';
import { horasEntre } from './formato';

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
  'ocupacion',
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
 * El campo 'ocupacion' (E13) se calcula al vuelo desde carga/capacidad.
 */
export function ordenarUnidades(
  vehiculos: VehiculoSnapshot[],
  campo: CampoOrdenUnidad,
  sentido: SentidoOrden
): VehiculoSnapshot[] {
  const factor = sentido === 'asc' ? 1 : -1;
  const valor = (v: VehiculoSnapshot): number | string | null =>
    campo === 'ocupacion' ? ocupacionVehiculo(v) : (v[campo] ?? null);
  return [...vehiculos].sort((a, b) => {
    const va = valor(a);
    const vb = valor(b);
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

// ===========================================================================
// E06 a E13: búsqueda, filtros permanentes y orden de la Lista de UT del panel.
// Reutiliza obtenerEstadoSemaforoVehiculo (D06) para no duplicar los umbrales.
// Todas las UT salen del almacén central, así que el "origen" es ese punto y
// el tiempo/km al origen se mide desde la salida de la unidad.
// ===========================================================================

/** Ocupación de la UT en porcentaje (0..100), o null si no se puede calcular. E02/E13. */
export function ocupacionVehiculo(v: VehiculoSnapshot): number | null {
  const cap = v.capacidadPaquetes ?? 0;
  if (cap <= 0) return null;
  const carga = v.cargaActualPaquetes ?? 0;
  return (carga / cap) * 100;
}

/** Semáforo de la UT a partir de su snapshot (envuelve la función D06). E03/E12. */
export function semaforoVehiculo(v: VehiculoSnapshot): SemaforoFiltro {
  return obtenerEstadoSemaforoVehiculo(v.cargaActualPaquetes ?? 0, v.capacidadPaquetes ?? 0);
}

/**
 * E06: minutos que faltan para que la UT llegue a su destino inmediato, medidos
 * desde el reloj simulado. Negativo/null si no hay dato.
 */
export function minutosHastaLlegada(
  reloj: string | null | undefined,
  horaLlegada: string | null | undefined
): number | null {
  const h = horasEntre(reloj, horaLlegada);
  return h == null ? null : h * 60;
}

/**
 * E07: minutos transcurridos desde que la UT salió de su origen (almacén
 * central), medidos con el reloj simulado. Null si aún no ha salido.
 */
export function minutosDesdeSalida(
  reloj: string | null | undefined,
  horaSalida: string | null | undefined
): number | null {
  const h = horasEntre(horaSalida, reloj);
  return h == null ? null : h * 60;
}

/** Referencia de una búsqueda/orden por distancia: al destino (llegada) o al origen (salida). E06-E09. */
export type ReferenciaDistancia = 'DESTINO' | 'ORIGEN';

/**
 * E11: rangos permanentes de distancia en km. El último tramo llega hasta 200 km
 * (diagonal máxima de la ciudad de 70x50 es ~120 km, así que cubre cualquier caso).
 */
export const RANGOS_DISTANCIA_KM = [
  { id: '0-10', etiqueta: '[0, 10) km', min: 0, max: 10 },
  { id: '10-20', etiqueta: '[10, 20) km', min: 10, max: 20 },
  { id: '20-40', etiqueta: '[20, 40) km', min: 20, max: 40 },
  { id: '40-200', etiqueta: '[40, 200) km', min: 40, max: 200 },
] as const;

export type RangoDistanciaId = (typeof RANGOS_DISTANCIA_KM)[number]['id'];

/** Km de la UT según la referencia: por recorrer (al destino) o recorridos (desde el origen). */
export function kmSegunReferencia(v: VehiculoSnapshot, referencia: ReferenciaDistancia): number | null {
  const km = referencia === 'DESTINO' ? v.distanciaPorRecorrerKm : v.distanciaRecorridaKm;
  return km ?? null;
}

/** True si `km` cae en alguno de los rangos seleccionados (semiabierto [min, max)). E11. */
function kmEnRangos(km: number | null, rangos: RangoDistanciaId[]): boolean {
  if (km == null) return false;
  return rangos.some((id) => {
    const r = RANGOS_DISTANCIA_KM.find((x) => x.id === id);
    return !!r && km >= r.min && km < r.max;
  });
}

/** Estado de los filtros permanentes de la Lista de UT (E10, E11, E12). */
export interface FiltrosListaUT {
  /** E10: tipos de UT visibles. Vacío = ninguno. */
  tipos: TipoVehiculo[];
  /** E11: rangos de distancia activos. Vacío = sin filtro de distancia. */
  rangosKm: RangoDistanciaId[];
  /** E11: referencia del rango de distancia (al destino o al origen). */
  referenciaKm: ReferenciaDistancia;
  /** E12: niveles de semáforo visibles. Vacío = ninguno. */
  semaforos: SemaforoFiltro[];
}

export const FILTROS_LISTA_UT_INICIAL: FiltrosListaUT = {
  tipos: ['AUTO', 'MOTO', 'BICICLETA'],
  rangosKm: [],
  referenciaKm: 'DESTINO',
  semaforos: ['VERDE', 'AMARILLO', 'ROJO', 'VACIO'],
};

/**
 * Aplica los filtros permanentes E10/E11/E12 a una lista de UT. Se combinan
 * entre sí (AND) y el resultado sigue siendo operable por la búsqueda y el orden.
 */
export function filtrarUnidades(vehiculos: VehiculoSnapshot[], f: FiltrosListaUT): VehiculoSnapshot[] {
  return vehiculos.filter((v) => {
    // E10: tipo de UT.
    const tipo = tipoDesdeCodigo(v.idVehiculo);
    if (!tipo || !f.tipos.includes(tipo)) return false;
    // E12: semáforo de capacidad actual.
    if (!f.semaforos.includes(semaforoVehiculo(v))) return false;
    // E11: rango de distancia (solo si hay rangos activos).
    if (f.rangosKm.length > 0 && !kmEnRangos(kmSegunReferencia(v, f.referenciaKm), f.rangosKm)) return false;
    return true;
  });
}

/** Criterio de la búsqueda E06-E09: por minutos o km, al destino o al origen. */
export type CriterioBusqueda = 'MINUTOS' | 'KM';

/**
 * E06-E09: devuelve el valor de la UT para el criterio de búsqueda elegido.
 * - MINUTOS + DESTINO (E06): minutos hasta la llegada.
 * - MINUTOS + ORIGEN  (E07): minutos desde la salida.
 * - KM + DESTINO (E08): km por recorrer.
 * - KM + ORIGEN  (E09): km recorridos.
 */
export function valorBusqueda(
  v: VehiculoSnapshot,
  criterio: CriterioBusqueda,
  referencia: ReferenciaDistancia,
  reloj: string | null | undefined
): number | null {
  if (criterio === 'MINUTOS') {
    return referencia === 'DESTINO'
      ? minutosHastaLlegada(reloj, v.horaLlegadaEstimada)
      : minutosDesdeSalida(reloj, v.horaSalida);
  }
  return kmSegunReferencia(v, referencia);
}
