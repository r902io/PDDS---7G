import { useCallback, useEffect, useMemo, useRef, useState, type PointerEvent as EventoPuntero } from 'react';
import type { AlmacenOperativo, BloqueoRespuesta, PedidoOperativo, VehiculoSnapshot } from '../types/api';
import { dibujarForma, BORDE } from '../components/iconos/formas';
import {
  construirRutas,
  distanciaSegmento,
  polilineaPx,
  type Proyeccion,
  type RutaKm,
} from '../components/mapa/geometria';
import { colorDeVehiculo, estaAveriado, estaEnMantenimiento, tipoDesdeCodigo } from '../utilitarios/vehiculos';

/**
 * Motor de dibujo del mapa de la ciudad.
 *
 * - Retícula como 71 líneas verticales y 51 horizontales (ancho+1, alto+1).
 * - Escala = menor cociente entre espacio disponible y dimensiones de la
 *   ciudad → celdas cuadradas. El zoom solo multiplica esa escala base.
 * - Origen (0,0) abajo a la izquierda: el eje Y se invierte al proyectar.
 * - Resolución interna multiplicada por devicePixelRatio.
 */

export interface Dimensiones {
  anchoKm: number;
  altoKm: number;
}

export type Objetivo =
  | { tipo: 'vehiculo'; id: string }
  | { tipo: 'nodo'; x: number; y: number; ids: string[]; idAlmacen: string | null }
  | { tipo: 'almacen'; id: string }
  | { tipo: 'pedido'; id: number };

export type Seleccion = { tipo: 'vehiculo'; id: string } | { tipo: 'nodo'; x: number; y: number } | { tipo: 'pedido'; id: number } | null;

export interface EstadoHover {
  objetivo: Objetivo;
  /** Posición del cursor en píxeles CSS dentro del lienzo. */
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
  /** Atenúa todo el contenido (datos no vigentes). */
  atenuado: boolean;
}

const MARGEN = 34;
const GROSOR_RUTA = 2.4;
const RADIO_MARCA = 11;

interface Grupo {
  x: number;
  y: number;
  ids: string[];
  idAlmacen: string | null;
}

