import { useRangosSemaforo, clasificarVehiculo, COLOR_SEMAFORO, type RangosSemaforo } from '../estado/SemaforoVehiculos';
import { useCallback, useEffect, useMemo, useRef, useState, type PointerEvent as EventoPuntero } from 'react';
import type { AlmacenOperativo, BloqueoRespuesta, PedidoOperativo, PuntoSimulacion, VehiculoSnapshot } from '../types/api';
import { dibujarForma, BORDE, type NombreForma } from '../components/iconos/formas';
import { distanciaSegmento, type Proyeccion } from '../components/mapa/geometria';
import { useFlujoVisual } from '../estado/SimulacionContext';
import { AnimadorVehiculos } from '../components/mapa/animacionVehiculos';
import {
  colorDeVehiculo,
  COLOR_TIPO,
  estaAveriado,
  estaEnMantenimiento,
  obtenerFormaAveria,
  tipoDesdeCodigo,
} from '../utilitarios/vehiculos';
import { obtenerColorSemaforoAlmacen, obtenerColorHexAlmacen } from '../utilitarios/almacenes';
import type { RutaDestacada } from '../utilitarios/envios';

export interface Dimensiones {
  anchoKm: number;
  altoKm: number;
}

export type Objetivo =
    | { tipo: 'vehiculo'; id: string }
    | { tipo: 'nodo'; x: number; y: number; ids: string[]; idAlmacen: string | null }
    | { tipo: 'almacen'; id: string }
    | { tipo: 'pedido'; id: number };

export type Seleccion =
    | { tipo: 'vehiculo'; id: string }
    | { tipo: 'nodo'; x: number; y: number; ids?: string[] }
    | { tipo: 'almacen'; id: string }
    | { tipo: 'pedido'; id: number }
    | null;

export interface EstadoHover {
  objetivo: Objetivo;
  px: number;
  py: number;
}

interface Opciones {
  dimensiones: Dimensiones;
  almacenes: AlmacenOperativo[];
  vehiculos: VehiculoSnapshot[];
  corridaId: number | null;
  pedidos: PedidoOperativo[];
  bloqueos: BloqueoRespuesta[];
  seleccion: Seleccion;
  alSeleccionar: (s: Seleccion) => void;
  atenuado: boolean;
  animar: boolean;
  rutasDestacadas: RutaDestacada[];
}

const MARGEN = 34;
const GROSOR_RUTA = 2.4;
const RADIO_MARCA = 11;
const ZOOM_MINIMO = 1;
export const ZOOM_MAXIMO = 8;
const PASO_ZOOM_RUEDA = 1.15;
const PASO_ZOOM_BOTON = 1.3;

interface Grupo {
  x: number;
  y: number;
  ids: string[];
  idAlmacen: string | null;
}

interface RutaPersistida {
  puntos: PuntoSimulacion[];
  esperando: boolean;
  puntosAnteriores?: PuntoSimulacion[];
  transicionInicio?: number;
}

const DURACION_CAMBIO_RUTA_MS = 320;

