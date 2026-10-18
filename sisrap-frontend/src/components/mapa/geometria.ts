import type { PuntoSimulacion, VehiculoSnapshot } from '../../types/api';

/**
 * Geometría de las rutas en la retícula.
 *
 * - Todo trazado es ortogonal: si dos puntos consecutivos difieren en x e
 *   y a la vez, se inserta una esquina (primero x, luego y). Nunca hay
 *   diagonales ni curvas.
 * - Las rutas se descomponen en tramos de 1 km (aristas de la retícula).
 *   Cuando varias rutas comparten una arista, cada una recibe un carril y
 *   se desplaza perpendicularmente lo mínimo para que todas se vean.
 */

export interface Nodo {
  x: number;
  y: number;
}

export interface Arista {
  a: Nodo;
  b: Nodo;
  /** Posición del carril centrada en 0: -1.5, -0.5, 0.5, 1.5 ... */
  carril: number;
  /** Cantidad de rutas que comparten esta arista. */
  compartida: number;
}

export interface RutaKm {
  idVehiculo: string;
  aristas: Arista[];
  destino: Nodo | null;
}

const clave = (a: Nodo, b: Nodo) => {
  const [p, q] = a.x < b.x || (a.x === b.x && a.y <= b.y) ? [a, b] : [b, a];
  return `${p.x},${p.y}|${q.x},${q.y}`;
};

/** Expande una secuencia de puntos en pasos unitarios ortogonales. */
export function pasosUnitarios(puntos: ReadonlyArray<Nodo>): Nodo[] {
  if (puntos.length === 0) return [];
  const salida: Nodo[] = [{ x: puntos[0].x, y: puntos[0].y }];
  for (let i = 1; i < puntos.length; i++) {
    let { x, y } = salida[salida.length - 1];
    const destino = puntos[i];
    while (x !== destino.x) {
      x += Math.sign(destino.x - x);
      salida.push({ x, y });
    }
    while (y !== destino.y) {
      y += Math.sign(destino.y - y);
      salida.push({ x, y });
    }
  }
  return salida;
}

export function construirRutas(vehiculos: ReadonlyArray<VehiculoSnapshot>): RutaKm[] {
  const conRuta = vehiculos.filter((v) => v.caminoActual && v.caminoActual.length > 0);

  // 1. Aristas unitarias de cada ruta, desde la posición actual de la unidad.
  const aristasPorVehiculo = new Map<string, Array<{ a: Nodo; b: Nodo; k: string }>>();
  const usoPorArista = new Map<string, string[]>();

  for (const v of conRuta) {
    const camino: PuntoSimulacion[] = [{ x: v.x, y: v.y }, ...v.caminoActual];
    const pasos = pasosUnitarios(camino);
    const lista: Array<{ a: Nodo; b: Nodo; k: string }> = [];
    const vistas = new Set<string>();
    for (let i = 1; i < pasos.length; i++) {
      const a = pasos[i - 1];
      const b = pasos[i];
      const k = clave(a, b);
      lista.push({ a, b, k });
      if (!vistas.has(k)) {
        vistas.add(k);
        const uso = usoPorArista.get(k);
        if (uso) uso.push(v.idVehiculo);
        else usoPorArista.set(k, [v.idVehiculo]);
      }
    }
    aristasPorVehiculo.set(v.idVehiculo, lista);
  }

  // 2. Carriles: orden estable por código de vehículo.
  for (const uso of usoPorArista.values()) uso.sort();

  return conRuta.map((v) => {
    const lista = aristasPorVehiculo.get(v.idVehiculo) ?? [];
    const aristas: Arista[] = lista.map(({ a, b, k }) => {
      const uso = usoPorArista.get(k) ?? [v.idVehiculo];
      const n = uso.length;
      return { a, b, carril: uso.indexOf(v.idVehiculo) - (n - 1) / 2, compartida: n };
    });
    const ultimo = v.caminoActual[v.caminoActual.length - 1];
    return { idVehiculo: v.idVehiculo, aristas, destino: ultimo ? { x: ultimo.x, y: ultimo.y } : null };
  });
}

export interface Proyeccion {
  px: (x: number) => number;
  py: (y: number) => number;
  escala: number;
}

/** Separación en píxeles entre carriles: el grosor del trazo, sin pasar de la celda. */
export function separacionCarril(escala: number, compartida: number, grosor: number): number {
  if (compartida <= 1) return 0;
  return Math.min(grosor + 0.6, (escala * 0.9) / (compartida - 1));
}

/**
 * Convierte una ruta en una polilínea de píxeles con los carriles aplicados.
 * Los empalmes entre aristas con distinto desplazamiento son ortogonales.
 */
export function polilineaPx(ruta: RutaKm, proy: Proyeccion, grosor: number): Array<[number, number]> {
  const puntos: Array<[number, number]> = [];
  for (const ar of ruta.aristas) {
    const horizontal = ar.a.y === ar.b.y;
    const d = ar.carril * separacionCarril(proy.escala, ar.compartida, grosor);
    // Normal hacia arriba en pantalla para tramos horizontales, a la derecha para verticales.
    const dx = horizontal ? 0 : d;
    const dy = horizontal ? -d : 0;
    const A: [number, number] = [proy.px(ar.a.x) + dx, proy.py(ar.a.y) + dy];
    const B: [number, number] = [proy.px(ar.b.x) + dx, proy.py(ar.b.y) + dy];

    if (puntos.length === 0) {
      puntos.push(A);
    } else {
      const P = puntos[puntos.length - 1];
      if (P[0] !== A[0] && P[1] !== A[1]) {
        // Esquina ortogonal: se prolonga el tramo anterior hasta alinearse.
        const previoHorizontal = puntos.length >= 2 && puntos[puntos.length - 2][1] === P[1];
        puntos.push(previoHorizontal ? [A[0], P[1]] : [P[0], A[1]]);
      }
      if (puntos[puntos.length - 1][0] !== A[0] || puntos[puntos.length - 1][1] !== A[1]) {
        puntos.push(A);
      }
    }
    puntos.push(B);
  }
  return puntos;
}

/** Distancia de un punto a un segmento, en píxeles. */
export function distanciaSegmento(
  p: [number, number],
  a: [number, number],
  b: [number, number]
): number {
  const vx = b[0] - a[0];
  const vy = b[1] - a[1];
  const largo2 = vx * vx + vy * vy;
  let t = largo2 === 0 ? 0 : ((p[0] - a[0]) * vx + (p[1] - a[1]) * vy) / largo2;
  t = Math.max(0, Math.min(1, t));
  const cx = a[0] + t * vx;
  const cy = a[1] + t * vy;
  return Math.hypot(p[0] - cx, p[1] - cy);
}
