import { useEffect, useMemo, useState } from 'react';
import { simulacionService } from '../services/simulacionService';
import { useConsulta } from '../hooks/useConsulta';
import type { DetalleRecorridoVehiculo, PuntoRecorridoVehiculo } from '../types/api';
import { Cargando, ErrorCarga, Nota } from './ui/Avisos';
import { Boton } from './ui/Boton';
import { formatoDecimal, formatoEntero, formatoFechaHora } from '../utilitarios/formato';
import { COLOR_TIPO, COLOR_TIPO_DESCONOCIDO } from '../utilitarios/vehiculos';

interface AlmacenInicial { idAlmacen: string; nombre: string; tipo: string; x: number; y: number }

function crearCsv(id: number, vehiculo: string, puntos: PuntoRecorridoVehiculo[]) {
  const cabeceras = ['instante', 'x', 'y', 'estado', 'fase', 'evento', 'cargaPaquetes', 'pedidoObjetivo', 'distanciaTramoKm'] as const;
  const filas = [cabeceras.join(';'), ...puntos.map(p => cabeceras.map(c => {
    const v = p[c];
    const texto = v == null ? '' : String(v);
    const seguro = /^[\s]*[=+@\-]/.test(texto) && !/^-?\d+(\.\d+)?$/.test(texto) ? "'" + texto : texto;
    return `"${seguro.replaceAll('"', '""')}"`;
  }).join(';'))].join('\r\n');
  const url = URL.createObjectURL(new Blob(['\ufeff' + filas], { type: 'text/csv;charset=utf-8' }));
  const enlace = document.createElement('a');
  enlace.href = url;
  enlace.download = `recorrido_${id}_${vehiculo}.csv`;
  enlace.click();
  setTimeout(() => URL.revokeObjectURL(url), 4000);
}

const DESCRIPCION_FASE: Record<string, string> = {
  IR_ALMACEN: 'Hacia almacén', IR_RECOGER: 'Recogiendo carga', IR_PEDIDO: 'Hacia entrega',
  ATENDIENDO: 'Atendiendo al cliente', RETORNANDO: 'Regresando al almacén',
  DISPONIBLE: 'Disponible', AVARIADO: 'Averiado', EN_AVERIA: 'Averiado',
  EN_MANTENIMIENTO: 'Mantenimiento',
};

function nombreFase(p: PuntoRecorridoVehiculo) {
  return DESCRIPCION_FASE[p.fase] ?? p.fase.replaceAll('_', ' ').toLowerCase();
}

function colorTipo(tipo: string) {
  return tipo in COLOR_TIPO ? COLOR_TIPO[tipo as keyof typeof COLOR_TIPO] : COLOR_TIPO_DESCONOCIDO;
}

