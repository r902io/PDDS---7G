import { useMemo, useState } from 'react';
import { useConsulta } from '../hooks/useConsulta';
import { simulacionService } from '../services/simulacionService';
import { Boton } from '../components/ui/Boton';
import { ErrorCarga, Cargando, Nota } from '../components/ui/Avisos';
import { formatoDecimal, formatoEntero, formatoFechaHora, formatoSoles } from '../utilitarios/formato';

function archivo(nombre: string, contenido: string, tipo: string) {
  const url = URL.createObjectURL(new Blob(['\ufeff' + contenido], { type: tipo }));
  const a = document.createElement('a'); a.href = url; a.download = nombre; a.click();
  setTimeout(() => URL.revokeObjectURL(url), 4000);
}
function csvValor(valor: unknown): string {
  const s = valor == null ? '' : typeof valor === 'object' ? JSON.stringify(valor) : String(valor);
  // Protege de la ejecución de fórmulas cuando se abre el CSV en Excel.
  const safe = /^[\s]*[=+@\-]/.test(s) && !/^-?\d+(\.\d+)?$/.test(s) ? "'" + s : s;
  return `"${safe.replace(/"/g, '""')}"`;
}
function exportarCsv<T extends object>(nombre: string, filas: T[]) {
  if (!filas.length) return;
  const cabeceras = Object.keys(filas[0]);
  archivo(nombre, [cabeceras.map(csvValor).join(';'), ...filas.map(f => cabeceras.map(c => csvValor((f as Record<string, unknown>)[c])).join(';'))].join('\r\n'), 'text/csv;charset=utf-8');
}
function Cuadro({ titulo, valor }: { titulo: string; valor: string }) {
  return <div className="bg-panel2 rounded border border-borde px-3 py-2">
    <dt className="text-xs text-texto2">{titulo}</dt><dd className="text-lg font-mono text-texto font-semibold">{valor}</dd>
  </div>;
}