export function useMapCanvas(op: Opciones) {
  const rangos = useRangosSemaforo();
  const canvasRef = useRef<HTMLCanvasElement | null>(null);
  const rutasCanvasRef = useRef<HTMLCanvasElement | null>(null);
  const vehiculosCanvasRef = useRef<HTMLCanvasElement | null>(null);
  const almacenesCanvasRef = useRef<HTMLCanvasElement | null>(null);
  const animador = useRef(new AnimadorVehiculos());
  const flujo = useFlujoVisual();
  const despertar = useRef<(() => void) | null>(null);
  const redibujarRutas = useRef<(() => void) | null>(null);
  const relojVisualRef = useRef<HTMLSpanElement | null>(null);
  const ultimaMuestra = useRef<unknown>(null);
  const animacionHabilitada = useRef(false);
  const gruposAnimados = useRef<Grupo[]>([]);
  const [tamano, setTamano] = useState({ w: 0, h: 0 });
  const [vista, setVista] = useState({ zoom: 1, x: 0, y: 0 });
  const [hover, setHover] = useState<EstadoHover | null>(null);
  const [cursorKm, setCursorKm] = useState<{ x: number; y: number } | null>(null);
  const arrastre = useRef<{ x0: number; y0: number; dx0: number; dy0: number; movido: boolean } | null>(null);
  const vehiculosVisuales = useRef(op.vehiculos);
  const corridaVisual = useRef(op.corridaId);
  const polilineas = useRef<Map<string, Array<[number, number]>>>(new Map());
  const rutasPersistidas = useRef<Map<string, RutaPersistida>>(new Map());
  // Se lee en cada cuadro: resaltar un envío no reinicia el dibujo del mapa.
  const rutasDestacadas = useRef(op.rutasDestacadas);
  rutasDestacadas.current = op.rutasDestacadas;

  const { anchoKm, altoKm } = op.dimensiones;

  useEffect(() => {
    const canvas = canvasRef.current;
    const padre = canvas?.parentElement;
    if (!padre) return;
    const medir = () => setTamano({ w: padre.clientWidth, h: padre.clientHeight });
    medir();
    const ro = new ResizeObserver(medir);
    ro.observe(padre);
    return () => ro.disconnect();
  }, []);

  const proy: Proyeccion & { ox: number; oy: number } = useMemo(() => {
    const base = Math.max(
        0.1,
        Math.min((tamano.w - 2 * MARGEN) / anchoKm, (tamano.h - 2 * MARGEN) / altoKm)
    );
    const escala = base * vista.zoom;
    const ox = (tamano.w - escala * anchoKm) / 2 + vista.x;
    const oy = (tamano.h - escala * altoKm) / 2 + vista.y;
    return {
      escala,
      ox,
      oy,
      px: (x: number) => ox + x * escala,
      py: (y: number) => oy + (altoKm - y) * escala,
    };
  }, [tamano, vista, anchoKm, altoKm]);

  const grupos: Grupo[] = useMemo(() => {
    const porNodo = new Map<string, Grupo>();
    for (const v of op.vehiculos) {
      const k = `${v.x},${v.y}`;
      let g = porNodo.get(k);
      if (!g) {
        const alm = op.almacenes.find((a) => a.ubicacionX === v.x && a.ubicacionY === v.y);
        g = { x: v.x, y: v.y, ids: [], idAlmacen: alm?.idAlmacen ?? null };
        porNodo.set(k, g);
      }
      g.ids.push(v.idVehiculo);
    }
    for (const g of porNodo.values()) g.ids.sort();
    return [...porNodo.values()];
  }, [op.vehiculos, op.almacenes]);

  const enfocado: string | null =
      hover?.objetivo.tipo === 'vehiculo'
          ? hover.objetivo.id
          : op.seleccion?.tipo === 'vehiculo'
              ? op.seleccion.id
              : null;

  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas || tamano.w === 0 || tamano.h === 0) return;
    const ctx = canvas.getContext('2d');
    if (!ctx) return;

    const dpr = Math.min(2, window.devicePixelRatio || 1);
    const ancho = Math.round(tamano.w * dpr);
    const alto = Math.round(tamano.h * dpr);
    if (canvas.width !== ancho) canvas.width = ancho;
    if (canvas.height !== alto) canvas.height = alto;
    canvas.style.width = `${tamano.w}px`;
    canvas.style.height = `${tamano.h}px`;
    ctx.setTransform(dpr, 0, 0, dpr, 0, 0);

    const { px, py, escala } = proy;
    ctx.clearRect(0, 0, tamano.w, tamano.h);
    ctx.fillStyle = '#0a111e';
    ctx.fillRect(0, 0, tamano.w, tamano.h);

    ctx.lineWidth = 1;
    for (let x = 0; x <= anchoKm; x++) {
      ctx.strokeStyle = x % 10 === 0 ? '#2a3d5e' : '#1a2940';
      ctx.beginPath();
      ctx.moveTo(Math.round(px(x)) + 0.5, py(0));
      ctx.lineTo(Math.round(px(x)) + 0.5, py(altoKm));
      ctx.stroke();
    }
    for (let y = 0; y <= altoKm; y++) {
      ctx.strokeStyle = y % 10 === 0 ? '#2a3d5e' : '#1a2940';
      ctx.beginPath();
      ctx.moveTo(px(0), Math.round(py(y)) + 0.5);
      ctx.lineTo(px(anchoKm), Math.round(py(y)) + 0.5);
      ctx.stroke();
    }
    ctx.strokeStyle = '#3b527a';
    ctx.lineWidth = 1.5;
    ctx.strokeRect(px(0), py(altoKm), anchoKm * escala, altoKm * escala);

    ctx.font = '11px ui-monospace, Consolas, monospace';
    ctx.fillStyle = '#a7b6c9';
    ctx.textAlign = 'center';
    ctx.textBaseline = 'top';
    for (let x = 0; x <= anchoKm; x += 10) ctx.fillText(String(x), px(x), py(0) + 6);
    ctx.textAlign = 'right';
    ctx.textBaseline = 'middle';
    for (let y = 0; y <= altoKm; y += 10) ctx.fillText(String(y), px(0) - 6, py(y));

    const alfaBase = 1;

    for (const p of op.pedidos) {
      if (p.estado === 'ENTREGADO') continue;
      const x = px(p.ubicacionX), y = py(p.ubicacionY);
      const color = p.estado === 'RETRASADO' ? '#f87171' : p.estado === 'EN_RUTA' ? '#60a5fa' : '#fbbf24';
      ctx.save();
      ctx.globalAlpha = alfaBase * 0.85;
      ctx.fillStyle = color;
      ctx.strokeStyle = '#101827';
      ctx.lineWidth = 1.2;
      ctx.beginPath();
      ctx.arc(x, y, 4.5, 0, Math.PI * 2);
      ctx.fill();
      ctx.stroke();
      if (op.seleccion?.tipo === 'pedido' && op.seleccion.id === p.idPedido) {
        ctx.strokeStyle = '#ffffff';
        ctx.lineWidth = 2;
        ctx.beginPath();
        ctx.arc(x, y, 8, 0, Math.PI * 2);
        ctx.stroke();
      }
      ctx.restore();
    }
  }, [tamano, proy, op.pedidos, op.seleccion, anchoKm, altoKm]);

  useEffect(() => {
    const recibir = () => {
      const s = flujo?.actual;
      const reducir = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
      const animar = (s ? s.estado === 'EJECUTANDO' : op.animar)
          && !document.hidden && !reducir;
      const muestra = s ?? op.vehiculos;
      if (ultimaMuestra.current === muestra && animar && animacionHabilitada.current) return;
      const corrida = s?.idSimulacion ?? op.corridaId;
      if (corridaVisual.current !== corrida) {
        polilineas.current.clear();
        rutasPersistidas.current.clear();
        gruposAnimados.current = [];
        corridaVisual.current = corrida;
      }
      vehiculosVisuales.current = s?.vehiculos ?? op.vehiculos;
      animacionHabilitada.current = animar;
      ultimaMuestra.current = muestra;
      animador.current.actualizar(s?.vehiculos ?? op.vehiculos, s?.idSimulacion ?? op.corridaId,
          performance.now(), animar, s?.relojSimulado);
      redibujarRutas.current?.();
      despertar.current?.();
    };
    recibir();
    flujo?.oyentes.add(recibir);
    return () => { flujo?.oyentes.delete(recibir); };
  }, [flujo, op.vehiculos, op.corridaId, op.animar]);

  // Capa intermedia: rutas y bloqueos. Solo se redibuja cuando
  // cambia una muestra, la vista o los elementos que forman esta capa.
  useEffect(() => {
    const canvas = rutasCanvasRef.current;
    if (!canvas || tamano.w === 0 || tamano.h === 0) return;
    const ctx = canvas.getContext('2d');
    if (!ctx) return;

    const dpr = Math.min(2, window.devicePixelRatio || 1);
    const ancho = Math.round(tamano.w * dpr);
    const alto = Math.round(tamano.h * dpr);
    if (canvas.width !== ancho) canvas.width = ancho;
    if (canvas.height !== alto) canvas.height = alto;
    canvas.style.width = `${tamano.w}px`;
    canvas.style.height = `${tamano.h}px`;
    ctx.setTransform(dpr, 0, 0, dpr, 0, 0);

    const dibujarRutasActuales = () => {
      const ahora = performance.now();
      ctx.setTransform(1, 0, 0, 1, 0, 0);
      ctx.clearRect(0, 0, canvas.width, canvas.height);
      ctx.setTransform(dpr, 0, 0, dpr, 0, 0);

      const { px, py } = proy;
      const vehiculos = vehiculosVisuales.current;
      const idsPresentes = new Set(vehiculos.map((v) => v.idVehiculo));
      for (const id of rutasPersistidas.current.keys()) {
        if (!idsPresentes.has(id)) rutasPersistidas.current.delete(id);
      }

      const alfaBase = 1;
      const tramosCerrados: Array<[[number, number], [number, number]]> = [];
      for (const bloqueo of op.bloqueos) {
        for (let i = 1; i < bloqueo.vertices.length; i++) {
          const a = bloqueo.vertices[i - 1];
          const b = bloqueo.vertices[i];
          tramosCerrados.push([[px(a.x), py(a.y)], [px(b.x), py(b.y)]]);
        }
      }

      const destacadas = rutasDestacadas.current;
      const hayResalte = enfocado != null || destacadas.length > 0;
      const nuevasPolilineas = new Map<string, Array<[number, number]>>();
      const vehiculosOrdenados = [...vehiculos].sort((a, b) =>
          a.idVehiculo === enfocado ? 1 : b.idVehiculo === enfocado ? -1 : 0
      );

      for (const v of vehiculosOrdenados) {
        const id = v.idVehiculo;
        if (estaAveriado(v.estado) || estaEnMantenimiento(v.estado)) {
          rutasPersistidas.current.delete(id);
          continue;
        }

        const { pendiente } = animador.current.camino(id, ahora);
        // Mostrar únicamente el trayecto que el vehículo aún tiene por recorrer.
        // El tramo ya recorrido no se dibuja detrás del vehículo como una estela.
        const candidato = limpiarPuntosRuta(pendiente ?? []);
        const tieneRutaConfirmada = (v.caminoActual?.length ?? 0) > 0;
        const tieneDestinoActivo = v.pedidoObjetivo != null
            || v.estado === 'RETORNANDO_ALMACEN';
        let ruta = rutasPersistidas.current.get(id);

        // Al finalizar una entrega, el backend puede conservar caminoActual
        // durante un snapshot adicional. No volver a pintar ese recorrido viejo.
        if (!tieneDestinoActivo) {
          rutasPersistidas.current.delete(id);
          continue;
        }

        // Nunca sustituir una ruta válida por un snapshot vacío. En cuanto
        // llega la nueva geometría, se intercambia mediante un breve fundido.
        if (tieneRutaConfirmada && candidato.length > 1) {
          const transicionEnCurso = ruta?.puntosAnteriores != null
              && ruta.transicionInicio != null
              && ahora - ruta.transicionInicio < DURACION_CAMBIO_RUTA_MS;
          if (ruta?.esperando && animacionHabilitada.current && !mismaGeometriaRuta(ruta.puntos, candidato)) {
            ruta.puntosAnteriores = ruta.puntos;
            ruta.transicionInicio = ahora;
          } else if (ruta && !transicionEnCurso) {
            ruta.puntosAnteriores = undefined;
            ruta.transicionInicio = undefined;
          }
          if (!ruta) {
            ruta = { puntos: candidato, esperando: false };
          } else {
            ruta.puntos = candidato;
            ruta.esperando = false;
          }
          rutasPersistidas.current.set(id, ruta);
        } else if (!ruta && candidato.length > 1) {
          ruta = { puntos: candidato, esperando: false };
          rutasPersistidas.current.set(id, ruta);
        } else if (ruta) {
          ruta.esperando = true;
        }

        if (!ruta || ruta.puntos.length < 2) continue;

        // Recortar cualquier pequeño tramo histórico que pueda quedar delante
        // de la posición animada por diferencias entre snapshots y replanificaciones.
        const posicionActual = animador.current.posicion(id, ahora) ?? {
          x: v.xVisual ?? v.x,
          y: v.yVisual ?? v.y,
        };
        const puntosRutaVisibles = recortarRutaEnPosicionActual(ruta.puntos, posicionActual);
        if (puntosRutaVisibles.length < 2) continue;
        const ptsRuta = puntosRutaVisibles.map((p): [number, number] => [px(p.x), py(p.y)]);
        nuevasPolilineas.set(id, ptsRuta);

        const esEnfocado = id === enfocado;
        const alfaRuta = !hayResalte ? 0.85 : (esEnfocado ? 1 : 0.2);
        ctx.save();
        ctx.globalAlpha = alfaBase * alfaRuta;
        ctx.lineJoin = 'round';
        ctx.lineCap = 'round';

        // Mientras el servidor calcula una replanificación, conservar la
        // apariencia habitual de la última ruta válida: mismo color, grosor
        // y trazo continuo. La espera no debe cambiar su representación.
        let progresoCambio = 1;
        if (ruta.puntosAnteriores && ruta.transicionInicio != null) {
          progresoCambio = Math.max(0, Math.min(1,
              (ahora - ruta.transicionInicio) / DURACION_CAMBIO_RUTA_MS));
          const puntosAnterioresVisibles = recortarRutaEnPosicionActual(
              ruta.puntosAnteriores, posicionActual);
          const anteriorPixeles = puntosAnterioresVisibles.map(
              (p): [number, number] => [px(p.x), py(p.y)]);
          if (anteriorPixeles.length > 1 && progresoCambio < 1) {
            ctx.save();
            ctx.globalAlpha *= (1 - progresoCambio) * 0.65;
            ctx.strokeStyle = colorDeVehiculo(id);
            ctx.lineWidth = GROSOR_RUTA;
            trazarPrevision(ctx, anteriorPixeles, tramosCerrados);
            ctx.restore();
          } else {
            ruta.puntosAnteriores = undefined;
            ruta.transicionInicio = undefined;
          }
        }

        ctx.globalAlpha *= progresoCambio;
        if (esEnfocado) {
          ctx.strokeStyle = '#ffffff';
          ctx.lineWidth = GROSOR_RUTA + 3;
          trazarPrevision(ctx, ptsRuta, tramosCerrados);
        }
        ctx.strokeStyle = colorDeVehiculo(id);
        ctx.lineWidth = esEnfocado ? GROSOR_RUTA + 1 : GROSOR_RUTA;
        trazarPrevision(ctx, ptsRuta, tramosCerrados);

        const ultimoPunto = ruta.puntos[ruta.puntos.length - 1];
        if (ultimoPunto && v.pedidoObjetivo != null) {
          dibujarForma(ctx, 'destinoCliente', px(ultimoPunto.x), py(ultimoPunto.y), colorDeVehiculo(id));
        }
        ctx.restore();
      }

      polilineas.current = nuevasPolilineas;

      for (const ruta of destacadas) {
        const unidad = vehiculos.find((v) => v.idVehiculo === ruta.idVehiculo);
        if (!unidad) {
          dibujarRutaDestacada(ctx, ruta, proy, alfaBase);
          continue;
        }
        const posicionActual = animador.current.posicion(ruta.idVehiculo, ahora) ?? {
          x: unidad.xVisual ?? unidad.x,
          y: unidad.yVisual ?? unidad.y,
        };
        const rutaSinTramoRecorrido: RutaDestacada = {
          ...ruta,
          real: recortarRutaEnPosicionActual(ruta.real, posicionActual),
          estimada: recortarRutaEnPosicionActual(ruta.estimada, posicionActual),
        };
        dibujarRutaDestacada(ctx, rutaSinTramoRecorrido, proy, alfaBase);
      }

      for (const bloqueo of op.bloqueos) {
        if (bloqueo.vertices.length < 2) continue;
        ctx.save();
        ctx.globalAlpha = alfaBase;
        ctx.strokeStyle = '#ef4444';
        ctx.lineWidth = 4;
        ctx.lineCap = 'round';
        ctx.lineJoin = 'miter';
        ctx.beginPath();
        let previo = bloqueo.vertices[0];
        ctx.moveTo(px(previo.x), py(previo.y));
        for (let i = 1; i < bloqueo.vertices.length; i++) {
          const punto = bloqueo.vertices[i];
          if (punto.x !== previo.x && punto.y !== previo.y) ctx.lineTo(px(punto.x), py(previo.y));
          ctx.lineTo(px(punto.x), py(punto.y));
          previo = punto;
        }
        ctx.stroke();
        const a = bloqueo.vertices[0];
        const z = bloqueo.vertices[1];
        dibujarForma(ctx, 'bloqueo', (px(a.x) + px(z.x)) / 2, (py(a.y) + py(z.y)) / 2);
        ctx.restore();
      }

    };

    redibujarRutas.current = dibujarRutasActuales;
    dibujarRutasActuales();
    return () => {
      if (redibujarRutas.current === dibujarRutasActuales) redibujarRutas.current = null;
    };
  }, [op.bloqueos, tamano, proy, enfocado, op.rutasDestacadas]);

  // Capa superior absoluta: los almacenes siempre quedan por encima de
  // rutas, pedidos, bloqueos y vehículos, incluso cuando comparten coordenadas.
  useEffect(() => {
    const canvas = almacenesCanvasRef.current;
    if (!canvas || tamano.w === 0 || tamano.h === 0) return;
    const ctx = canvas.getContext('2d');
    if (!ctx) return;

    const dpr = Math.min(2, window.devicePixelRatio || 1);
    const ancho = Math.round(tamano.w * dpr);
    const alto = Math.round(tamano.h * dpr);
    if (canvas.width !== ancho) canvas.width = ancho;
    if (canvas.height !== alto) canvas.height = alto;
    canvas.style.width = `${tamano.w}px`;
    canvas.style.height = `${tamano.h}px`;
    ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
    ctx.clearRect(0, 0, tamano.w, tamano.h);

    const { px, py } = proy;
    for (const almacen of op.almacenes) {
      const x = px(almacen.ubicacionX);
      const y = py(almacen.ubicacionY);
      const central = almacen.tipo === 'CENTRAL';
      const semaforo = obtenerColorSemaforoAlmacen(almacen);
      const colorSem = obtenerColorHexAlmacen(semaforo);

      ctx.save();
      // Fondo opaco: evita que la silueta de un vehículo o un marcador
      // situado debajo se vea atravesando el icono del almacén.
      ctx.fillStyle = '#0a111e';
      ctx.strokeStyle = '#40516b';
      ctx.lineWidth = 1.2;
      ctx.beginPath();
      ctx.arc(x, y, 15, 0, Math.PI * 2);
      ctx.fill();
      ctx.stroke();
      dibujarForma(ctx, central ? 'almacenCentral' : 'almacenIntermedio', x, y, colorSem, 1.25);
      etiqueta(ctx, `${almacen.nombre} (${almacen.ubicacionX},${almacen.ubicacionY})`, x, y + 18, colorSem);
      ctx.restore();
    }
  }, [op.almacenes, tamano, proy]);

  // Capa superior de movimiento: vehículos y agrupaciones. Los iconos usan
  // la posición interpolada del animador, no el último punto discreto del servidor.
  useEffect(() => {
    const canvas = vehiculosCanvasRef.current;
    if (!canvas || tamano.w === 0 || tamano.h === 0) return;
    const ctx = canvas.getContext('2d');
    if (!ctx) return;
    const dpr = Math.min(2, window.devicePixelRatio || 1);
    const ancho = Math.round(tamano.w * dpr);
    const alto = Math.round(tamano.h * dpr);
    if (canvas.width !== ancho) canvas.width = ancho;
    if (canvas.height !== alto) canvas.height = alto;
    canvas.style.width = `${tamano.w}px`;
    canvas.style.height = `${tamano.h}px`;
    ctx.setTransform(dpr, 0, 0, dpr, 0, 0);

    let frame = 0;
    let ultimoDibujoRutas = -Infinity;

    const dibujar = (ahora: number) => {
      frame = 0;
      const reloj = animador.current.reloj(ahora);
      if (relojVisualRef.current && reloj) {
        const texto = 'Hora del mapa: ' + reloj.slice(11, 19);
        if (relojVisualRef.current.textContent !== texto) relojVisualRef.current.textContent = texto;
      }

      ctx.setTransform(1, 0, 0, 1, 0, 0);
      ctx.clearRect(0, 0, canvas.width, canvas.height);
      ctx.setTransform(dpr, 0, 0, dpr, 0, 0);

      const { px, py } = proy;
      const vehiculos = vehiculosVisuales.current;
      const vehiculoPorId = new Map(vehiculos.map((v) => [v.idVehiculo, v]));
      const posiciones = new Map<string, { x: number; y: number }>();
      const destacadas = rutasDestacadas.current;
      const idsDestacados = new Set(destacadas.map((r) => r.idVehiculo));
      const hayResalte = enfocado != null || destacadas.length > 0;

      for (const v of vehiculos) {
        const interpolada = animador.current.posicion(v.idVehiculo, ahora);
        const vx = interpolada?.x ?? v.xVisual ?? v.x;
        const vy = interpolada?.y ?? v.yVisual ?? v.y;
        posiciones.set(v.idVehiculo, { x: vx, y: vy });

        const x = px(vx);
        const y = py(vy);
        const tipo = tipoDesdeCodigo(v.idVehiculo ?? '') ?? 'AUTO';
        const color = tipo ? COLOR_TIPO[tipo] : '#e8eef7';
        if (estaAveriado(v.estado)) {
          const formaAveria = obtenerFormaAveria(v.tipoAveria);
          dibujarForma(ctx, formaAveria, x, y - 12, undefined, 1.2);
        } else if (estaEnMantenimiento(v.estado)) {
          dibujarForma(ctx, 'mantenimiento', x, y - 10, undefined, 1.1);
        } else {
          const formaVehiculo = tipo.toLowerCase() as NombreForma;
          dibujarForma(ctx, formaVehiculo, x, y, color);
        }
      }

      const agrupados = new Map<string, Grupo>();
      for (const v of vehiculos) {
        const pos = posiciones.get(v.idVehiculo);
        if (!pos) continue;
        const clave = `${pos.x.toFixed(4)},${pos.y.toFixed(4)}`;
        let grupo = agrupados.get(clave);
        if (!grupo) {
          const almacen = op.almacenes.find((a) => a.ubicacionX === pos.x && a.ubicacionY === pos.y);
          grupo = { ...pos, ids: [], idAlmacen: almacen?.idAlmacen ?? null };
          agrupados.set(clave, grupo);
        }
        grupo.ids.push(v.idVehiculo);
      }
      gruposAnimados.current = [...agrupados.values()];

      for (const grupo of gruposAnimados.current) {
        const x = px(grupo.x);
        const y = py(grupo.y);
        const contieneEnfocado = enfocado != null && grupo.ids.includes(enfocado);
        const contieneDestacada = grupo.ids.some((id) => idsDestacados.has(id));
        ctx.save();
        ctx.globalAlpha = !hayResalte || contieneEnfocado || contieneDestacada ? 1 : 0.35;
        if (grupo.idAlmacen) {
          insignia(ctx, x + 22, y - 2, String(grupo.ids.length), contieneEnfocado);
          ctx.restore();
          continue;
        }

        const seleccionado =
            (op.seleccion?.tipo === 'nodo' && op.seleccion.x === grupo.x && op.seleccion.y === grupo.y) ||
            (op.seleccion?.tipo === 'vehiculo' && grupo.ids.includes(op.seleccion.id));
        if (seleccionado || contieneEnfocado) {
          ctx.strokeStyle = '#ffffff';
          ctx.lineWidth = 2;
          ctx.beginPath();
          ctx.arc(x, y, RADIO_MARCA + 3, 0, Math.PI * 2);
          ctx.stroke();
        }

        if (grupo.ids.length === 1) {
          const unidad = vehiculoPorId.get(grupo.ids[0]);
          if (unidad) dibujarUnidad(ctx, unidad, x, y, rangos);
        } else {
          ctx.fillStyle = '#16233a';
          ctx.strokeStyle = '#e8eef7';
          ctx.lineWidth = 1.5;
          ctx.beginPath();
          ctx.arc(x, y, RADIO_MARCA, 0, Math.PI * 2);
          ctx.fill();
          ctx.stroke();
          ctx.fillStyle = '#e8eef7';
          ctx.font = 'bold 11px ui-monospace, Consolas, monospace';
          ctx.textAlign = 'center';
          ctx.textBaseline = 'middle';
          ctx.fillText(String(grupo.ids.length), x, y + 0.5);
          const estados = grupo.ids.map((id) => vehiculoPorId.get(id)?.estado);
          if (estados.some((estado) => (estado ? estaAveriado(estado) : false))) {
            const tipoAveria = grupo.ids
                .map((id) => vehiculoPorId.get(id)?.tipoAveria)
                .find(Boolean);
            dibujarForma(ctx, obtenerFormaAveria(tipoAveria), x + 12, y - 11, undefined, 0.8);
          } else if (estados.some((estado) => (estado ? estaEnMantenimiento(estado) : false))) {
            dibujarForma(ctx, 'mantenimiento', x + 12, y - 11, undefined, 0.8);
          }
        }
        ctx.restore();
      }

      const activo = animacionHabilitada.current && animador.current.enMovimiento(ahora);
      // La geometría de rutas cambia mucho menos que los iconos: actualizarla
      // a 10 Hz durante el movimiento evita recalcularla en cada cuadro.
      if (activo && ahora - ultimoDibujoRutas >= 100) {
        redibujarRutas.current?.();
        ultimoDibujoRutas = ahora;
      }
      if (activo && !document.hidden) frame = requestAnimationFrame(dibujar);
    };

    const alCambiarVisibilidad = () => {
      cancelAnimationFrame(frame);
      frame = 0;
      if (!document.hidden) {
        const s = flujo?.actual;
        animador.current.actualizar(s?.vehiculos ?? op.vehiculos, s?.idSimulacion ?? op.corridaId,
            performance.now(), false, s?.relojSimulado);
        animacionHabilitada.current = false;
        redibujarRutas.current?.();
        frame = requestAnimationFrame(dibujar);
      }
    };

    despertar.current = () => {
      if (!frame && !document.hidden) frame = requestAnimationFrame(dibujar);
    };
    document.addEventListener('visibilitychange', alCambiarVisibilidad);
    frame = requestAnimationFrame(dibujar);

    return () => {
      cancelAnimationFrame(frame);
      despertar.current = null;
      document.removeEventListener('visibilitychange', alCambiarVisibilidad);
    };
  }, [op.vehiculos, op.corridaId, op.animar, op.almacenes, op.seleccion,
    tamano, proy, enfocado, rangos]);

  const aKm = useCallback(
      (mx: number, my: number) => {
        const x = Math.round((mx - proy.ox) / proy.escala);
        const y = Math.round(altoKm - (my - proy.oy) / proy.escala);
        if (x < 0 || x > anchoKm || y < 0 || y > altoKm) return null;
        return { x, y };
      },
      [proy, anchoKm, altoKm]
  );

  const buscarObjetivo = useCallback(
      (mx: number, my: number): Objetivo | null => {
        let mejor: { o: Objetivo; d: number } | null = null;
        for (const g of gruposAnimados.current.length > 0 ? gruposAnimados.current : grupos) {
          const d = Math.hypot(mx - proy.px(g.x), my - proy.py(g.y));
          if (d > RADIO_MARCA + 6 || (mejor && mejor.d <= d)) continue;
          const o: Objetivo =
              g.ids.length === 1 && !g.idAlmacen
                  ? { tipo: 'vehiculo', id: g.ids[0] }
                  : { tipo: 'nodo', x: g.x, y: g.y, ids: g.ids, idAlmacen: g.idAlmacen };
          mejor = { o, d };
        }
        for (const a of op.almacenes) {
          if (grupos.some((g) => g.idAlmacen === a.idAlmacen)) continue;
          const d = Math.hypot(mx - proy.px(a.ubicacionX), my - proy.py(a.ubicacionY));
          if (d <= RADIO_MARCA + 6 && (!mejor || d < mejor.d)) mejor = { o: { tipo: 'almacen', id: a.idAlmacen }, d };
        }
        if (mejor) return mejor.o;

        for (const p of op.pedidos) {
          if (p.estado === 'ENTREGADO') continue;
          const d = Math.hypot(mx - proy.px(p.ubicacionX), my - proy.py(p.ubicacionY));
          if (d <= 8) return { tipo: 'pedido', id: p.idPedido };
        }

        let mejorRuta: { id: string; d: number } | null = null;
        for (const [id, pts] of polilineas.current) {
          for (let i = 1; i < pts.length; i++) {
            const d = distanciaSegmento([mx, my], pts[i - 1], pts[i]);
            if (d <= 5 && (!mejorRuta || d < mejorRuta.d)) mejorRuta = { id, d };
          }
        }
        return mejorRuta ? { tipo: 'vehiculo', id: mejorRuta.id } : null;
      },
      [grupos, proy, op.almacenes, op.pedidos]
  );

  const posicion = (e: EventoPuntero) => {
    const r = canvasRef.current!.getBoundingClientRect();
    return { mx: e.clientX - r.left, my: e.clientY - r.top };
  };

  const onPointerDown = (e: EventoPuntero<HTMLCanvasElement>) => {
    arrastre.current = { x0: e.clientX, y0: e.clientY, dx0: vista.x, dy0: vista.y, movido: false };
  };

  const onPointerMove = (e: EventoPuntero<HTMLCanvasElement>) => {
    const { mx, my } = posicion(e);
    const a = arrastre.current;
    if (a && e.buttons === 1) {
      const dx = e.clientX - a.x0;
      const dy = e.clientY - a.y0;
      if (a.movido || Math.abs(dx) + Math.abs(dy) > 4) {
        a.movido = true;
        setVista((v) => ({ ...v, x: a.dx0 + dx, y: a.dy0 + dy }));
        setHover(null);
        return;
      }
    }
    setCursorKm(aKm(mx, my));
    const o = buscarObjetivo(mx, my);
    setHover((prev) => {
      if (!o) return null;
      if (prev && mismoObjetivo(prev.objetivo, o)) return { ...prev, px: mx, py: my };
      return { objetivo: o, px: mx, py: my };
    });
  };

  const onPointerUp = (e: EventoPuntero<HTMLCanvasElement>) => {
    const a = arrastre.current;
    arrastre.current = null;
    if (a?.movido) return;
    const { mx, my } = posicion(e);
    const o = buscarObjetivo(mx, my);
    if (!o) op.alSeleccionar(null);
    else if (o.tipo === 'vehiculo') op.alSeleccionar({ tipo: 'vehiculo', id: o.id });
    else if (o.tipo === 'nodo') op.alSeleccionar({ tipo: 'nodo', x: o.x, y: o.y, ids: [...o.ids] });
    else if (o.tipo === 'pedido') op.alSeleccionar({ tipo: 'pedido', id: o.id });
    else op.alSeleccionar(null);
  };

  const onPointerLeave = () => {
    arrastre.current = null;
    setHover(null);
    setCursorKm(null);
  };

  /** Cambia el zoom dejando fijo el punto (mx, my) del lienzo. */
  const aplicarZoom = useCallback(
      (factor: number, mx: number, my: number) => {
        setVista((v) => {
          const zoom = Math.max(ZOOM_MINIMO, Math.min(ZOOM_MAXIMO, v.zoom * factor));
          if (zoom === ZOOM_MINIMO) return { zoom: ZOOM_MINIMO, x: 0, y: 0 };
          const k = zoom / v.zoom;
          const cx = tamano.w / 2 + v.x;
          const cy = tamano.h / 2 + v.y;
          return { zoom, x: v.x + (mx - cx) * (1 - k), y: v.y + (my - cy) * (1 - k) };
        });
      },
      [tamano]
  );

  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas) return;
    const alGirar = (e: WheelEvent) => {
      e.preventDefault();
      const r = canvas.getBoundingClientRect();
      aplicarZoom(e.deltaY < 0 ? PASO_ZOOM_RUEDA : 1 / PASO_ZOOM_RUEDA, e.clientX - r.left, e.clientY - r.top);
    };
    canvas.addEventListener('wheel', alGirar, { passive: false });
    return () => canvas.removeEventListener('wheel', alGirar);
  }, [aplicarZoom]);

  const encuadrar = useCallback(() => {
    setVista({ zoom: ZOOM_MINIMO, x: 0, y: 0 });
  }, []);

  const acercar = () => aplicarZoom(PASO_ZOOM_BOTON, tamano.w / 2, tamano.h / 2);
  const alejar = () => aplicarZoom(1 / PASO_ZOOM_BOTON, tamano.w / 2, tamano.h / 2);

  return {
    canvasRef,
    relojVisualRef,
    rutasCanvasRef,
    vehiculosCanvasRef,
    almacenesCanvasRef,
    hover,
    cursorKm,
    escalaPx: proy.escala,
    zoom: vista.zoom,
    encuadrar,
    acercar,
    alejar,
    manejadores: { onPointerDown, onPointerMove, onPointerUp, onPointerLeave },
  };
}


