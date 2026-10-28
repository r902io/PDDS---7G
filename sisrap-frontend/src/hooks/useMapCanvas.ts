import { useCallback, useEffect, useMemo, useRef, useState, type PointerEvent as EventoPuntero } from 'react';
import type { AlmacenOperativo, BloqueoRespuesta, PedidoOperativo, VehiculoSnapshot } from '../types/api';
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
    | { tipo: 'nodo'; x: number; y: number }
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
  const animador = useRef(new AnimadorVehiculos());
  const flujo = useFlujoVisual();
  const despertar = useRef<(() => void) | null>(null);
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

    const alfaBase = op.atenuado ? 0.45 : 1;

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
  }, [tamano, proy, op.pedidos, op.seleccion, op.atenuado, anchoKm, altoKm]);

  useEffect(() => {
    const recibir = () => {
      const s = flujo?.actual;
      const reducir = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
      const animar = (s ? s.estado === 'EJECUTANDO' : op.animar)
          && !op.atenuado && !document.hidden && !reducir;
      const muestra = s ?? op.vehiculos;
      if (ultimaMuestra.current === muestra && animar && animacionHabilitada.current) return;
      const corrida = s?.idSimulacion ?? op.corridaId;
      if (corridaVisual.current !== corrida) {
        polilineas.current.clear();
        gruposAnimados.current = [];
        corridaVisual.current = corrida;
      }
      vehiculosVisuales.current = s?.vehiculos ?? op.vehiculos;
      animacionHabilitada.current = animar;
      ultimaMuestra.current = muestra;
      animador.current.actualizar(s?.vehiculos ?? op.vehiculos, s?.idSimulacion ?? op.corridaId,
          performance.now(), animar, s?.relojSimulado);
      despertar.current?.();
    };
    recibir();
    flujo?.oyentes.add(recibir);
    return () => { flujo?.oyentes.delete(recibir); };
  }, [flujo, op.vehiculos, op.corridaId, op.animar, op.atenuado]);

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
    let ultimoDibujo = -Infinity;

    const dibujar = (ahora: number) => {
      frame = 0;
      if (ahora - ultimoDibujo < 1_000 / 30) {
        frame = requestAnimationFrame(dibujar);
        return;
      }
      ultimoDibujo = ahora;
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
      const vehiculoPorId = new Map(vehiculos.map(v => [v.idVehiculo, v]));
      const posiciones = new Map<string, { x: number; y: number }>();
      const activo = animador.current.enMovimiento(ahora);

      for (const v of vehiculos) {
        const vx = v.xVisual ?? v.x;
        const vy = v.yVisual ?? v.y;
        posiciones.set(v.idVehiculo, { x: vx, y: vy });

        const x = px(vx);
        const y = py(vy);

        const tipo = tipoDesdeCodigo(v.idVehiculo ?? '') ?? 'AUTO';
        const color = tipo ? COLOR_TIPO[tipo] : '#e8eef7';

        ctx.textAlign = 'center';

        if (estaAveriado(v.estado)) {
          const formaAveria = obtenerFormaAveria(v.tipoAveria);
          dibujarForma(ctx, formaAveria, x, y - 12, undefined, 1.2);
          ctx.font = 'bold 9px monospace';
          ctx.fillStyle = '#ffffff';
          ctx.fillText(v.idVehiculo, x, y + 14);
        } else if (estaEnMantenimiento(v.estado)) {
          dibujarForma(ctx, 'mantenimiento', x, y - 10, undefined, 1.1);
          ctx.font = 'bold 9px monospace';
          ctx.fillStyle = '#38bdf8';
          ctx.fillText(`${v.idVehiculo} (MANT)`, x, y + 12);
        } else {
          // Ahora TypeScript garantiza que 'tipo' es string y permite llamar toLowerCase()
          const formaVehiculo = tipo.toLowerCase() as NombreForma;

          dibujarForma(ctx, formaVehiculo, x, y, color);

          ctx.font = 'bold 9px monospace';
          ctx.fillStyle = '#cbd5e1';
          ctx.fillText(v.idVehiculo, x, y + 12);
        }
      }

      const alfaBase = op.atenuado ? 0.45 : 1;
      const nuevas = new Map<string, Array<[number, number]>>();

      const vehiculosOrdenados = [...vehiculos].sort((a, b) =>
          a.idVehiculo === enfocado ? 1 : b.idVehiculo === enfocado ? -1 : 0
      );

      for (const v of vehiculosOrdenados) {
        if (estaAveriado(v.estado) || estaEnMantenimiento(v.estado)) continue;

        const id = v.idVehiculo;
        const esEnfocado = id === enfocado;

        const { recorrido, pendiente } = animador.current.camino(id, ahora);
        const ptsRecorrido: Array<[number, number]> = (recorrido || []).map(p => [px(p.x), py(p.y)]);
        const ptsPendiente: Array<[number, number]> = (pendiente || []).map(p => [px(p.x), py(p.y)]);
        const ptsTotales = [...ptsRecorrido, ...ptsPendiente];

        if (ptsTotales.length < 2) continue;
        nuevas.set(id, ptsTotales);

        ctx.save();
        const alfaRuta = enfocado == null ? 0.85 : (esEnfocado ? 1.0 : 0.2);
        ctx.globalAlpha = alfaBase * alfaRuta;
        ctx.lineJoin = 'round';
        ctx.lineCap = 'round';

        if (ptsRecorrido.length > 1) {
          ctx.save();
          ctx.globalAlpha *= 0.3;
          ctx.strokeStyle = colorDeVehiculo(id);
          ctx.lineWidth = GROSOR_RUTA;
          trazar(ctx, ptsRecorrido);
          ctx.restore();
        }

        if (ptsPendiente.length > 1) {
          let colorSem = colorDeVehiculo(id);
          const estadoSemaforo = animador.current.obtenerColorSemaforo(id);
          if (estadoSemaforo === 'ROJO') colorSem = '#ef4444';
          else if (estadoSemaforo === 'AMBAR') colorSem = '#f59e0b';
          else if (estadoSemaforo === 'VERDE') colorSem = '#22c55e';

          if (esEnfocado) {
            ctx.strokeStyle = '#ffffff';
            ctx.lineWidth = GROSOR_RUTA + 3;
            trazar(ctx, ptsPendiente);
          }
          ctx.strokeStyle = colorSem;
          ctx.lineWidth = esEnfocado ? GROSOR_RUTA + 1 : GROSOR_RUTA;
          trazar(ctx, ptsPendiente);

          const ultimoPunto = pendiente[pendiente.length - 1];
          if (ultimoPunto && v.pedidoObjetivo != null) {
            dibujarForma(ctx, 'destinoCliente', px(ultimoPunto.x), py(ultimoPunto.y), colorSem);
          }
        }
        ctx.restore();
      }

      polilineas.current = nuevas;

      for (const b of op.bloqueos) {
        if (b.vertices.length < 2) continue;
        ctx.save();
        ctx.globalAlpha = alfaBase;
        ctx.strokeStyle = '#ef4444';
        ctx.lineWidth = 4;
        ctx.lineCap = 'round';
        ctx.lineJoin = 'miter';
        ctx.beginPath();
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

      for (const a of op.almacenes) {
        const x = px(a.ubicacionX);
        const y = py(a.ubicacionY);
        const central = a.tipo === 'CENTRAL';

        const semaforo = obtenerColorSemaforoAlmacen(a);
        const colorSem = obtenerColorHexAlmacen(semaforo);

        ctx.save();
        ctx.globalAlpha = alfaBase;
        dibujarForma(ctx, central ? 'almacenCentral' : 'almacenIntermedio', x, y, colorSem, 1.25);
        etiqueta(ctx, `${a.nombre} (${a.ubicacionX},${a.ubicacionY})`, x, y + 18, colorSem);
        ctx.restore();
      }

      const agrupados = new Map<string, Grupo>();
      for (const v of vehiculos) {
        const pos = posiciones.get(v.idVehiculo);
        if (!pos) continue;
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
        const x = px(g.x);
        const y = py(g.y);
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
          if (unidad) dibujarUnidad(ctx, unidad, x, y, animador.current);
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
          if (estados.some((e) => (e ? estaAveriado(e) : false))) {
            const tipoAveria = g.ids
                .map((id) => vehiculoPorId.get(id)?.tipoAveria)
                .find(Boolean);

            dibujarForma(ctx, obtenerFormaAveria(tipoAveria), x + 12, y - 11, undefined, 0.8);
          } else if (estados.some((e) => (e ? estaEnMantenimiento(e) : false))) {
            dibujarForma(ctx, 'mantenimiento', x + 12, y - 11, undefined, 0.8);
          }
        }
        ctx.restore();
      }
      if ((activo || true) && !document.hidden) frame = requestAnimationFrame(dibujar);
    };

    const alCambiarVisibilidad = () => {
      cancelAnimationFrame(frame);
      if (!document.hidden) {
        const s = flujo?.actual;
        animador.current.actualizar(s?.vehiculos ?? op.vehiculos, s?.idSimulacion ?? op.corridaId,
            performance.now(), false, s?.relojSimulado);
        animacionHabilitada.current = false;
        frame = requestAnimationFrame(dibujar);
      }
    };
    despertar.current = () => { if (!frame && !document.hidden) frame = requestAnimationFrame(dibujar); };
    document.addEventListener('visibilitychange', alCambiarVisibilidad);
    frame = requestAnimationFrame(dibujar);
    return () => {
      cancelAnimationFrame(frame);
      despertar.current = null;
      document.removeEventListener('visibilitychange', alCambiarVisibilidad);
    };
  }, [op.vehiculos, op.corridaId, op.animar, op.almacenes, op.bloqueos, op.seleccion, op.atenuado, tamano, proy, enfocado]);

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
    else if (o.tipo === 'nodo') op.alSeleccionar({ tipo: 'nodo', x: o.x, y: o.y });
    else if (o.tipo === 'pedido') op.alSeleccionar({ tipo: 'pedido', id: o.id });
    else op.alSeleccionar(null);
  };

  const onPointerLeave = () => {
    arrastre.current = null;
    setHover(null);
    setCursorKm(null);
  };

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
    relojVisualRef,
    vehiculosCanvasRef,
    hover,
    cursorKm,
    escalaPx: proy.escala,
    zoom: vista.zoom,
    encuadrar,
    manejadores: { onPointerDown, onPointerMove, onPointerUp, onPointerLeave },
  };
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

function dibujarUnidad(ctx: CanvasRenderingContext2D, v: VehiculoSnapshot, x: number, y: number, animador: AnimadorVehiculos) {
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

  let color = colorDeVehiculo(v.idVehiculo);
  const estadoSemaforo = animador.obtenerColorSemaforo(v.idVehiculo);

  if (estadoSemaforo === 'ROJO') color = '#ef4444';
  else if (estadoSemaforo === 'AMBAR') color = '#f59e0b';
  else if (estadoSemaforo === 'VERDE') color = '#22c55e';

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