/** No depende de valores precalculados por el navegador: consulta el archivo de esta corrida. */
export function DetalleResultado({ idSimulacion }: { idSimulacion: number }) {
  const consulta = useConsulta(s => simulacionService.resultado(idSimulacion, s), `resultado-${idSimulacion}`);
  const [compararId, setCompararId] = useState('');
  const [comparando, setComparando] = useState<number | null>(null);
  const anterior = useConsulta(s => comparando == null ? Promise.resolve(null) : simulacionService.resultado(comparando, s), `comparacion-${comparando}`);
  const r = consulta.datos;
  const llegadasATiempo = r?.pedidos.filter(p => {
    const horas = Number(p.prioridad.match(/(36|18|12|8|4)H$/)?.[1]);
    const registro = Date.parse(p.fechaRegistro);
    const llegada = p.fechaArribo ? Date.parse(p.fechaArribo) : NaN;
    return Number.isFinite(registro) && Number.isFinite(llegada) && Number.isFinite(horas)
      && llegada <= registro + horas * 3_600_000;
  }).length ?? 0;
  const cumplimiento = r && r.totalPedidos > 0 ? 100 * llegadasATiempo / r.totalPedidos : null;
  const comparacion = useMemo(() => {
    if (!r || !anterior.datos) return null;
    return [
      { k: 'Pedidos', ahora: r.totalPedidos, anterior: anterior.datos.totalPedidos, unidad: '' },
      { k: 'Entregados', ahora: r.entregados, anterior: anterior.datos.entregados, unidad: '' },
      { k: 'Retrasados', ahora: r.retrasados, anterior: anterior.datos.retrasados, unidad: '' },
      { k: 'Costo planificado', ahora: r.costoPlanificado, anterior: anterior.datos.costoPlanificado, unidad: 'S/ ' },
      { k: 'Distancia planificada', ahora: r.distanciaPlanificadaKm, anterior: anterior.datos.distanciaPlanificadaKm, unidad: 'km ' },
    ];
  }, [r, anterior.datos]);

  return <section className="rounded-lg border border-borde bg-panel p-4 space-y-4" aria-label="Resultados archivados de la corrida">
    <div className="flex flex-wrap items-center justify-between gap-2">
      <div><h2 className="text-base font-semibold text-texto">Registro completo de la corrida #{idSimulacion}</h2>
        <p className="text-xs text-texto2">Datos conservados independientemente de próximas simulaciones.</p></div>
      <Boton variant="secundario" onClick={consulta.recargar}>Actualizar resultado</Boton>
    </div>
    {consulta.error && <ErrorCarga titulo="No se pudo obtener el resultado persistido" error={consulta.error} reintentar={consulta.recargar} />}
    {!r && !consulta.error && <Cargando texto="Consultando el registro de la simulación…" />}
    {r && <>
      <dl className="grid grid-cols-2 md:grid-cols-3 gap-2">
        <Cuadro titulo="Pedidos generados" valor={formatoEntero(r.totalPedidos)} />
        <Cuadro titulo="Entregados" valor={formatoEntero(r.entregados)} />
        <Cuadro titulo="Fuera de plazo" valor={formatoEntero(r.retrasados)} />
        <Cuadro titulo="Costo planificado" valor={formatoSoles(r.costoPlanificado)} />
        <Cuadro titulo="Distancia planificada" valor={`${formatoDecimal(r.distanciaPlanificadaKm)} km`} />
        <Cuadro titulo="Llegadas dentro de plazo" valor={cumplimiento !== null ? `${formatoDecimal(cumplimiento)} %` : '—'} />
      </dl>
      <Nota>Los valores de costo y distancia corresponden a las rutas planificadas registradas por el backend. No deben interpretarse como recorrido real sin telemetría adicional. Se considera cumplido el plazo cuando el vehículo llega al cliente antes de su límite; la descarga posterior no se incluye.</Nota>
      {r.retrasados > 0 && <div className="rounded-lg border border-rojo/50 bg-rojo/10 px-3 py-2 text-sm text-texto">
        Primer pedido registrado fuera de plazo: <strong className="font-mono">
          {r.pedidos.find(p => p.estado === 'RETRASADO')?.idPedido ?? 'No identificado en el detalle'}
        </strong>.
        {r.pedidos.find(p => p.estado === 'RETRASADO')?.fechaArribo &&
          <span> Arribo: {formatoFechaHora(r.pedidos.find(p => p.estado === 'RETRASADO')!.fechaArribo)}</span>}
      </div>}
      <div className="flex flex-wrap gap-2">
        <Boton variant="secundario" onClick={() => archivo(`corrida_${idSimulacion}.json`, JSON.stringify(r, null, 2), 'application/json;charset=utf-8')}>Exportar resultado JSON</Boton>
        {r.pedidos.length > 0 && <Boton variant="secundario" onClick={() => exportarCsv(`pedidos_${idSimulacion}.csv`, r.pedidos)}>Pedidos CSV</Boton>}
        {r.incidencias.length > 0 && <Boton variant="secundario" onClick={() => exportarCsv(`incidencias_${idSimulacion}.csv`, r.incidencias)}>Incidencias CSV</Boton>}
        {r.mantenimientos.length > 0 && <Boton variant="secundario" onClick={() => exportarCsv(`mantenimientos_${idSimulacion}.csv`, r.mantenimientos)}>Mantenimientos CSV</Boton>}
      </div>
      <div className="grid grid-cols-1 sm:grid-cols-3 gap-3 text-xs text-texto2">
        <span>Flota inicial: <b className="text-texto">{r.flotaInicial.length} vehículos</b></span>
        <span>Incidencias: <b className="text-texto">{r.incidencias.length}</b></span>
        <span>Mantenimientos: <b className="text-texto">{r.mantenimientos.length}</b></span>
      </div>
      <details className="border-t border-borde pt-2">
        <summary className="cursor-pointer text-sm text-mint">Pedidos generados ({r.pedidos.length})</summary>
        <div className="overflow-auto max-h-96 mt-2"><table className="w-full text-xs text-texto">
          <thead className="text-texto2"><tr><th className="p-2 text-left">Pedido</th><th className="p-2 text-left">Prioridad</th><th className="p-2 text-left">Registro</th><th className="p-2 text-left">Arribo</th><th className="p-2 text-left">Entrega</th><th className="p-2 text-left">Estado</th></tr></thead>
          <tbody>{r.pedidos.map(p => <tr key={p.idPedido} className="border-t border-borde/60"><td className="p-2 font-mono">{p.idPedido}</td><td className="p-2">{p.prioridad}</td><td className="p-2">{formatoFechaHora(p.fechaRegistro)}</td><td className="p-2">{formatoFechaHora(p.fechaArribo)}</td><td className="p-2">{formatoFechaHora(p.fechaEntrega)}</td><td className="p-2">{p.estado}</td></tr>)}</tbody>
        </table></div>
      </details>
      <details className="border-t border-borde pt-2">
        <summary className="cursor-pointer text-sm text-mint">Incidencias y averías ({r.incidencias.length})</summary>
        <ul className="text-xs text-texto2 mt-2 space-y-1">{r.incidencias.map(i => <li className="border-b border-borde/50 py-1" key={i.idIncidencia}>#{i.idIncidencia} · {i.tipo} · {formatoFechaHora(i.fechaOcurrencia)} {i.idVehiculo ? `· vehículo ${i.idVehiculo}` : ''} {i.tipoAveria ? `· ${i.tipoAveria}` : ''}</li>)}</ul>
      </details>
      <details className="border-t border-borde pt-2">
        <summary className="cursor-pointer text-sm text-mint">Mantenimientos ({r.mantenimientos.length})</summary>
        <ul className="text-xs text-texto2 mt-2 space-y-1">{r.mantenimientos.map(m => <li className="border-b border-borde/50 py-1" key={m.idMantenimiento}>#{m.idMantenimiento} · {m.idVehiculo} · {m.tipo} · {formatoFechaHora(m.inicio)} → {formatoFechaHora(m.fin)}</li>)}</ul>
      </details>
      <details className="border-t border-borde pt-2">
        <summary className="cursor-pointer text-sm text-mint">Configuración empleada ({r.flotaInicial.length} unidades, {r.almacenesIniciales.length} almacenes)</summary>
        <div className="text-xs text-texto2 mt-2 space-y-1">{r.almacenesIniciales.map(a => <p key={a.idAlmacen}>{a.nombre} ({a.tipo}) · ({a.x}, {a.y}) · capacidad {a.capacidadMaxima ?? 'ilimitada'}</p>)}
          {Array.from(new Map(r.flotaInicial.map(f => [f.tipo, r.flotaInicial.filter(v => v.tipo === f.tipo)])).entries()).map(([tipo, v]) => <p key={tipo}>{tipo}: {v.length} unidades · {v[0].capacidadPaquetes} paq · {v[0].velocidadKmh} km/h · S/ {v[0].costoPorKm}/km</p>)}
        </div>
      </details>
      <div className="border-t border-borde pt-4 space-y-2">
        <h3 className="text-sm font-semibold text-texto">Comparar con otra corrida</h3>
        <p className="text-xs text-texto2">Consulta cualquier corrida anterior por su identificador. El backend no expone aún un listado de corridas archivadas.</p>
        <form className="flex flex-wrap gap-2" onSubmit={e => { e.preventDefault(); const n = Number(compararId); if (Number.isSafeInteger(n) && n > 0 && n !== idSimulacion) setComparando(n); }}>
          <input type="number" min={1} step={1} className="bg-bg border border-borde rounded px-2 py-1.5 text-sm text-texto" aria-label="Identificador de corrida anterior" value={compararId} onChange={e => setCompararId(e.target.value)} placeholder="ID de corrida anterior" />
          <Boton type="submit" disabled={!Number.isSafeInteger(Number(compararId)) || Number(compararId) <= 0 || Number(compararId) === idSimulacion}>Comparar</Boton>
        </form>
        {comparando !== null && anterior.error && <ErrorCarga titulo={`No se encontró o no se pudo consultar la corrida #${comparando}`} error={anterior.error} reintentar={anterior.recargar} />}
        {comparando !== null && comparacion && <div className="overflow-x-auto"><table className="w-full text-xs">
          <thead><tr className="border-b border-borde text-texto2"><th className="p-2 text-left">Métrica</th><th className="p-2 text-right">Corrida #{comparando}</th><th className="p-2 text-right">Corrida #{idSimulacion}</th><th className="p-2 text-right">Diferencia</th></tr></thead>
          <tbody>{comparacion.map(c => <tr key={c.k} className="border-b border-borde/50 text-texto"><td className="p-2">{c.k}</td><td className="p-2 text-right font-mono">{c.unidad}{formatoDecimal(c.anterior)}</td><td className="p-2 text-right font-mono">{c.unidad}{formatoDecimal(c.ahora)}</td><td className="p-2 text-right font-mono">{c.unidad}{formatoDecimal(c.ahora - c.anterior)}</td></tr>)}</tbody>
        </table></div>}
      </div>
    </>}
  </section>;
}