function limpiarPuntosRuta(puntos: PuntoSimulacion[]): PuntoSimulacion[] {
  const resultado: PuntoSimulacion[] = [];
  for (const punto of puntos) {
    const anterior = resultado[resultado.length - 1];
    if (!anterior || Math.abs(anterior.x - punto.x) + Math.abs(anterior.y - punto.y) > 1e-6) {
      resultado.push({ x: punto.x, y: punto.y });
    }
  }
  return resultado;
}

/**
 * Retira la parte ya recorrida de una polilínea usando la posición visual real
 * del vehículo. Si la ruta no pasa cerca de la unidad, se conserva intacta
 * (por ejemplo, una ruta estimada hacia una parada futura).
 */
function recortarRutaEnPosicionActual(
  puntos: PuntoSimulacion[],
  posicion: PuntoSimulacion,
  toleranciaKm = 0.3
): PuntoSimulacion[] {
  const ruta = limpiarPuntosRuta(puntos);
  if (ruta.length < 2) return ruta;

  let mejorDistancia = Number.POSITIVE_INFINITY;
  let mejorIndice = -1;
  let mejorProyeccion: PuntoSimulacion | null = null;

  for (let i = 0; i < ruta.length - 1; i++) {
    const a = ruta[i];
    const b = ruta[i + 1];
    const dx = b.x - a.x;
    const dy = b.y - a.y;
    const largo2 = dx * dx + dy * dy;
    if (largo2 <= 1e-12) continue;

    const t = Math.max(0, Math.min(1,
      ((posicion.x - a.x) * dx + (posicion.y - a.y) * dy) / largo2));
    const proyeccion = { x: a.x + t * dx, y: a.y + t * dy };
    const distancia = Math.hypot(posicion.x - proyeccion.x, posicion.y - proyeccion.y);

    // En empalmes o cruces equivalentes preferir el tramo más avanzado:
    // evita volver a conservar una sección que el vehículo ya dejó atrás.
    if (distancia < mejorDistancia - 1e-6
      || (Math.abs(distancia - mejorDistancia) <= 1e-6 && i > mejorIndice)) {
      mejorDistancia = distancia;
      mejorIndice = i;
      mejorProyeccion = proyeccion;
    }
  }

  if (mejorIndice < 0 || !mejorProyeccion || mejorDistancia > toleranciaKm) return ruta;
  return limpiarPuntosRuta([mejorProyeccion, ...ruta.slice(mejorIndice + 1)]);
}