export function useMapCanvas(op: Opciones) {
  const canvasRef = useRef<HTMLCanvasElement | null>(null);
  const vehiculosCanvasRef = useRef<HTMLCanvasElement | null>(null);
  const anteriores = useRef<VehiculoSnapshot[] | null>(null);
  const corridaAnterior = useRef<number | null>(null);
  const ultimaRecepcion = useRef<number | null>(null);
  const transiciones = useRef<Map<string, Transicion>>(new Map());
  const gruposAnimados = useRef<Grupo[]>([]);
  const [tamano, setTamano] = useState({ w: 0, h: 0 });
  /** zoom multiplica la escala base; x/y desplazan el centro de la ciudad en px. */
  const [vista, setVista] = useState({ zoom: 1, x: 0, y: 0 });
  const [hover, setHover] = useState<EstadoHover | null>(null);
  const [cursorKm, setCursorKm] = useState<{ x: number; y: number } | null>(null);
  const arrastre = useRef<{ x0: number; y0: number; dx0: number; dy0: number; movido: boolean } | null>(null);
  const polilineas = useRef<Map<string, Array<[number, number]>>>(new Map());

  const { anchoKm, altoKm } = op.dimensiones;

  // Tamaño del contenedor.
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

  // Proyección km → px.
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

  const rutas: RutaKm[] = useMemo(() => construirRutas(op.vehiculos), [op.vehiculos]);

  // Vehículos agrupados por nodo. Los que están en un almacén quedan DENTRO de él.
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

  const vehiculoPorId = useMemo(() => new Map(op.vehiculos.map((v) => [v.idVehiculo, v])), [op.vehiculos]);

  // Vehículo resaltado: el que está bajo el cursor o, si no, el seleccionado.
  const enfocado: string | null =
    hover?.objetivo.tipo === 'vehiculo'
      ? hover.objetivo.id
      : op.seleccion?.tipo === 'vehiculo'
        ? op.seleccion.id
        : null;

  // ------------------------------------------------------------------------
  // Dibujo
  // ------------------------------------------------------------------------
  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas || tamano.w === 0 || tamano.h === 0) return;
    const ctx = canvas.getContext('2d');
    if (!ctx) return;

    const dpr = window.devicePixelRatio || 1;
    canvas.width = Math.round(tamano.w * dpr);
    canvas.height = Math.round(tamano.h * dpr);
    canvas.style.width = `${tamano.w}px`;
    canvas.style.height = `${tamano.h}px`;
    ctx.setTransform(dpr, 0, 0, dpr, 0, 0);

    const { px, py, escala } = proy;
    ctx.clearRect(0, 0, tamano.w, tamano.h);
    ctx.fillStyle = '#0a111e';
    ctx.fillRect(0, 0, tamano.w, tamano.h);

    // 1. Retícula: anchoKm+1 verticales y altoKm+1 horizontales, líneas continuas.
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

    const alfaBase = op.atenuado ? 0.45 : 1;

    // 2. Bloqueos activos (programados desde el archivo mensual).
    for (const b of op.bloqueos) {
      if (b.vertices.length < 2) continue;
      ctx.save();
      ctx.globalAlpha = alfaBase;
      ctx.strokeStyle = '#ef4444';
      ctx.lineWidth = 4;
      ctx.lineCap = 'round';
      ctx.lineJoin = 'miter';
      ctx.beginPath();
      // Se respeta la ortogonalidad aunque un vértice llegara mal formado.
      let previo = b.vertices[0];
      ctx.moveTo(px(previo.x), py(previo.y));
      for (let i = 1; i < b.vertices.length; i++) {
        const v = b.vertices[i];
        if (v.x !== previo.x && v.y !== previo.y) ctx.lineTo(px(v.x), py(previo.y));
        ctx.lineTo(px(v.x), py(v.y));
        previo = v;
      }
      ctx.stroke();
      const a = b.vertices[0];
      const z = b.vertices[1];
      dibujarForma(ctx, 'bloqueo', (px(a.x) + px(z.x)) / 2, (py(a.y) + py(z.y)) / 2);
      ctx.restore();
    }

    // 3. Rutas pendientes (caminoActual), con carriles en tramos compartidos.
    const nuevas = new Map<string, Array<[number, number]>>();
    const ordenadas = [...rutas].sort((r1, r2) =>
      r1.idVehiculo === enfocado ? 1 : r2.idVehiculo === enfocado ? -1 : 0
    );
    for (const r of ordenadas) {
      const pts = polilineaPx(r, proy, GROSOR_RUTA);
      nuevas.set(r.idVehiculo, pts);
      if (pts.length < 2) continue;
      const esEnfocada = r.idVehiculo === enfocado;
      const color = colorDeVehiculo(r.idVehiculo);
      ctx.save();
      ctx.globalAlpha = alfaBase * (enfocado == null ? 0.85 : esEnfocada ? 1 : 0.15);
      ctx.lineJoin = 'miter';
      ctx.lineCap = 'butt';
      if (esEnfocada) {
        ctx.strokeStyle = '#ffffff';
        ctx.lineWidth = GROSOR_RUTA + 3;
        trazar(ctx, pts);
      }
      ctx.strokeStyle = color;
      ctx.lineWidth = esEnfocada ? GROSOR_RUTA + 1 : GROSOR_RUTA;
      trazar(ctx, pts);
      if (r.destino && vehiculoPorId.get(r.idVehiculo)?.pedidoObjetivo != null) {
        dibujarForma(ctx, 'destinoCliente', px(r.destino.x), py(r.destino.y), color);
      }
      ctx.restore();
    }
    polilineas.current = nuevas;

    // Destinos de pedidos que YA ingresaron. Los históricos no pertenecen a esta lista.
    for (const p of op.pedidos) {
      if (p.estado === 'ENTREGADO') continue;
      const x = px(p.ubicacionX), y = py(p.ubicacionY);
      const color = p.estado === 'RETRASADO' ? '#f87171' : p.estado === 'EN_RUTA' ? '#60a5fa' : '#fbbf24';
      ctx.save(); ctx.globalAlpha = alfaBase * 0.85;
      ctx.fillStyle = color; ctx.strokeStyle = '#101827'; ctx.lineWidth = 1.2;
      ctx.beginPath(); ctx.arc(x, y, 4.5, 0, Math.PI * 2); ctx.fill(); ctx.stroke();
      if (op.seleccion?.tipo === 'pedido' && op.seleccion.id === p.idPedido) {
        ctx.strokeStyle = '#ffffff'; ctx.lineWidth = 2;
        ctx.beginPath(); ctx.arc(x, y, 8, 0, Math.PI * 2); ctx.stroke();
      }
      ctx.restore();
    }

    // 4. Almacenes, con la cantidad de unidades que contienen.
    for (const a of op.almacenes) {
      const x = px(a.ubicacionX);
      const y = py(a.ubicacionY);
      const central = a.tipo === 'CENTRAL';
      ctx.save();
      ctx.globalAlpha = alfaBase;
      dibujarForma(ctx, central ? 'almacenCentral' : 'almacenIntermedio', x, y, undefined, 1.25);
      etiqueta(ctx, `${a.nombre} (${a.ubicacionX},${a.ubicacionY})`, x, y + 18, '#34d399');
      ctx.restore();
    }

  }, [tamano, proy, rutas, grupos, op.almacenes, op.bloqueos, op.pedidos, op.seleccion, op.atenuado, enfocado, vehiculoPorId, anchoKm, altoKm]);

  useEffect(() => {
    const ahora = performance.now();
    if (corridaAnterior.current !== op.corridaId) {
      anteriores.current = null;
      ultimaRecepcion.current = null;
      corridaAnterior.current = op.corridaId;
    }
    const previos = anteriores.current;
    const intervalo = ultimaRecepcion.current == null ? 500 : ahora - ultimaRecepcion.current;
    const duracion = Math.max(150, Math.min(650, intervalo * 0.88));
    const nuevos = new Map<string, Transicion>();
    const porId = new Map(previos?.map((v) => [v.idVehiculo, v]) ?? []);
    const cerrados = nodosCerrados(op.bloqueos);

    for (const actual of op.vehiculos) {
      const previo = porId.get(actual.idVehiculo);
      if (!previo || (previo.x === actual.x && previo.y === actual.y)
          || estaAveriado(actual.estado) || estaEnMantenimiento(actual.estado)) continue;
      const camino = caminoRecorrido(previo, actual, cerrados);
      if (camino) nuevos.set(actual.idVehiculo, { puntos: camino, inicio: ahora, duracion });
    }

    transiciones.current = nuevos;
    anteriores.current = op.vehiculos;
    ultimaRecepcion.current = ahora;
  }, [op.vehiculos, op.corridaId]);

  useEffect(() => {
    const canvas = vehiculosCanvasRef.current;
    if (!canvas || tamano.w === 0 || tamano.h === 0) return;
    const ctx = canvas.getContext('2d');
    if (!ctx) return;
    const dpr = window.devicePixelRatio || 1;
    canvas.width = Math.round(tamano.w * dpr);
    canvas.height = Math.round(tamano.h * dpr);
    canvas.style.width = `${tamano.w}px`;
    canvas.style.height = `${tamano.h}px`;
    ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
    const cerrados = nodosCerrados(op.bloqueos);
    let frame = 0;

    const dibujar = (ahora: number) => {
      ctx.clearRect(0, 0, tamano.w, tamano.h);
      const posiciones = new Map<string, { x: number; y: number }>();
      let activo = false;
      for (const v of op.vehiculos) {
        const tr = transiciones.current.get(v.idVehiculo);
        if (tr && !tr.puntos.some((n) => cerrados.has(`${n.x},${n.y}`))) {
          const avance = Math.min(1, Math.max(0, (ahora - tr.inicio) / tr.duracion));
          posiciones.set(v.idVehiculo, posicionEnCamino(tr.puntos, avance));
          if (avance < 1) activo = true;
        } else {
          posiciones.set(v.idVehiculo, { x: v.x, y: v.y });
        }
      }

      const agrupados = new Map<string, Grupo>();
      for (const v of op.vehiculos) {
        const pos = posiciones.get(v.idVehiculo)!;
        const clave = `${pos.x.toFixed(4)},${pos.y.toFixed(4)}`;
        let grupo = agrupados.get(clave);
        if (!grupo) {
          const alm = op.almacenes.find((a) => a.ubicacionX === pos.x && a.ubicacionY === pos.y);
          grupo = { ...pos, ids: [], idAlmacen: alm?.idAlmacen ?? null };
          agrupados.set(clave, grupo);
        }
        grupo.ids.push(v.idVehiculo);
      }
      gruposAnimados.current = [...agrupados.values()];

      for (const g of gruposAnimados.current) {
        const x = proy.px(g.x);
        const y = proy.py(g.y);
        const contieneEnfocado = enfocado != null && g.ids.includes(enfocado);
        ctx.save();
        ctx.globalAlpha = (op.atenuado ? 0.45 : 1) * (enfocado == null || contieneEnfocado ? 1 : 0.35);
        if (g.idAlmacen) {
          insignia(ctx, x + 22, y - 2, String(g.ids.length), contieneEnfocado);
          ctx.restore();
          continue;
        }
        const seleccionado =
          (op.seleccion?.tipo === 'nodo' && op.seleccion.x === g.x && op.seleccion.y === g.y) ||
          (op.seleccion?.tipo === 'vehiculo' && g.ids.includes(op.seleccion.id));
        if (seleccionado || contieneEnfocado) {
          ctx.strokeStyle = '#ffffff';
          ctx.lineWidth = 2;
          ctx.beginPath();
          ctx.arc(x, y, RADIO_MARCA + 3, 0, Math.PI * 2);
          ctx.stroke();
        }
        if (g.ids.length === 1) {
          const unidad = vehiculoPorId.get(g.ids[0]);
          if (unidad) dibujarUnidad(ctx, unidad, x, y);
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
          ctx.fillText(String(g.ids.length), x, y + 0.5);
          const estados = g.ids.map((id) => vehiculoPorId.get(id)?.estado);
          if (estados.some(estaAveriado)) dibujarForma(ctx, 'averia', x + 12, y - 11, undefined, 0.8);
          else if (estados.some(estaEnMantenimiento)) dibujarForma(ctx, 'mantenimiento', x + 12, y - 11, undefined, 0.8);
        }
        ctx.restore();
      }
      if (activo) frame = requestAnimationFrame(dibujar);
    };
    frame = requestAnimationFrame(dibujar);
    return () => cancelAnimationFrame(frame);
  }, [op.vehiculos, op.almacenes, op.bloqueos, op.seleccion, op.atenuado, tamano, proy, enfocado, vehiculoPorId]);

  // ------------------------------------------------------------------------
  // Interacción
  // ------------------------------------------------------------------------

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
      // Marcas de unidades y almacenes primero.
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

      // Pedidos activos: sus marcadores son clicables cuando no se superponen con vehículos.
      for (const p of op.pedidos) {
        if (p.estado === 'ENTREGADO') continue;
        const d = Math.hypot(mx - proy.px(p.ubicacionX), my - proy.py(p.ubicacionY));
        if (d <= 8) return { tipo: 'pedido', id: p.idPedido };
      }

      // Luego, el trazo de las rutas.
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
    else if (o.tipo === 'nodo') op.alSeleccionar({ tipo: 'nodo', x: o.x, y: o.y });
    else if (o.tipo === 'pedido') op.alSeleccionar({ tipo: 'pedido', id: o.id });
    else op.alSeleccionar(null);
  };

  const onPointerLeave = () => {
    arrastre.current = null;
    setHover(null);
    setCursorKm(null);
  };

  // Rueda: zoom centrado en el cursor (listener no pasivo para evitar el scroll de la página).
  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas) return;
    const alGirar = (e: WheelEvent) => {
      e.preventDefault();
      const r = canvas.getBoundingClientRect();
      const mx = e.clientX - r.left;
      const my = e.clientY - r.top;
      const factor = e.deltaY < 0 ? 1.15 : 1 / 1.15;
      setVista((v) => {
        const zoom = Math.max(1, Math.min(8, v.zoom * factor));
        if (zoom === 1) return { zoom: 1, x: 0, y: 0 };
        // Mantener fijo el punto bajo el cursor: el centro de la ciudad está en (w/2 + x, h/2 + y).
        const k = zoom / v.zoom;
        const cx = tamano.w / 2 + v.x;
        const cy = tamano.h / 2 + v.y;
        return { zoom, x: v.x + (mx - cx) * (1 - k), y: v.y + (my - cy) * (1 - k) };
      });
    };
    canvas.addEventListener('wheel', alGirar, { passive: false });
    return () => canvas.removeEventListener('wheel', alGirar);
  }, [tamano]);

  const encuadrar = useCallback(() => {
    setVista({ zoom: 1, x: 0, y: 0 });
  }, []);

  return {
    canvasRef,
    vehiculosCanvasRef,
    hover,
    cursorKm,
    escalaPx: proy.escala,
    zoom: vista.zoom,
    encuadrar,
    manejadores: { onPointerDown, onPointerMove, onPointerUp, onPointerLeave },
  };
}

