/**
 * Formas de los iconos del mapa, como trazos SVG centrados en (0,0).
 *
 * La misma definición se dibuja en el Canvas (con Path2D) y en la leyenda
 * (con <svg> en línea), así la leyenda muestra exactamente lo que se ve en
 * el mapa. Se dibujan a tamaño fijo en píxeles, no en kilómetros.
 * Basadas en los iconos del prototipo paqrap_prototipo_v03.html.
 */

/** "color" = el color que se pasa al dibujar (p. ej. el del tipo de vehículo). */
export interface Capa {
  d: string;
  relleno?: string;
  trazo?: string;
  grosor?: number;
}

export type FormaIcono = Capa[];

export const BORDE = '#0a111e';

const circ = (cx: number, cy: number, r: number) =>
  `M${cx + r} ${cy} A${r} ${r} 0 1 0 ${cx - r} ${cy} A${r} ${r} 0 1 0 ${cx + r} ${cy} Z`;

export const FORMAS = {
  auto: [
    { d: 'M-4.6 -2.5 L-3 -6.4 L3.4 -6.4 L5 -2.5 Z', relleno: 'color', trazo: BORDE, grosor: 1.3 },
    {
      d: 'M-7.5 -2.5 H7.5 A2 2 0 0 1 9.5 -0.5 V1.7 A2 2 0 0 1 7.5 3.7 H-7.5 A2 2 0 0 1 -9.5 1.7 V-0.5 A2 2 0 0 1 -7.5 -2.5 Z',
      relleno: 'color',
      trazo: BORDE,
      grosor: 1.3,
    },
    { d: circ(-5.2, 4.2, 2.4) + circ(5.2, 4.2, 2.4), relleno: BORDE },
  ],
  moto: [
    { d: circ(-5.6, 2.6, 2.9) + circ(5.6, 2.6, 2.9), trazo: 'color', grosor: 1.9 },
    {
      d: 'M-5.6 2.6 L-1.2 -1.6 L3.2 -1.6 L5.6 2.6 M-3.4 -3.8 L0.8 -3.8 M4.2 -1.6 L6.6 -4.4',
      trazo: 'color',
      grosor: 1.9,
    },
  ],
  bicicleta: [
    { d: circ(-5.8, 2.4, 3.9) + circ(5.8, 2.4, 3.9), trazo: 'color', grosor: 1.4 },
    {
      d: 'M-5.8 2.4 L-0.4 -2.8 L5.8 2.4 M-0.4 -2.8 L-1.6 2.4 M-2.6 -4.4 L0.6 -4.4 M5.8 2.4 L4.4 -4.2 L6.8 -4.8',
      trazo: 'color',
      grosor: 1.4,
    },
  ],
  almacenCentral: [
    { d: 'M-11.4 -2.4 L0 -10.6 L11.4 -2.4 Z', relleno: '#34d399', trazo: BORDE, grosor: 1.3 },
    { d: 'M-9.4 -2.4 H9.4 V8.9 H-9.4 Z', relleno: '#34d399', trazo: BORDE, grosor: 1.3 },
    { d: 'M-3.1 2.4 H3.1 V8.9 H-3.1 Z M-7.2 -0.7 H7.2 V1.3 H-7.2 Z', relleno: '#042416' },
  ],
  almacenIntermedio: [
    { d: 'M-9.5 -2 L0 -8.8 L9.5 -2 Z', relleno: '#34d399', trazo: BORDE, grosor: 1.3 },
    { d: 'M-7.8 -2 H7.8 V7.4 H-7.8 Z', relleno: '#34d399', trazo: BORDE, grosor: 1.3 },
    { d: 'M-2.6 2 H2.6 V7.4 H-2.6 Z', relleno: '#042416' },
  ],
  bloqueo: [
    { d: circ(0, 0, 6.6), relleno: '#ef4444', trazo: BORDE, grosor: 1.3 },
    { d: 'M-3.9 -1.2 H3.9 V1.2 H-3.9 Z', relleno: '#ffffff' },
  ],
  /** Avería: triángulo de advertencia ámbar. */
  averia: [
    { d: 'M0 -7.2 L6.8 4.6 L-6.8 4.6 Z', relleno: '#f59e0b', trazo: BORDE, grosor: 1.3 },
    { d: 'M-0.9 -3.4 H0.9 V1.2 H-0.9 Z M-0.9 2.2 H0.9 V4 H-0.9 Z', relleno: '#2a1d02' },
  ],
  /** Mantenimiento preventivo: cuadrado celeste con una llave. Distinto de la avería. */
  mantenimiento: [
    {
      d: 'M-4.6 -6.6 H4.6 A2 2 0 0 1 6.6 -4.6 V4.6 A2 2 0 0 1 4.6 6.6 H-4.6 A2 2 0 0 1 -6.6 4.6 V-4.6 A2 2 0 0 1 -4.6 -6.6 Z',
      relleno: '#38bdf8',
      trazo: BORDE,
      grosor: 1.3,
    },
    { d: 'M-3.6 3.6 L0.9 -0.9', trazo: '#082f49', grosor: 2 },
    { d: circ(2.3, -2.3, 2), trazo: '#082f49', grosor: 1.7 },
  ],
  destinoCliente: [
    { d: circ(0, 0, 8.4), trazo: 'color', grosor: 2 },
    { d: circ(0, 0, 2.6), relleno: 'color' },
  ],
} satisfies Record<string, FormaIcono>;

export type NombreForma = keyof typeof FORMAS;

// ---------------------------------------------------------------------------
// Dibujo en Canvas
// ---------------------------------------------------------------------------

const cache = new Map<string, Path2D>();
function path2d(d: string): Path2D {
  let p = cache.get(d);
  if (!p) {
    p = new Path2D(d);
    cache.set(d, p);
  }
  return p;
}

export function dibujarForma(
  ctx: CanvasRenderingContext2D,
  nombre: NombreForma,
  x: number,
  y: number,
  color = '#e8eef7',
  escala = 1
): void {
  ctx.save();
  ctx.translate(x, y);
  if (escala !== 1) ctx.scale(escala, escala);
  ctx.lineJoin = 'round';
  ctx.lineCap = 'round';
  for (const capa of FORMAS[nombre] as FormaIcono) {
    const p = path2d(capa.d);
    if (capa.relleno) {
      ctx.fillStyle = capa.relleno === 'color' ? color : capa.relleno;
      ctx.fill(p);
    }
    if (capa.trazo) {
      ctx.strokeStyle = capa.trazo === 'color' ? color : capa.trazo;
      ctx.lineWidth = capa.grosor ?? 1;
      ctx.stroke(p);
    }
  }
  ctx.restore();
}