function mismaGeometriaRuta(a: PuntoSimulacion[], b: PuntoSimulacion[]): boolean {
  if (a.length !== b.length) return false;
  return a.every((punto, i) =>
    Math.abs(punto.x - b[i].x) + Math.abs(punto.y - b[i].y) <= 1e-6);
}

function mismoObjetivo(a: Objetivo, b: Objetivo): boolean {
  if (a.tipo !== b.tipo) return false;
  if (a.tipo === 'vehiculo' && b.tipo === 'vehiculo') return a.id === b.id;
  if (a.tipo === 'almacen' && b.tipo === 'almacen') return a.id === b.id;
  if (a.tipo === 'pedido' && b.tipo === 'pedido') return a.id === b.id;
  if (a.tipo === 'nodo' && b.tipo === 'nodo') return a.x === b.x && a.y === b.y && a.ids.length === b.ids.length;
  return false;
}

/** Lo real va en línea continua y lo estimado, discontinuo: nunca se confunden. */
function dibujarRutaDestacada(
    ctx: CanvasRenderingContext2D,
    ruta: RutaDestacada,
    proy: Proyeccion,
    alfa: number
) {
  const color = colorDeVehiculo(ruta.idVehiculo);
  const aPixeles = (p: { x: number; y: number }): [number, number] => [proy.px(p.x), proy.py(p.y)];
  ctx.save();
  ctx.globalAlpha = alfa;
  ctx.lineJoin = 'round';
  ctx.lineCap = 'round';
  if (ruta.real.length > 1) {
    const pts = ruta.real.map(aPixeles);
    ctx.strokeStyle = '#ffffff';
    ctx.lineWidth = GROSOR_RUTA + 3;
    trazar(ctx, pts);
    ctx.strokeStyle = color;
    ctx.lineWidth = GROSOR_RUTA + 1;
    trazar(ctx, pts);
  }
  if (ruta.estimada.length > 1) {
    ctx.strokeStyle = color;
    ctx.lineWidth = GROSOR_RUTA + 0.5;
    ctx.lineCap = 'butt';
    ctx.setLineDash([7, 6]);
    trazar(ctx, ruta.estimada.map(aPixeles));
    ctx.setLineDash([]);
  }
  dibujarForma(ctx, 'destinoCliente', proy.px(ruta.destino.x), proy.py(ruta.destino.y), color);
  ctx.restore();
}

