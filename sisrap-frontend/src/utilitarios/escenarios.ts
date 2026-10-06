import type { EscenarioSimulacion, EstadoSimulacion, SnapshotSimulacion } from '../types/api';

export interface DescripcionEscenario {
  id: EscenarioSimulacion;
  titulo: string;
  tituloCorto: string;
  resumen: string;
  condicionTermino: string;
  /** Días totales conocidos de antemano; null si la duración no se conoce. */
  diasTotales: number | null;
}

export const ESCENARIOS: Record<EscenarioSimulacion, DescripcionEscenario> = {
  OPERACION_DIARIA: {
    id: 'OPERACION_DIARIA',
    titulo: 'Operación día a día',
    tituloCorto: 'Día a día',
    resumen:
      'Operación continua: los pedidos ingresan conforme llegan y el planificador replanifica en tiempo real.',
    condicionTermino: 'No termina por sí sola. Solo se detiene manualmente.',
    diasTotales: null,
  },
  SIMULACION_CINCO_DIAS: {
    id: 'SIMULACION_CINCO_DIAS',
    titulo: 'Simulación de cinco días',
    tituloCorto: 'Cinco días',
    resumen:
      'Simula cinco jornadas completas de operación desde la fecha de inicio elegida.',
    condicionTermino: 'Termina al completar 120 horas simuladas (5 días).',
    diasTotales: 5,
  },
  COLAPSO_LOGISTICO: {
    id: 'COLAPSO_LOGISTICO',
    titulo: 'Simulación hasta el colapso logístico',
    tituloCorto: 'Colapso logístico',
    resumen:
      'Prueba de estrés: la operación continúa hasta que un pedido no se entrega dentro de su plazo.',
    condicionTermino: 'Termina en el primer incumplimiento de un plazo.',
    diasTotales: null,
  },
};

export const ORDEN_ESCENARIOS: EscenarioSimulacion[] = [
  'OPERACION_DIARIA',
  'SIMULACION_CINCO_DIAS',
  'COLAPSO_LOGISTICO',
];

export const ETIQUETA_ESTADO_SIMULACION: Record<EstadoSimulacion, string> = {
  DETENIDA: 'Detenida',
  EJECUTANDO: 'En ejecución',
  PAUSADA: 'Pausada (informado por el backend)',
  FINALIZADA: 'Finalizada',
  ERROR: 'Error',
};

/** Una corrida está activa si el backend la reporta ejecutando o pausada. */
export function corridaActiva(s: SnapshotSimulacion | null): boolean {
  return s != null && (s.estado === 'EJECUTANDO' || s.estado === 'PAUSADA');
}

/** Hubo una corrida (activa o terminada) que se puede mostrar. */
export function hayCorrida(s: SnapshotSimulacion | null): boolean {
  return s != null && s.idSimulacion != null && s.escenario != null;
}

/**
 * Regla del caso: el primer incumplimiento de un plazo es el colapso.
 * No existe un acumulado de pedidos retrasados tolerable.
 */
export function enColapso(s: SnapshotSimulacion | null): boolean {
  return s != null && s.pedidos != null && s.pedidos.retrasados > 0;
}

export type TipoResultado = 'COMPLETADA' | 'COLAPSO' | 'DETENIDA_MANUAL' | 'ERROR';

/**
 * Clasifica una corrida terminada.
 * - COLAPSO: hay al menos un pedido retrasado (en cualquier escenario).
 * - ERROR: el backend reporta ERROR (incluye falta de datos).
 * - COMPLETADA: FINALIZADA sin retrasos (llegó a su condición de término).
 * - DETENIDA_MANUAL: DETENIDA con una corrida previa (la detuvo el controlador).
 */
export function tipoResultado(s: SnapshotSimulacion): TipoResultado | null {
  if (corridaActiva(s) || !hayCorrida(s)) return null;
  if (enColapso(s)) return 'COLAPSO';
  if (s.estado === 'ERROR') return 'ERROR';
  if (s.estado === 'FINALIZADA') return 'COMPLETADA';
  return 'DETENIDA_MANUAL';
}
