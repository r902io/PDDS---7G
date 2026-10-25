import type { PuntoSimulacion, VehiculoSnapshot } from '../../types/api';
import { estaAveriado, estaEnMantenimiento } from '../../utilitarios/vehiculos';

type Punto = PuntoSimulacion;
interface Transicion {
  puntos: Punto[];
  inicio: number;
  duracion: number;
  longitudes: number[];
  distancia: number;
}
interface Unidad {
  muestra: VehiculoSnapshot;
  transiciones: Transicion[];
}
const EPS = 1e-6;
const igual = (a: Punto, b: Punto) => Math.abs(a.x - b.x) + Math.abs(a.y - b.y) < EPS;
const posicion = (v: VehiculoSnapshot): Punto => ({
  x: Number.isFinite(v.xVisual) ? v.xVisual! : v.x,
  y: Number.isFinite(v.yVisual) ? v.yVisual! : v.y,
});

function crear(puntos: Punto[], inicio: number, duracion: number): Transicion {
  const limpios = puntos.filter((p, i) => i === 0 || !igual(p, puntos[i - 1]));
  const longitudes = [0];
  for (let i = 1; i < limpios.length; i++) {
    const a = limpios[i - 1], b = limpios[i];
    longitudes.push(longitudes[i - 1] + Math.abs(b.x - a.x) + Math.abs(b.y - a.y));
  }
  return { puntos: limpios, inicio, duracion, longitudes, distancia: longitudes[longitudes.length - 1] };
}

function recorrer(tr: Transicion, ahora: number): { punto: Punto; restantes: Punto[] } {
  const avance = tr.duracion > 0 ? Math.max(0, Math.min(1, (ahora - tr.inicio) / tr.duracion)) : 1;
  const distancia = avance * tr.distancia;
  for (let i = 1; i < tr.puntos.length; i++) {
    if (distancia >= tr.longitudes[i] - EPS) continue;
    const a = tr.puntos[i - 1], b = tr.puntos[i];
    const t = (distancia - tr.longitudes[i - 1]) / (tr.longitudes[i] - tr.longitudes[i - 1]);
    const punto = { x: a.x + (b.x - a.x) * t, y: a.y + (b.y - a.y) * t };
    return { punto, restantes: [punto, ...tr.puntos.slice(i)] };
  }
  const ultimo = tr.puntos[tr.puntos.length - 1];
  return { punto: ultimo, restantes: [ultimo] };
}

/** Solo une tramos confirmados. Una discontinuidad se resincroniza sin inventar calles. */
function trayecto(previo: VehiculoSnapshot, actual: VehiculoSnapshot): Punto[] | null {
  const origen = posicion(previo), destino = posicion(actual);
  const puntos = [origen];
  if (actual.secuenciaMovimiento != null && previo.secuenciaMovimiento != null) {
    if (actual.secuenciaMovimiento < previo.secuenciaMovimiento) return null;
    const pasos = (actual.movimientosRecientes ?? [])
      .filter((p) => p.secuencia > previo.secuenciaMovimiento!);
    let siguiente = previo.secuenciaMovimiento + 1;
    for (const paso of pasos) {
      if (paso.secuencia !== siguiente++) return null;
      puntos.push({ x: paso.x, y: paso.y });
    }
    if (siguiente - 1 !== actual.secuenciaMovimiento) return null;
    // Sin cruzar un nodo, ambos puntos deben estar sobre la misma calle unitaria.
    if (pasos.length === 0 && Math.abs(origen.x - destino.x) + Math.abs(origen.y - destino.y) > 1 + EPS)
      return null;
  } else if (!igual(origen, destino)) {
    // Compatibilidad con snapshots sin metadatos de animación.
    let encontrado = false;
    for (const nodo of previo.caminoActual.slice(0, 256)) {
      puntos.push(nodo);
      if (igual(nodo, destino)) { encontrado = true; break; }
    }
    if (!encontrado) {
      if (Math.abs(origen.x - destino.x) + Math.abs(origen.y - destino.y) > 1 + EPS) return null;
      puntos.length = 1;
    }
  }
  puntos.push(destino);
  for (let i = 1; i < puntos.length; i++) {
    const a = puntos[i - 1], b = puntos[i];
    if (Math.abs(a.x - b.x) > EPS && Math.abs(a.y - b.y) > EPS) return null;
    if (Math.abs(a.x - b.x) + Math.abs(a.y - b.y) > 1 + EPS) return null;
  }
  return puntos;
}

