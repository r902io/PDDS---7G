import { useState } from 'react';
import type { AlmacenOperativo, BloqueoRespuesta, VehiculoSnapshot } from '../../types/api';
import { useMapCanvas, type Dimensiones, type Objetivo, type Seleccion } from '../../hooks/useMapCanvas';
import { IconoEncuadrar, IconoMapaSvg } from '../iconos';
import {
  formatoCoordenada,
  formatoEntero,
  formatoKm,
  longitudCaminoKm,
  NO_DISPONIBLE,
} from '../../utilitarios/formato';
import {
  COLOR_TIPO,
  ETIQUETA_TIPO,
  etiquetaEstadoVehiculo,
  tipoDesdeCodigo,
} from '../../utilitarios/vehiculos';

interface Props {
  dimensiones: Dimensiones;
  almacenes: AlmacenOperativo[];
  vehiculos: VehiculoSnapshot[];
  bloqueos: BloqueoRespuesta[];
  seleccion: Seleccion;
  alSeleccionar: (s: Seleccion) => void;
  atenuado: boolean;
}

/** Longitudes "redondas" para la escala gráfica. */
const LARGOS_ESCALA = [1, 2, 5, 10, 20];

export function MapaCiudad(props: Props) {
  const { canvasRef, hover, cursorKm, escalaPx, zoom, encuadrar, manejadores } = useMapCanvas(props);
  const [leyendaAbierta, setLeyendaAbierta] = useState(true);

  // Escala gráfica: el mayor largo redondo que quepa en ~120 px.
  const kmEscala = [...LARGOS_ESCALA].reverse().find((k) => k * escalaPx <= 130) ?? 1;

  return (
    <div className="relative w-full h-full overflow-hidden bg-bg">
      <canvas
        ref={canvasRef}
        className="block touch-none cursor-crosshair"
        role="img"
        aria-label={`Mapa de la ciudad de ${props.dimensiones.anchoKm} por ${props.dimensiones.altoKm} km con ${props.vehiculos.length} unidades`}
        {...manejadores}
      />

      {/* Leyenda */}
      <div className="absolute left-2.5 top-2.5 z-10 bg-panel/95 border border-borde rounded-lg px-3 py-2 text-xs max-w-[340px]">
        <div className="flex items-center gap-2">
          <span className="text-[11px] uppercase tracking-wider text-texto2 font-semibold">Leyenda</span>
          <button
            className="ml-auto text-[11px] text-texto2 border border-borde rounded px-1.5 py-0.5 hover:text-texto"
            onClick={() => setLeyendaAbierta((v) => !v)}
            aria-expanded={leyendaAbierta}
          >
            {leyendaAbierta ? 'Ocultar' : 'Mostrar'}
          </button>
        </div>
        {leyendaAbierta && (
          <>
            <div className="grid grid-cols-2 gap-x-4 gap-y-1 mt-1.5">
              <ItemLeyenda forma="auto" color={COLOR_TIPO.AUTO} texto="Auto (TA)" />
              <ItemLeyenda forma="almacenCentral" texto="Almacén central" />
              <ItemLeyenda forma="moto" color={COLOR_TIPO.MOTO} texto="Moto (TM)" />
              <ItemLeyenda forma="almacenIntermedio" texto="Almacén intermedio" />
              <ItemLeyenda forma="bicicleta" color={COLOR_TIPO.BICICLETA} texto="Bicicleta (TB)" />
              <ItemLeyenda forma="destinoCliente" color={COLOR_TIPO.AUTO} texto="Destino: pedido" />
              <ItemLeyenda forma="averia" texto="Unidad averiada" />
              <ItemLeyenda forma="bloqueo" texto="Tramo bloqueado" />
              <ItemLeyenda forma="mantenimiento" texto="En mantenimiento" />
              <div className="flex items-center gap-1.5 text-texto2 whitespace-nowrap">
                <span className="w-6 h-5 flex items-center justify-center">
                  <span className="w-[18px] h-[18px] rounded-full border border-texto bg-panel2 text-[10px] font-mono text-texto flex items-center justify-center">
                    3
                  </span>
                </span>
                Unidades en un nodo
              </div>
            </div>
            <p className="mt-1.5 pt-1.5 border-t border-borde text-[11px] text-texto2 leading-snug">
              La cifra sobre un almacén indica cuántas unidades están <b className="text-texto">dentro</b> de él.
              Pase el cursor sobre una unidad o su ruta para resaltarla; haga clic para fijarla.
            </p>
          </>
        )}
      </div>

      {/* Escala gráfica y encuadre */}
      <div className="absolute left-2.5 bottom-2.5 z-10 flex items-end gap-2">
        <div className="bg-panel/95 border border-borde rounded px-2 py-1">
          <div className="h-2 border-x-2 border-b-2 border-texto2" style={{ width: kmEscala * escalaPx }} />
          <div className="font-mono text-[11px] text-texto mt-0.5">{kmEscala} km</div>
        </div>
        <button
          onClick={encuadrar}
          disabled={zoom === 1}
          title={zoom === 1 ? 'El mapa ya muestra la ciudad completa' : 'Volver a mostrar la ciudad completa'}
          className="bg-panel/95 border border-borde rounded px-2 py-1.5 text-xs text-texto flex items-center gap-1.5 disabled:text-texto2 disabled:cursor-not-allowed"
        >
          <IconoEncuadrar tamano={14} /> Ciudad completa
        </button>
      </div>

      {/* Coordenadas del cursor */}
      <div className="absolute right-2.5 bottom-2.5 z-10 bg-panel/95 border border-borde rounded px-2.5 py-1 font-mono text-xs text-texto">
        {cursorKm ? `x ${cursorKm.x} · y ${cursorKm.y} km` : 'x — · y —'}
      </div>

      {hover && (
        <Tooltip
          objetivo={hover.objetivo}
          x={hover.px}
          y={hover.py}
          vehiculos={props.vehiculos}
          almacenes={props.almacenes}
        />
      )}
    </div>
  );
}

