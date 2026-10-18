/**
 * Formato de fechas, horas, cifras y coordenadas.
 *
 * Las fechas del backend son LocalDateTime sin zona horaria. Se leen como
 * texto y se descomponen a mano: nunca se pasan por `new Date(iso)` (que
 * las interpretaría como hora local del navegador o como UTC según el
 * formato) ni se les agrega "Z".
 */

export const NO_DISPONIBLE = 'No disponible';

export interface PartesFechaHora {
  anio: number;
  mes: number; // 1..12
  dia: number;
  hora: number;
  minuto: number;
  segundo: number;
}

const RE_FECHA_HORA = /^(\d{4})-(\d{2})-(\d{2})(?:[T ](\d{2}):(\d{2})(?::(\d{2})(?:\.\d+)?)?)?$/;

/** Descompone "AAAA-MM-DD[THH:MM[:SS[.fff]]]" sin aplicar zona horaria. */
export function partesFechaHora(iso: string | null | undefined): PartesFechaHora | null {
  if (!iso) return null;
  const m = RE_FECHA_HORA.exec(iso.trim());
  if (!m) return null;
  return {
    anio: Number(m[1]),
    mes: Number(m[2]),
    dia: Number(m[3]),
    hora: Number(m[4] ?? 0),
    minuto: Number(m[5] ?? 0),
    segundo: Number(m[6] ?? 0),
  };
}

const dos = (n: number) => String(n).padStart(2, '0');

/** Milisegundos "de pared" (como si fuese UTC) para restar dos fechas locales. */
function msPared(p: PartesFechaHora): number {
  return Date.UTC(p.anio, p.mes - 1, p.dia, p.hora, p.minuto, p.segundo);
}

/** "DD/MM/AAAA" */
export function formatoFecha(iso: string | null | undefined): string {
  const p = partesFechaHora(iso);
  return p ? `${dos(p.dia)}/${dos(p.mes)}/${p.anio}` : NO_DISPONIBLE;
}

/** "HH:MM" */
export function formatoHora(iso: string | null | undefined): string {
  const p = partesFechaHora(iso);
  return p ? `${dos(p.hora)}:${dos(p.minuto)}` : NO_DISPONIBLE;
}

/** "HH:MM:SS" */
export function formatoHoraSegundos(iso: string | null | undefined): string {
  const p = partesFechaHora(iso);
  return p ? `${dos(p.hora)}:${dos(p.minuto)}:${dos(p.segundo)}` : NO_DISPONIBLE;
}

/** "DD/MM/AAAA HH:MM" */
export function formatoFechaHora(iso: string | null | undefined): string {
  const p = partesFechaHora(iso);
  return p ? `${dos(p.dia)}/${dos(p.mes)}/${p.anio} ${dos(p.hora)}:${dos(p.minuto)}` : NO_DISPONIBLE;
}

const DIAS_SEMANA = ['domingo', 'lunes', 'martes', 'miércoles', 'jueves', 'viernes', 'sábado'];

/** Nombre del día de la semana de una fecha local. */
export function diaSemana(iso: string | null | undefined): string {
  const p = partesFechaHora(iso);
  if (!p) return NO_DISPONIBLE;
  return DIAS_SEMANA[new Date(Date.UTC(p.anio, p.mes - 1, p.dia)).getUTCDay()];
}

/** Horas transcurridas entre dos LocalDateTime (b - a). */
export function horasEntre(a: string | null | undefined, b: string | null | undefined): number | null {
  const pa = partesFechaHora(a);
  const pb = partesFechaHora(b);
  if (!pa || !pb) return null;
  return (msPared(pb) - msPared(pa)) / 3_600_000;
}

/**
 * Día simulado (1, 2, 3...) contado desde la fecha de inicio de la corrida.
 * Se cuenta por fecha calendario: el día 1 es el día de inicio.
 */