/** Reproduce tramos confirmados con un segundo de reserva, sin extrapolar. */
export class AnimadorVehiculos {
  private unidades = new Map<string, Unidad>();
  private corrida: number | null = null;
  private recibido: number | null = null;
  private relojes: { recibido: number; simulado: number }[] = [];
  private animando = false;
  private readonly retraso = 1_000;

  actualizar(vehiculos: VehiculoSnapshot[], corrida: number | null, ahora: number,
      animar: boolean, reloj?: string | null) {
    const reiniciar = corrida !== this.corrida || this.recibido == null
      || ahora - this.recibido > 4_000 || ahora < this.recibido || animar !== this.animando;
    if (reiniciar || !animar) {
      this.unidades.clear();
      this.relojes = [];
    }
    const anterior = this.recibido ?? ahora;
    const actuales = new Set<string>();
    for (const actual of vehiculos) {
      actuales.add(actual.idVehiculo);
      const previo = this.unidades.get(actual.idVehiculo);
      const fijo = !animar || reiniciar || !previo
        || estaAveriado(actual.estado) || estaEnMantenimiento(actual.estado);
      if (fijo) {
        this.unidades.set(actual.idVehiculo, { muestra: actual,
          transiciones: [crear([posicion(actual)], ahora, 0)] });
        continue;
      }
      const camino = trayecto(previo.muestra, actual);
      if (!camino || ahora <= anterior) {
        previo.transiciones = [crear([posicion(actual)], ahora, 0)];
      } else {
        // Cada tramo conserva su intervalo. Recibir una muestra no reinicia lo dibujado.
        this.podar(previo, ahora);
        previo.transiciones.push(crear(camino, anterior + this.retraso, ahora - anterior));
        if (previo.transiciones.length > 16)
          previo.transiciones = [crear([posicion(actual)], ahora, 0)];
      }
      previo.muestra = actual;
    }
    for (const id of this.unidades.keys()) if (!actuales.has(id)) this.unidades.delete(id);
    if (reloj) {
      // Fechas locales del simulador: UTC se usa solo como eje numérico, sin convertir de zona.
      const simulado = Date.parse(reloj.replace(/Z$/, '') + 'Z');
      if (Number.isFinite(simulado)) {
        this.relojes.push({ recibido: ahora, simulado });
        while (this.relojes.length > 16) this.relojes.shift();
      }
    }
    this.corrida = corrida;
    this.recibido = ahora;
    this.animando = animar;
  }

  private podar(unidad: Unidad, ahora: number) {
    while (unidad.transiciones.length > 1 && unidad.transiciones[1].inicio <= ahora)
      unidad.transiciones.shift();
  }

  posicion(id: string, ahora: number): Punto | null {
    const unidad = this.unidades.get(id);
    if (!unidad) return null;
    this.podar(unidad, ahora);
    return recorrer(unidad.transiciones[0], ahora).punto;
  }

  /** Une lo que falta reproducir con la ruta confirmada de la última muestra. */
  camino(id: string, ahora: number): Punto[] {
    const unidad = this.unidades.get(id);
    if (!unidad) return [];
    this.podar(unidad, ahora);
    const puntos: Punto[] = [];
    const agregar = (p: Punto) => {
      if (!puntos.length || !igual(puntos[puntos.length - 1], p)) puntos.push({ x: p.x, y: p.y });
    };
    unidad.transiciones.forEach((tr, i) => {
      for (const p of i === 0 ? recorrer(tr, ahora).restantes : tr.puntos) agregar(p);
    });
    for (const p of unidad.muestra.caminoActual) agregar(p);
    return puntos;
  }

  reloj(ahora: number): string | null {
    if (!this.relojes.length) return null;
    const t = ahora - (this.animando ? this.retraso : 0);
    let a = this.relojes[0];
    for (const b of this.relojes.slice(1)) {
      if (t < b.recibido) {
        const f = Math.max(0, Math.min(1, (t - a.recibido) / (b.recibido - a.recibido)));
        return new Date(a.simulado + (b.simulado - a.simulado) * f).toISOString().slice(0, 19);
      }
      a = b;
    }
    return new Date(a.simulado).toISOString().slice(0, 19);
  }

  enMovimiento(ahora: number): boolean {
    if (!this.animando) return false;
    // Mantener RAF hasta consumir la reserva, incluso cuando el vehículo está detenido.
    return this.recibido != null && ahora < this.recibido + this.retraso;
  }
}