type PuntoPantalla = [number, number];
export function segmentosSeCruzan(a: PuntoPantalla, b: PuntoPantalla, c: PuntoPantalla, d: PuntoPantalla): boolean {
  if (Math.max(a[0], b[0]) < Math.min(c[0], d[0]) || Math.max(c[0], d[0]) < Math.min(a[0], b[0])
    || Math.max(a[1], b[1]) < Math.min(c[1], d[1]) || Math.max(c[1], d[1]) < Math.min(a[1], b[1])) return false;
  const cruz = (p: PuntoPantalla, q: PuntoPantalla, r: PuntoPantalla) => (q[0] - p[0]) * (r[1] - p[1]) - (q[1] - p[1]) * (r[0] - p[0]);
  return cruz(a, b, c) * cruz(a, b, d) <= 0 && cruz(c, d, a) * cruz(c, d, b) <= 0;
}
function trazarPrevision(ctx: CanvasRenderingContext2D, puntos: PuntoPantalla[], bloqueos: Array<[PuntoPantalla, PuntoPantalla]>) {
  ctx.save();
  for (let i = 1; i < puntos.length; i++) {
    const a = puntos[i - 1], b = puntos[i];
    const cerrado = bloqueos.some(([c, d]) => segmentosSeCruzan(a, b, c, d));
    ctx.setLineDash(cerrado ? [3, 5] : []);
    ctx.beginPath(); ctx.moveTo(a[0], a[1]); ctx.lineTo(b[0], b[1]); ctx.stroke();
  }
  ctx.restore();
}

