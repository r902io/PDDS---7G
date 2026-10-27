import type { ReactNode } from 'react';
import type { AlmacenOperativo, PedidoOperativo, VehiculoOperativo, VehiculoSnapshot } from '../../types/api';
import type { Seleccion } from '../../hooks/useMapCanvas';
import { IconoCerrar, IconoMapaSvg } from '../iconos';
import {
  formatoCoordenada,
  formatoDecimal,
  formatoEntero,
  formatoKm,
  formatoSoles,
  longitudCaminoKm,
  NO_DISPONIBLE,
} from '../../utilitarios/formato';
import {
  colorDeVehiculo,
  ETIQUETA_TIPO,
  estaAveriado,
  estaEnMantenimiento,
  etiquetaEstadoVehiculo,
  tipoDesdeCodigo,
  obtenerFormaAveria,
  obtenerEtiquetaAveria,
  obtenerColorAveriaHex,
} from '../../utilitarios/vehiculos';
import { pasosUnitarios } from './geometria';

interface Props {
  seleccion: Exclude<Seleccion, null>;
  vehiculos: VehiculoSnapshot[];
  pedidos: PedidoOperativo[];
  catalogo: VehiculoOperativo[] | null;
  almacenes: AlmacenOperativo[];
  alSeleccionar: (s: Seleccion) => void;
}

/** Panel lateral con el detalle de la unidad (o del nodo) seleccionado en el mapa. */
/** Panel lateral con el detalle de la unidad, almacén o nodo seleccionado en el mapa. */
export function PanelDetalle({ seleccion, vehiculos, pedidos, catalogo, almacenes, alSeleccionar }: Props) {
  const cerrar = () => alSeleccionar(null);

  return (
      <aside
          className="absolute top-0 right-0 bottom-0 z-20 w-[380px] max-w-[92%] bg-panel border-l border-mint shadow-2xl flex flex-col"
          aria-label="Detalle de la selección"
      >
        {seleccion.tipo === 'pedido' ? (
            <div className="px-4 py-3 overflow-y-auto">
              <button className="float-right text-texto2 underline text-xs" onClick={cerrar}>Cerrar</button>
              <h2 className="text-base font-semibold text-texto mb-3">Pedido #{seleccion.id}</h2>
              {(() => {
                const p = pedidos.find((p) => p.idPedido === seleccion.id);
                return p ? (
                    <>
                      <Dato k="Cliente" v={p.idCliente} />
                      <Dato k="Estado" v={p.estado} />
                      <Dato k="Paquetes" v={String(p.cantidadQq)} mono />
                      <Dato k="Prioridad" v={p.prioridad} />
                      <Dato k="Plazo" v={`${p.horasLimite} horas`} mono />
                      <Dato k="Ubicación" v={formatoCoordenada(p.ubicacionX, p.ubicacionY)} mono />
                      <Dato k="Registro" v={p.fechaLlegada} mono />
                      <Dato k="Entrega" v={p.fechaEntregaReal ?? 'Todavía no entregado'} />
                    </>
                ) : (
                    <p className="text-sm text-texto2">Este pedido no está en la página del mapa.</p>
                );
              })()}
            </div>
        ) : seleccion.tipo === 'vehiculo' ? (
            <DetalleVehiculo
                v={vehiculos.find((u) => u.idVehiculo === seleccion.id) ?? null}
                id={seleccion.id}
                catalogo={catalogo?.find((c) => c.idVehiculo === seleccion.id) ?? null}
                catalogoCargado={catalogo != null}
                cerrar={cerrar}
            />
        ) : seleccion.tipo === 'almacen' ? (
            (() => {
              const alm = almacenes.find((a) => a.idAlmacen === seleccion.id) ?? null;
              const x = alm?.ubicacionX ?? 0;
              const y = alm?.ubicacionY ?? 0;
              return (
                  <DetalleNodo
                      x={x}
                      y={y}
                      vehiculos={vehiculos.filter((v) => v.x === x && v.y === y)}
                      almacen={alm}
                      alSeleccionar={alSeleccionar}
                      cerrar={cerrar}
                  />
              );
            })()
        ) : (
            /* En esta rama, TypeScript reduce la unión exactamente a { tipo: 'nodo'; x: number; y: number } */
            <DetalleNodo
                x={seleccion.x}
                y={seleccion.y}
                vehiculos={vehiculos.filter((v) => v.x === seleccion.x && v.y === seleccion.y)}
                almacen={almacenes.find((a) => a.ubicacionX === seleccion.x && a.ubicacionY === seleccion.y) ?? null}
                alSeleccionar={alSeleccionar}
                cerrar={cerrar}
            />
        )}
      </aside>
  );
}