function ItemLeyenda({ forma, color, texto }: { forma: Parameters<typeof IconoMapaSvg>[0]['forma']; color?: string; texto: string }) {
  return (
    <div className="flex items-center gap-1.5 text-texto2 whitespace-nowrap">
      <IconoMapaSvg forma={forma} color={color} tamano={22} />
      {texto}
    </div>
  );
}

function Tooltip({
  objetivo,
  x,
  y,
  vehiculos,
  almacenes,
}: {
  objetivo: Objetivo;
  x: number;
  y: number;
  vehiculos: VehiculoSnapshot[];
  almacenes: AlmacenOperativo[];
}) {
  const estilo = { left: x + 14, top: y + 14 };
  const clase =
    'absolute z-30 pointer-events-none max-w-[300px] bg-[#0d1729] border border-mint rounded-lg px-3 py-2 text-xs shadow-2xl';

  if (objetivo.tipo === 'vehiculo') {
    const v = vehiculos.find((u) => u.idVehiculo === objetivo.id);
    if (!v) return null;
    const tipo = tipoDesdeCodigo(v.idVehiculo);
    return (
      <div className={clase} style={estilo}>
        <div className="font-mono font-semibold text-sm text-texto mb-1">{v.idVehiculo}</div>
        <Tabla
          filas={[
            ['Tipo', tipo ? ETIQUETA_TIPO[tipo] : NO_DISPONIBLE],
            ['Estado', etiquetaEstadoVehiculo(v.estado)],
            ['Pedido objetivo', v.pedidoObjetivo != null ? String(v.pedidoObjetivo) : 'Sin pedido asignado'],
            ['Coordenadas', formatoCoordenada(v.x, v.y)],
            ['Camino pendiente', formatoKm(longitudCaminoKm(v, v.caminoActual))],
          ]}
          mono={[false, false, v.pedidoObjetivo != null, true, true]}
        />
      </div>
    );
  }

  if (objetivo.tipo === 'almacen') {
    const a = almacenes.find((al) => al.idAlmacen === objetivo.id);
    if (!a) return null;
    return (
      <div className={clase} style={estilo}>
        <div className="font-semibold text-sm text-texto mb-1">Almacén {a.nombre}</div>
        <Tabla
          filas={[
            ['Coordenadas', formatoCoordenada(a.ubicacionX, a.ubicacionY)],
            ['Stock', a.stockActual == null ? 'Ilimitado' : formatoEntero(a.stockActual)],
            ['Unidades dentro', '0'],
          ]}
          mono={[true, true, true]}
        />
      </div>
    );
  }

  const almacen = objetivo.idAlmacen ? almacenes.find((a) => a.idAlmacen === objetivo.idAlmacen) : null;
  const porTipo = new Map<string, number>();
  for (const id of objetivo.ids) {
    const t = tipoDesdeCodigo(id);
    const k = t ? ETIQUETA_TIPO[t] : 'Otro';
    porTipo.set(k, (porTipo.get(k) ?? 0) + 1);
  }
  return (
    <div className={clase} style={estilo}>
      <div className="font-semibold text-sm text-texto mb-1">
        {almacen ? `Almacén ${almacen.nombre}` : 'Nodo con varias unidades'}{' '}
        <span className="font-mono text-texto2">{formatoCoordenada(objetivo.x, objetivo.y)}</span>
      </div>
      <p className="text-texto2 mb-1">
        <span className="font-mono text-texto">{objetivo.ids.length}</span> unidades{' '}
        {almacen ? 'dentro del almacén' : 'en este nodo'}:{' '}
        {[...porTipo].map(([k, n]) => `${n} ${k.toLowerCase()}`).join(', ')}.
      </p>
      {almacen && (
        <p className="text-texto2 mb-1">
          Stock: <span className="font-mono text-texto">{almacen.stockActual == null ? 'ilimitado' : formatoEntero(almacen.stockActual)}</span>
        </p>
      )}
      <p className="font-mono text-texto leading-relaxed break-words">{objetivo.ids.join(' ')}</p>
      <p className="text-texto2 mt-1">Haga clic para ver el detalle de cada unidad.</p>
    </div>
  );
}

function Tabla({ filas, mono }: { filas: Array<[string, string]>; mono: boolean[] }) {
  return (
    <table className="border-collapse">
      <tbody>
        {filas.map(([k, v], i) => (
          <tr key={k}>
            <td className="text-texto2 pr-3 py-0.5 align-top whitespace-nowrap">{k}</td>
            <td className={`py-0.5 text-texto ${mono[i] ? 'font-mono' : ''}`}>{v}</td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}