function trazar(ctx: CanvasRenderingContext2D, pts: Array<[number, number]>) {
  ctx.beginPath();
  ctx.moveTo(pts[0][0], pts[0][1]);
  for (let i = 1; i < pts.length; i++) ctx.lineTo(pts[i][0], pts[i][1]);
  ctx.stroke();
}

function dibujarUnidad(ctx: CanvasRenderingContext2D, v: VehiculoSnapshot, x: number, y: number, rangos: RangosSemaforo) {
  ctx.save();
  ctx.globalAlpha = 1;
  ctx.fillStyle = BORDE;
  ctx.beginPath();
  ctx.arc(x, y, RADIO_MARCA, 0, Math.PI * 2);
  ctx.fill();
  ctx.restore();

  if (estaAveriado(v.estado)) {
    dibujarForma(ctx, obtenerFormaAveria(v.tipoAveria), x, y);
    return;
  }
  if (estaEnMantenimiento(v.estado)) {
    dibujarForma(ctx, 'mantenimiento', x, y);
    return;
  }

  const tipo = tipoDesdeCodigo(v.idVehiculo) ?? 'AUTO';

  const color = COLOR_SEMAFORO[clasificarVehiculo(v, rangos)];

  if (tipo === 'AUTO') dibujarForma(ctx, 'auto', x, y, color);
  else if (tipo === 'MOTO') dibujarForma(ctx, 'moto', x, y, color);
  else if (tipo === 'BICICLETA') dibujarForma(ctx, 'bicicleta', x, y, color);
  else {
    ctx.fillStyle = color;
    ctx.strokeStyle = BORDE;
    ctx.beginPath();
    ctx.arc(x, y, 5, 0, Math.PI * 2);
    ctx.fill();
    ctx.stroke();
  }
}