function Cabecera({ titulo, sub, icono, cerrar }: { titulo: string; sub?: string; icono?: ReactNode; cerrar: () => void }) {
  return (
      <div className="flex items-center gap-2.5 px-4 py-3 border-b border-borde">
        {icono}
        <div className="min-w-0">
          <h2 className="text-[15px] font-semibold text-texto truncate">{titulo}</h2>
          {sub && <p className="text-xs text-texto2">{sub}</p>}
        </div>
        <button
            onClick={cerrar}
            className="ml-auto text-texto2 hover:text-texto border border-borde rounded-md w-7 h-7 flex items-center justify-center"
            aria-label="Cancelar selección"
            title="Cancelar selección (Esc)"
        >
          <IconoCerrar tamano={16} />
        </button>
      </div>
  );
}

function Dato({ k, v, mono = false }: { k: string; v: ReactNode; mono?: boolean }) {
  return (
      <div className="flex gap-3 py-1 text-[13px] items-center">
        <span className="w-40 shrink-0 text-texto2">{k}</span>
        <span className={`text-texto ${mono ? 'font-mono' : ''} ${v === NO_DISPONIBLE ? 'text-texto2 italic' : ''}`}>{v}</span>
      </div>
  );
}

function DetalleVehiculo({
                           v,
                           id,
                           catalogo,
                           catalogoCargado,
                           cerrar,
                         }: {
  v: VehiculoSnapshot | null;
  id: string;
  catalogo: VehiculoOperativo | null;
  catalogoCargado: boolean;
  cerrar: () => void;
}) {
  const tipo = tipoDesdeCodigo(id);
  const forma = v && estaAveriado(v.estado)
      ? obtenerFormaAveria(v.tipoAveria)
      : v && estaEnMantenimiento(v.estado)
          ? 'mantenimiento'
          : tipo === 'MOTO'
              ? 'moto'
              : tipo === 'BICICLETA'
                  ? 'bicicleta'
                  : 'auto';

  if (!v) {
    return (
        <>
          <Cabecera titulo={id} cerrar={cerrar} />
          <p className="p-4 text-sm text-texto2">La unidad ya no figura en el estado más reciente de la simulación.</p>
        </>
    );
  }

  // Esquinas del camino: solo los puntos donde cambia la dirección.
  const pasos = pasosUnitarios([{ x: v.x, y: v.y }, ...v.caminoActual]);
  const esquinas = pasos.filter((p, i) => {
    if (i === 0 || i === pasos.length - 1) return true;
    const a = pasos[i - 1];
    const b = pasos[i + 1];
    return !((a.x === p.x && p.x === b.x) || (a.y === p.y && p.y === b.y));
  });
  const km = longitudCaminoKm(v, v.caminoActual);

  return (
      <>
        <Cabecera
            titulo={id}
            sub={tipo ? ETIQUETA_TIPO[tipo] : 'Tipo no deducible del código'}
            icono={<IconoMapaSvg forma={forma} color={colorDeVehiculo(id)} tamano={28} />}
            cerrar={cerrar}
        />
        <div className="flex-1 overflow-y-auto px-4 py-3">
          <h3 className="text-[11px] uppercase tracking-wider text-texto2 font-semibold mb-1">Estado actual</h3>
          <Dato k="Código" v={id} mono />
          <Dato k="Tipo" v={tipo ? ETIQUETA_TIPO[tipo] : NO_DISPONIBLE} />
          <Dato
              k="Estado"
              v={
                estaAveriado(v.estado) ? (
                    <span
                        className="inline-flex items-center gap-1.5 px-2 py-0.5 rounded text-xs font-semibold"
                        style={{
                          backgroundColor: `${obtenerColorAveriaHex(v.tipoAveria)}20`,
                          color: obtenerColorAveriaHex(v.tipoAveria),
                          border: `1px solid ${obtenerColorAveriaHex(v.tipoAveria)}40`,
                        }}
                    >
                <span
                    className="w-2 h-2 rounded-full animate-pulse"
                    style={{ backgroundColor: obtenerColorAveriaHex(v.tipoAveria) }}
                />
                Averiado: {obtenerEtiquetaAveria(v.tipoAveria)}
              </span>
                ) : (
                    etiquetaEstadoVehiculo(v.estado)
                )
              }
          />
          <Dato k="Pedido objetivo" v={v.pedidoObjetivo != null ? String(v.pedidoObjetivo) : 'Sin pedido asignado'} mono={v.pedidoObjetivo != null} />
          <Dato k="Coordenadas" v={formatoCoordenada(v.x, v.y)} mono />
          <Dato k="Camino pendiente" v={formatoKm(km)} mono />
          <Dato k="Tramo ya recorrido" v={NO_DISPONIBLE} />
          <Dato k="Carga actual" v={`${formatoEntero(v.cargaActualPaquetes ?? 0)} / ${formatoEntero(v.capacidadPaquetes || catalogo?.capacidadPaquetes || 0)} paquetes`} mono />

          <div className="mt-1 mb-2 h-2 bg-bg border border-borde rounded-full overflow-hidden" aria-label="Porcentaje de ocupación del vehículo">
            {(() => {
              const stock = v.cargaActualPaquetes ?? 0;
              const max = v.capacidadPaquetes || catalogo?.capacidadPaquetes || 1;
              const pct = stock / max;

              let colorBg = 'bg-gray-400';
              if (stock > 0) {
                if (pct >= 0.8) colorBg = 'bg-red-500';
                else if (pct >= 0.5) colorBg = 'bg-amber-500';
                else colorBg = 'bg-green-500';
              }

              return (
                  <div
                      className={`h-full rounded-full ${colorBg}`}
                      style={{ width: `${Math.min(100, (100 * stock) / max)}%` }}
                  />
              );
            })()}
          </div>

          <Dato k="Pedidos a bordo" v={formatoEntero(v.cargaActualPaquetes > 0 ? v.entregasPendientes : 0)} mono />
          {v.cargaPorRecogerPaquetes > 0 && <Dato k="Pendiente de recoger" v={`${formatoEntero(v.cargaPorRecogerPaquetes)} paquetes`} mono />}

          <h3 className="text-[11px] uppercase tracking-wider text-texto2 font-semibold mt-4 mb-1">Ficha de la unidad</h3>
          {!catalogoCargado && <p className="text-xs text-texto2">Cargando datos del vehículo…</p>}
          <Dato k="Capacidad" v={catalogo ? `${formatoEntero(catalogo.capacidadPaquetes)} paquetes` : NO_DISPONIBLE} mono={!!catalogo} />
          <Dato k="Velocidad" v={catalogo ? `${formatoDecimal(catalogo.velocidadKmh)} km/h` : NO_DISPONIBLE} mono={!!catalogo} />
          <Dato k="Tarifa" v={catalogo ? `${formatoSoles(catalogo.costoPorKm)} por km` : NO_DISPONIBLE} mono={!!catalogo} />
          <Dato k="Conductor" v={catalogo?.idConductorActual ?? NO_DISPONIBLE} mono={!!catalogo?.idConductorActual} />

          <h3 className="text-[11px] uppercase tracking-wider text-texto2 font-semibold mt-4 mb-1">
            Camino completo pendiente
          </h3>
          {v.caminoActual.length === 0 ? (
              <p className="text-sm text-texto2">La unidad no tiene un camino asignado en este momento.</p>
          ) : (
              <>
                <p className="text-xs text-texto2 mb-1.5">
                  <span className="font-mono text-texto">{v.caminoActual.length}</span> nodos ·{' '}
                  <span className="font-mono text-texto">{formatoKm(km)}</span>. Se listan los puntos de giro.
                </p>
                <ol className="font-mono text-xs text-texto leading-relaxed bg-bg border border-borde rounded-md p-2 max-h-64 overflow-y-auto">
                  {esquinas.map((p, i) => (
                      <li key={i} className="flex gap-2">
                        <span className="text-texto2 w-6 text-right">{i + 1}.</span>
                        <span>{formatoCoordenada(p.x, p.y)}</span>
                        {i === 0 && <span className="text-texto2 font-sans">posición actual</span>}
                        {i === esquinas.length - 1 && <span className="text-texto2 font-sans">destino inmediato</span>}
                      </li>
                  ))}
                </ol>
              </>
          )}
        </div>
      </>
  );
}

