import type {
  AsignacionEnvio,
  EnvioSimulacion,
  EstadoAsignacion,
  PuntoSimulacion,
} from '../types/api';
import { formatoCoordenada, formatoHora, partesFechaHora } from './formato';

/** Ruta de una unidad resaltada en el mapa: lo real va continuo y lo estimado, discontinuo. */
export interface RutaDestacada {
  idVehiculo: string;
  real: PuntoSimulacion[];
  estimada: PuntoSimulacion[];
  destino: PuntoSimulacion;
}

export const ETIQUETA_ASIGNACION: Record<EstadoAsignacion, string> = {
  SIN_ASIGNAR: 'Sin asignar',
  PROGRAMADA: 'Por salir',
  EN_RUTA: 'En ruta',
  EN_ATENCION: 'Descargando',
  ENTREGADA: 'Entregado',
  REASIGNADA: 'Reasignado',
  TRASVASADA: 'Trasvasado',
};

/** Hora, con la fecha solo si no es la del reloj simulado. */
export function horaEnContexto(iso: string | null | undefined, fechaReloj: string | null): string {
  const p = partesFechaHora(iso);
  if (!p) return 'Sin dato';
  if (iso?.slice(0, 10) === fechaReloj) return formatoHora(iso);
  return `${String(p.dia).padStart(2, '0')}/${String(p.mes).padStart(2, '0')} ${formatoHora(iso)}`;
}

/** Une números seguidos: 7-1 a 7-4, 7-9. */
export function formatoProductos(productos: string[]): string {
  const tramos: string[] = [];
  let inicio: string | null = null;
  let previo: string | null = null;
  const numero = (id: string) => Number(id.slice(id.lastIndexOf('-') + 1));
  const cerrar = () => {
    if (inicio == null || previo == null) return;
    tramos.push(inicio === previo ? inicio : `${inicio} a ${previo}`);
  };
  for (const id of productos) {
    if (previo != null && numero(id) === numero(previo) + 1) {
      previo = id;
      continue;
    }
    cerrar();
    inicio = id;
    previo = id;
  }
  cerrar();
  return tramos.join(', ');
}

export function formatoRuta(puntos: PuntoSimulacion[]): string {
  return puntos.map((p) => formatoCoordenada(p.x, p.y)).join(' → ');
}

/** Lo recorrido más el tramo en curso, sin repetir el punto donde se unen. */
export function rutaReal(a: AsignacionEnvio): PuntoSimulacion[] {
  const ultimo = a.rutaRecorrida[a.rutaRecorrida.length - 1];
  const primero = a.rutaPendiente[0];
  const repetido = ultimo && primero && ultimo.x === primero.x && ultimo.y === primero.y;
  return [...a.rutaRecorrida, ...(repetido ? a.rutaPendiente.slice(1) : a.rutaPendiente)];
}

export function esVigente(a: AsignacionEnvio): boolean {
  return a.estado !== 'REASIGNADA' && a.estado !== 'TRASVASADA';
}

/** Rutas de cada unidad que lleva o llevó el envío. Una unidad reasignada ya no lo lleva. */
export function rutasDeEnvio(envio: EnvioSimulacion): RutaDestacada[] {
  const rutas: RutaDestacada[] = [];
  for (const a of envio.asignaciones) {
    if (a.idVehiculo == null || a.estado === 'REASIGNADA') continue;
    const real = rutaReal(a);
    if (real.length < 2 && a.rutaEstimada.length < 2) continue;
    rutas.push({ idVehiculo: a.idVehiculo, real, estimada: a.rutaEstimada, destino: envio.destino });
  }
  return rutas;
}

export function unidadesDeEnvio(envio: EnvioSimulacion): string[] {
  const ids = envio.asignaciones
    .filter((a) => a.idVehiculo != null && a.estado !== 'REASIGNADA')
    .map((a) => a.idVehiculo as string);
  return [...new Set(ids)];
}