function etiqueta(ctx: CanvasRenderingContext2D, texto: string, x: number, y: number, color: string) {
  ctx.font = '600 11px system-ui, "Segoe UI", sans-serif';
  const w = ctx.measureText(texto).width;
  ctx.fillStyle = 'rgba(8,14,25,0.98)';
  ctx.fillRect(x - w / 2 - 4, y - 8, w + 8, 16);
  ctx.fillStyle = color;
  ctx.textAlign = 'center';
  ctx.textBaseline = 'middle';
  ctx.fillText(texto, x, y);
}

function insignia(ctx: CanvasRenderingContext2D, x: number, y: number, texto: string, resaltada: boolean) {
  ctx.font = 'bold 11px ui-monospace, Consolas, monospace';
  const w = Math.max(18, ctx.measureText(texto).width + 10);
  ctx.fillStyle = resaltada ? '#ffffff' : '#e8eef7';
  ctx.strokeStyle = BORDE;
  ctx.lineWidth = 1.5;
  ctx.beginPath();
  ctx.roundRect(x - w / 2, y - 9, w, 18, 9);
  ctx.fill();
  ctx.stroke();
  ctx.fillStyle = '#0a111e';
  ctx.textAlign = 'center';
  ctx.textBaseline = 'middle';
  ctx.fillText(texto, x, y + 0.5);
}