function DetalleNodo({
                       x,
                       y,
                       vehiculos,
                       almacen,
                       alSeleccionar,
                       cerrar,
                     }: {
  x: number;
  y: number;
  vehiculos: VehiculoSnapshot[];
  almacen: AlmacenOperativo | null;
  alSeleccionar: (s: Seleccion) => void;
  cerrar: () => void;
}) {
  const ordenados = [...vehiculos].sort((a, b) => {
    const stockA = a.cargaActualPaquetes ?? 0;
    const maxA = a.capacidadPaquetes || (a.idVehiculo.startsWith('TA') ? 24 : a.idVehiculo.startsWith('TM') ? 8 : 4);
    const pctA = stockA / maxA;

    const stockB = b.cargaActualPaquetes ?? 0;
    const maxB = b.capacidadPaquetes || (b.idVehiculo.startsWith('TA') ? 24 : b.idVehiculo.startsWith('TM') ? 8 : 4);
    const pctB = stockB / maxB;

    return pctB - pctA;
  });

  return (
      <>
        <Cabecera
            titulo={almacen ? `Almacén ${almacen.nombre}` : 'Unidades en el nodo'}
            sub={`Nodo ${formatoCoordenada(x, y)} · ${ordenados.length} unidades ${almacen ? 'dentro' : ''}`}
            icono={almacen ? <IconoMapaSvg forma={almacen.tipo === 'CENTRAL' ? 'almacenCentral' : 'almacenIntermedio'} tamano={28} /> : undefined}
            cerrar={cerrar}
        />
        <div className="flex-1 overflow-y-auto px-4 py-3">
          {almacen && (
              <div className="mb-3">
                <Dato k="Stock" v={almacen.stockActual == null ? 'Ilimitado' : formatoEntero(almacen.stockActual)} mono />
                <Dato k="Capacidad" v={almacen.capacidadMaxima == null ? 'Ilimitada' : formatoEntero(almacen.capacidadMaxima)} mono />
              </div>
          )}
          <p className="text-xs text-texto2 mb-2">Elija una unidad para ver su detalle y resaltar su ruta.</p>
          <ul className="grid grid-cols-2 gap-1.5">
            {ordenados.map((v) => {
              const tipo = tipoDesdeCodigo(v.idVehiculo);
              const formaNodo = estaAveriado(v.estado)
                  ? obtenerFormaAveria(v.tipoAveria)
                  : estaEnMantenimiento(v.estado)
                      ? 'mantenimiento'
                      : tipo === 'MOTO'
                          ? 'moto'
                          : tipo === 'BICICLETA'
                              ? 'bicicleta'
                              : 'auto';

              return (
                  <li key={v.idVehiculo}>
                    <button
                        onClick={() => alSeleccionar({ tipo: 'vehiculo', id: v.idVehiculo })}
                        className="w-full flex items-center gap-2 bg-bg border border-borde rounded-md px-2 py-1.5 hover:border-azul text-left"
                    >
                      <IconoMapaSvg
                          forma={formaNodo}
                          color={colorDeVehiculo(v.idVehiculo)}
                          tamano={20}
                      />
                      <div className="flex flex-col flex-1 min-w-0 text-left">
                        <span className="font-mono text-xs text-texto">{v.idVehiculo}</span>
                        <span className="text-[11px] text-texto2 truncate">{etiquetaEstadoVehiculo(v.estado)}</span>
                      </div>
                      {(() => {
                        const stock = v.cargaActualPaquetes ?? 0;
                        const max = v.capacidadPaquetes || (tipo === 'AUTO' ? 24 : tipo === 'MOTO' ? 8 : tipo === 'BICICLETA' ? 4 : 1);
                        const pct = stock / max;

                        let colorTexto = 'text-gray-400';
                        let textoStock = 'VACÍO';
                        if (stock > 0) {
                          textoStock = `${stock} paq.`;
                          if (pct >= 0.8) colorTexto = 'text-red-500';
                          else if (pct >= 0.5) colorTexto = 'text-amber-500';
                          else colorTexto = 'text-green-500';
                        }

                        return (
                            <span className={`text-[11px] font-bold ${colorTexto} whitespace-nowrap`}>
                        {textoStock}
                      </span>
                        );
                      })()}
                    </button>
                  </li>
              );
            })}
          </ul>
        </div>
      </>
  );
}