export function diaSimulado(inicio: string | null | undefined, reloj: string | null | undefined): number | null {
  const pi = partesFechaHora(inicio);
  const pr = partesFechaHora(reloj);
  if (!pi || !pr) return null;
  const d0 = Date.UTC(pi.anio, pi.mes - 1, pi.dia);
  const d1 = Date.UTC(pr.anio, pr.mes - 1, pr.dia);
  return Math.floor((d1 - d0) / 86_400_000) + 1;
}

/** Fecha local de hoy en el navegador, "AAAA-MM-DD" (para el selector). */
export function hoyLocal(): string {
  const h = new Date();
  return `${h.getFullYear()}-${dos(h.getMonth() + 1)}-${dos(h.getDate())}`;
}

/** Hora local del navegador "HH:MM:SS" (para "última actualización"). */
export function horaNavegador(ms: number | null): string {
  if (ms == null) return NO_DISPONIBLE;
  const d = new Date(ms);
  return `${dos(d.getHours())}:${dos(d.getMinutes())}:${dos(d.getSeconds())}`;
}

// ---------------------------------------------------------------------------
// Turnos: cambios a las 07:00, 15:00 y 23:00
// ---------------------------------------------------------------------------

export type Turno = 'T1' | 'T2' | 'T3';

export function turnoDe(iso: string | null | undefined): Turno | null {
  const p = partesFechaHora(iso);
  if (!p) return null;
  if (p.hora >= 7 && p.hora < 15) return 'T1';
  if (p.hora >= 15 && p.hora < 23) return 'T2';
  return 'T3';
}

export const ETIQUETA_TURNO: Record<Turno, string> = {
  T1: 'Turno 1 · 07:00–15:00',
  T2: 'Turno 2 · 15:00–23:00',
  T3: 'Turno 3 · 23:00–07:00',
};

// ---------------------------------------------------------------------------
// Cifras
// ---------------------------------------------------------------------------

const fmtEntero = new Intl.NumberFormat('es-PE', { maximumFractionDigits: 0 });
const fmtDecimal1 = new Intl.NumberFormat('es-PE', { minimumFractionDigits: 1, maximumFractionDigits: 1 });
const fmtDecimal2 = new Intl.NumberFormat('es-PE', { minimumFractionDigits: 2, maximumFractionDigits: 2 });

export function formatoEntero(n: number | null | undefined): string {
  return n == null || !Number.isFinite(n) ? NO_DISPONIBLE : fmtEntero.format(n);
}

export function formatoDecimal(n: number | null | undefined, decimales: 1 | 2 = 1): string {
  if (n == null || !Number.isFinite(n)) return NO_DISPONIBLE;
  return (decimales === 1 ? fmtDecimal1 : fmtDecimal2).format(n);
}

/** "S/ 8.00" */
export function formatoSoles(n: number | null | undefined): string {
  return n == null || !Number.isFinite(n) ? NO_DISPONIBLE : `S/ ${fmtDecimal2.format(n)}`;
}

/** "12 km" */
export function formatoKm(n: number | null | undefined): string {
  return n == null || !Number.isFinite(n) ? NO_DISPONIBLE : `${fmtEntero.format(n)} km`;
}

export function formatoPorcentaje(n: number | null | undefined): string {
  return n == null || !Number.isFinite(n) ? NO_DISPONIBLE : `${fmtDecimal1.format(n)} %`;
}

// ---------------------------------------------------------------------------
// Coordenadas y caminos
// ---------------------------------------------------------------------------

export function formatoCoordenada(x: number | null | undefined, y: number | null | undefined): string {
  if (x == null || y == null) return NO_DISPONIBLE;
  return `(${x}, ${y})`;
}

/**
 * Longitud en km (distancia Manhattan) de un camino que parte de (x, y).
 * Cada celda de la retícula mide 1 km.
 */
export function longitudCaminoKm(
  origen: { x: number; y: number },
  camino: ReadonlyArray<{ x: number; y: number }>
): number {
  let total = 0;
  let previo = origen;
  for (const p of camino) {
    total += Math.abs(p.x - previo.x) + Math.abs(p.y - previo.y);
    previo = p;
  }
  return total;
}