interface Transicion {
  puntos: Array<{ x: number; y: number }>;
  inicio: number;
  duracion: number;
}

function nodosCerrados(bloqueos: BloqueoRespuesta[]): Set<string> {
  const cerrados = new Set<string>();
  for (const bloqueo of bloqueos) {
    const vertices = bloqueo.vertices;
    if (vertices.length === 0 || vertices.some((v) => !Number.isFinite(v.x) || !Number.isFinite(v.y))) continue;
    let anterior = { x: Math.round(vertices[0].x), y: Math.round(vertices[0].y) };
    cerrados.add(`${anterior.x},${anterior.y}`);
    for (let i = 1; i < vertices.length; i++) {
      const actual = { x: Math.round(vertices[i].x), y: Math.round(vertices[i].y) };
      let x = anterior.x;
      let y = anterior.y;
      while (x !== actual.x) {
        x += Math.sign(actual.x - x);
        cerrados.add(`${x},${y}`);
      }
      while (y !== actual.y) {
        y += Math.sign(actual.y - y);
        cerrados.add(`${x},${y}`);
      }
      anterior = actual;
    }
  }
  return cerrados;
}

function caminoRecorrido(
  previo: VehiculoSnapshot,
  actual: VehiculoSnapshot,
  cerrados: Set<string>
): Array<{ x: number; y: number }> | null {
  const puntos = [{ x: previo.x, y: previo.y }];
  const destino = `${actual.x},${actual.y}`;
  let encontrado = false;
  for (const nodo of previo.caminoActual.slice(0, 256)) {
    const anterior = puntos[puntos.length - 1];
    if (Math.abs(anterior.x - nodo.x) + Math.abs(anterior.y - nodo.y) !== 1) return null;
    puntos.push({ x: nodo.x, y: nodo.y });
    if (`${nodo.x},${nodo.y}` === destino) {
      encontrado = true;
      break;
    }
  }
  if (!encontrado) {
    if (previo.caminoActual.length > 0
        || Math.abs(previo.x - actual.x) + Math.abs(previo.y - actual.y) !== 1) return null;
    puntos.length = 1;
    let x = previo.x;
    let y = previo.y;
    while (x !== actual.x || y !== actual.y) {
      x += Math.sign(actual.x - x);
      y += Math.sign(actual.y - y);
      puntos.push({ x, y });
      if (puntos.length > 65) return null;
    }
  }
  if (puntos.some((p) => cerrados.has(`${p.x},${p.y}`))) return null;
  return puntos.length >= 2 ? puntos : null;
}