export function RecorridosCorrida({ idSimulacion, almacenes }: { idSimulacion: number; almacenes: AlmacenInicial[] }) {
  const [abierto, setAbierto] = useState(false);
  const resumen = useConsulta(s => abierto ? simulacionService.recorridos(idSimulacion, s) : Promise.resolve([]),
    `recorridos-${idSimulacion}-${abierto}`);
  const [vehiculo, setVehiculo] = useState('');
  const [indice, setIndice] = useState<number | null>(null);
  const [soloEventos, setSoloEventos] = useState(true);
  const [pagina, setPagina] = useState(0);
  const detalle = useConsulta(s => abierto && vehiculo
    ? simulacionService.recorridoVehiculo(idSimulacion, vehiculo, s) : Promise.resolve(null),
    `recorrido-${idSimulacion}-${abierto}-${vehiculo}`);

  useEffect(() => { setVehiculo(''); setIndice(null); setAbierto(false); }, [idSimulacion]);
  useEffect(() => {
    if (!vehiculo && resumen.datos?.length) setVehiculo(resumen.datos[0].idVehiculo);
  }, [vehiculo, resumen.datos]);
  useEffect(() => { setIndice(null); setPagina(0); }, [vehiculo, detalle.datos]);
  useEffect(() => { setPagina(0); }, [soloEventos]);

  const recorrido: DetalleRecorridoVehiculo | null = detalle.datos?.idVehiculo === vehiculo ? detalle.datos : null;
  const puntos = useMemo(() => recorrido?.puntos ?? [], [recorrido]);
  const activo = puntos.length ? Math.min(indice ?? puntos.length - 1, puntos.length - 1) : 0;
  const punto = puntos[activo];
  const eventos = useMemo(() => puntos.map((p, i) => ({ p, i }))
    .filter(({ p }, i) => !soloEventos || p.evento !== 'MOVIMIENTO' && p.evento !== 'ESTADO'
      || i === 0 || i === puntos.length - 1), [puntos, soloEventos]);
  const color = colorTipo(recorrido?.tipo ?? '');
  const paginas = Math.max(1, Math.ceil(eventos.length / 200));
  const paginaActual = Math.min(pagina, paginas - 1);
  const eventosVisibles = eventos.slice(paginaActual * 200, (paginaActual + 1) * 200);

  return <section className="border-t border-borde pt-3 space-y-3" aria-label="Recorridos de vehículos de la corrida">
    <button type="button" onClick={() => setAbierto(v => !v)} aria-expanded={abierto}
      className="w-full flex items-center justify-between gap-2 text-sm font-semibold text-mint hover:text-texto text-left">
      <span>Recorridos realizados por vehículo</span><span className="font-normal text-xs">{abierto ? 'Ocultar ▲' : 'Mostrar ▼'}</span>
    </button>
    {abierto && <div className="space-y-3">
      <Nota>Trayectoria ejecutada y guardada por corrida. Se registra cada nodo alcanzado y los cambios de actividad;
        durante esperas se conservan puntos de control cada 10 minutos simulados (cada hora en colapso). No es una ruta propuesta ni telemetría GPS.</Nota>
      {resumen.cargando && !resumen.datos && <Cargando texto="Cargando resumen de recorridos…" />}
      {resumen.error && <ErrorCarga titulo="No se pudieron consultar los recorridos" error={resumen.error} reintentar={resumen.recargar} />}
      {resumen.datos && <>
        {resumen.datos.length === 0 ? <p className="text-sm text-texto2">No hay vehículos archivados en esta corrida.</p> : <>
          <div className="flex flex-wrap gap-3 items-end">
            <label className="flex flex-col gap-1 text-xs text-texto2">
              Vehículo de la corrida
              <select className="bg-panel2 text-texto border border-borde rounded px-3 py-2 min-w-44" value={vehiculo}
                onChange={e => setVehiculo(e.target.value)}>
                {resumen.datos.map(r => <option key={r.idVehiculo} value={r.idVehiculo}>{r.idVehiculo} · {r.tipo} ({formatoEntero(r.puntos)} puntos)</option>)}
              </select>
            </label>
            <span className="text-xs text-texto2">{resumen.datos.filter(r => r.puntos > 0).length} de {resumen.datos.length} vehículos con recorrido registrado</span>
          </div>
          {detalle.cargando && !detalle.datos && <Cargando texto="Consultando trayectoria del vehículo…" />}
          {detalle.error && <ErrorCarga titulo="No se pudo consultar el trayecto" error={detalle.error} reintentar={detalle.recargar} />}
          {recorrido && (puntos.length ? <>
            <div className="grid grid-cols-2 sm:grid-cols-4 gap-2 text-xs">
              <Indicador titulo="Vehículo" valor={recorrido.idVehiculo} />
              <Indicador titulo="Distancia recorrida" valor={`${formatoDecimal(recorrido.distanciaRecorridaKm)} km`} />
              <Indicador titulo="Puntos archivados" valor={formatoEntero(puntos.length)} />
              <Indicador titulo="Último estado" valor={nombreFase(puntos[puntos.length - 1])} />
            </div>
            <div className="rounded-lg border border-borde bg-bg p-2">
              <svg className="w-full h-auto max-h-[500px]" viewBox="-2 -2 74 54" role="img"
                aria-label={`Ruta histórica completa de ${recorrido.idVehiculo} en la cuadrícula de 70 por 50 kilómetros`}>
                <rect x="0" y="0" width="70" height="50" fill="#0a1625" stroke="#46556b" strokeWidth="0.16" />
                {Array.from({ length: 15 }, (_, k) => <line key={`v${k}`} x1={k * 5} x2={k * 5} y1="0" y2="50" stroke="#1e3048" strokeWidth="0.12" />)}
                {Array.from({ length: 11 }, (_, k) => <line key={`h${k}`} x1="0" x2="70" y1={k * 5} y2={k * 5} stroke="#1e3048" strokeWidth="0.12" />)}
                {almacenes.map(a => <g key={a.idAlmacen}>
                  <rect x={a.x - 0.43} y={50 - a.y - 0.43} width="0.86" height="0.86" fill="#24c79a" stroke="#ffffff" strokeWidth="0.13" />
                  <title>{a.nombre} ({a.x}, {a.y})</title>
                </g>)}
                <polyline fill="none" stroke="#546479" strokeWidth="0.28" strokeLinejoin="round" strokeLinecap="round"
                  points={puntos.map(p => `${p.x},${50-p.y}`).join(' ')} />
                <polyline fill="none" stroke={color} strokeWidth="0.38" strokeLinejoin="round" strokeLinecap="round"
                  points={puntos.slice(0, activo + 1).map(p => `${p.x},${50-p.y}`).join(' ')} />
                <circle cx={puntos[0].x} cy={50-puntos[0].y} r="0.55" fill="#24c79a" stroke="#071322" strokeWidth="0.18" />
                <circle cx={punto.x} cy={50-punto.y} r="0.75" fill={color} stroke="#ffffff" strokeWidth="0.22" />
                <text x="1" y="49" fontSize="1.3" fill="#a7b6c9">0,0</text>
                <text x="59" y="49" fontSize="1.3" fill="#a7b6c9">70 km →</text>
              </svg>
              <p className="text-[11px] text-texto2 pt-2">Verde: inicio y almacenes · Línea gris: recorrido completo · Línea de color: hasta el instante seleccionado.</p>
            </div>
            <div className="rounded-lg bg-panel2 border border-borde p-3 space-y-2">
              <label htmlFor={`instante-${idSimulacion}`} className="flex flex-wrap justify-between gap-2 text-xs text-texto">
                <span>Inspeccionar la trayectoria</span>
                <span className="font-mono">{formatoFechaHora(punto.instante)}</span>
              </label>
              <input id={`instante-${idSimulacion}`} type="range" min={0} max={puntos.length - 1}
                value={activo} onChange={e => setIndice(Number(e.target.value))}
                className="w-full accent-emerald-400" aria-label="Posición en la línea temporal" />
              <div className="grid grid-cols-2 md:grid-cols-4 gap-2 text-xs text-texto2">
                <span>Posición: <b className="text-texto">({punto.x}, {punto.y})</b></span>
                <span>Actividad: <b className="text-texto">{nombreFase(punto)}</b></span>
                <span>Carga: <b className="text-texto">{punto.cargaPaquetes} paq.</b></span>
                <span>Pedido objetivo: <b className="text-texto">{punto.pedidoObjetivo ?? '—'}</b></span>
              </div>
              <div className="flex flex-wrap gap-2">
                <Boton variant="secundario" onClick={() => setIndice(0)}>Inicio</Boton>
                <Boton variant="secundario" onClick={() => setIndice(null)}>Fin</Boton>
                <Boton variant="secundario" onClick={() => crearCsv(idSimulacion, vehiculo, puntos)}>Exportar trayecto CSV</Boton>
              </div>
            </div>
            <div className="flex items-center justify-between gap-2">
              <h3 className="text-xs font-semibold uppercase tracking-wide text-texto2">Cronología de la unidad ({eventos.length} registros visibles)</h3>
              <label className="text-xs text-texto2 flex items-center gap-2">
                <input type="checkbox" checked={soloEventos} onChange={e => setSoloEventos(e.target.checked)} />
                Solo cambios relevantes
              </label>
            </div>
            <div className="max-h-72 overflow-auto border border-borde rounded-lg">
              <table className="w-full text-xs text-texto text-left">
                <thead className="sticky top-0 bg-panel2 text-texto2"><tr><th className="p-2">Hora</th><th className="p-2">Lugar</th><th className="p-2">Actividad</th><th className="p-2">Carga</th><th className="p-2">Evento</th></tr></thead>
                <tbody>{eventosVisibles.map(({ p, i }) => <tr key={i} onClick={() => setIndice(i)}
                  className={`border-t border-borde/50 cursor-pointer hover:bg-panel2 ${i === activo ? 'bg-mint/10' : ''}`}>
                  <td className="p-2 font-mono whitespace-nowrap">{formatoFechaHora(p.instante)}</td>
                  <td className="p-2 font-mono">({p.x}, {p.y})</td>
                  <td className="p-2">{nombreFase(p)}</td>
                  <td className="p-2">{p.cargaPaquetes}</td><td className="p-2">{p.evento}</td>
                </tr>)}</tbody>
              </table>
            </div>
            {paginas > 1 && <div className="flex gap-3 items-center justify-between text-xs text-texto2">
              <span>Página {paginaActual + 1} de {paginas} · {eventos.length} registros</span>
              <div className="flex gap-2">
                <button type="button" disabled={paginaActual === 0} onClick={() => setPagina(v => Math.max(0, v - 1))}
                  className="border border-borde rounded px-3 py-1 disabled:opacity-40">Anterior</button>
                <button type="button" disabled={paginaActual + 1 >= paginas} onClick={() => setPagina(v => Math.min(paginas - 1, v + 1))}
                  className="border border-borde rounded px-3 py-1 disabled:opacity-40">Siguiente</button>
              </div>
            </div>}
          </> : <Nota>Esta corrida no tiene puntos archivados para {vehiculo}. Si se ejecutó antes de esta modificación, el histórico de posiciones no se puede reconstruir fielmente a partir de los pedidos.</Nota>)}
        </>}
      </>}
    </div>}
  </section>;
}

function Indicador({ titulo, valor }: { titulo: string; valor: string }) {
  return <div className="bg-panel2 rounded border border-borde px-3 py-2">
    <div className="text-texto2">{titulo}</div><div className="text-texto font-semibold font-mono mt-1">{valor}</div>
  </div>;
}