function posicionEnCamino(puntos: Array<{ x: number; y: number }>, avance: number) {
  const distancia = (puntos.length - 1) * avance;
  const tramo = Math.min(puntos.length - 2, Math.floor(distancia));
  const t = distancia - tramo;
  const a = puntos[tramo];
  const b = puntos[tramo + 1];
  return { x: a.x + (b.x - a.x) * t, y: a.y + (b.y - a.y) * t };
}

function mismoObjetivo(a: Objetivo, b: Objetivo): boolean {
  if (a.tipo !== b.tipo) return false;
  if (a.tipo === 'vehiculo' && b.tipo === 'vehiculo') return a.id === b.id;
  if (a.tipo === 'almacen' && b.tipo === 'almacen') return a.id === b.id;
  if (a.tipo === 'pedido' && b.tipo === 'pedido') return a.id === b.id;
  if (a.tipo === 'nodo' && b.tipo === 'nodo') return a.x === b.x && a.y === b.y && a.ids.length === b.ids.length;
  return false;
}

function trazar(ctx: CanvasRenderingContext2D, pts: Array<[number, number]>) {
  ctx.beginPath();
  ctx.moveTo(pts[0][0], pts[0][1]);
  for (let i = 1; i < pts.length; i++) ctx.lineTo(pts[i][0], pts[i][1]);
  ctx.stroke();
}

function dibujarUnidad(ctx: CanvasRenderingContext2D, v: VehiculoSnapshot, x: number, y: number) {
  // Fondo para que el icono se lea sobre las rutas.
  ctx.fillStyle = 'rgba(10,17,30,0.85)';
  ctx.beginPath();
  ctx.arc(x, y, RADIO_MARCA, 0, Math.PI * 2);
  ctx.fill();
  if (estaAveriado(v.estado)) {
    dibujarForma(ctx, 'averia', x, y);
    return;
  }
  if (estaEnMantenimiento(v.estado)) {
    dibujarForma(ctx, 'mantenimiento', x, y);
    return;
  }
  const tipo = tipoDesdeCodigo(v.idVehiculo);
  const color = colorDeVehiculo(v.idVehiculo);
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
  ctx.fillStyle = 'rgba(8,14,25,0.88)';
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
