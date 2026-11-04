import { memo, useMemo } from 'react';
import type { VehiculoSnapshot } from '../../types/api';
import { usePreferencia } from '../../hooks/usePreferencia';
import { formatoDecimal, formatoEntero, formatoHora, partesFechaHora } from '../../utilitarios/formato';
import {
  CAMPOS_ORDEN_UNIDADES,
  colorDeVehiculo,
  etiquetaEstadoVehiculo,
  ordenarUnidades,
  type CampoOrdenUnidad,
  type SentidoOrden,
} from '../../utilitarios/vehiculos';

interface Props {
  vehiculos: VehiculoSnapshot[];
  /** Fecha del reloj simulado, para mostrar la fecha solo cuando es otra. */
  fechaReloj: string | null;
  idSeleccionado: string | null;
  alSeleccionar: (id: string) => void;
}

interface Orden {
  campo: CampoOrdenUnidad;
  sentido: SentidoOrden;
}

const ORDEN_INICIAL: Orden = { campo: 'horaLlegadaEstimada', sentido: 'asc' };

const COLUMNAS: Array<{ campo: CampoOrdenUnidad; etiqueta: string; ayuda: string }> = [
  { campo: 'horaSalida', etiqueta: 'Salida', ayuda: 'Hora de salida del último almacén' },
  { campo: 'horaLlegadaEstimada', etiqueta: 'Llegada', ayuda: 'Hora de llegada a su destino inmediato, cliente o almacén' },
  { campo: 'distanciaPorRecorrerKm', etiqueta: 'Por rec.', ayuda: 'Kilómetros que faltan para llegar a su destino' },
  { campo: 'distanciaRecorridaKm', etiqueta: 'Recorr.', ayuda: 'Kilómetros recorridos desde su salida' },
];

function ordenValido(valor: unknown): boolean {
  const o = valor as Partial<Orden> | null;
  return !!o && CAMPOS_ORDEN_UNIDADES.includes(o.campo as CampoOrdenUnidad) && (o.sentido === 'asc' || o.sentido === 'desc');
}

/** E14 a E17: unidades con sus horas y distancias, ordenables por cada columna. */
export const ListaVehiculos = memo(function ListaVehiculos({ vehiculos, fechaReloj, idSeleccionado, alSeleccionar }: Props) {
  const [orden, setOrden] = usePreferencia<Orden>('sisrap.lista-unidades.orden', ORDEN_INICIAL, ordenValido);
  const filas = useMemo(
    () => ordenarUnidades(vehiculos, orden.campo, orden.sentido),
    [vehiculos, orden.campo, orden.sentido]
  );

  const alOrdenar = (campo: CampoOrdenUnidad) => {
    const invertir = orden.campo === campo && orden.sentido === 'asc';
    setOrden({ campo, sentido: invertir ? 'desc' : 'asc' });
  };

  return (
    <div>
      <div className="max-h-72 overflow-y-auto border border-borde rounded-md">
        <table className="w-full table-fixed text-[11px] border-collapse">
          <thead>
            <tr>
              <th className="sticky top-0 z-10 w-[19%] bg-panel text-left font-semibold text-texto2 px-1 py-1 border-b border-borde">UT</th>
              {COLUMNAS.map((c) => {
                const activa = orden.campo === c.campo;
                return (
                  <th
                    key={c.campo}
                    aria-sort={activa ? (orden.sentido === 'asc' ? 'ascending' : 'descending') : 'none'}
                    className="sticky top-0 z-10 bg-panel text-right text-[10px] font-semibold px-0.5 py-1 border-b border-borde"
                  >
                    <button
                      type="button"
                      onClick={() => alOrdenar(c.campo)}
                      title={`${c.ayuda}. Clic para ordenar.`}
                      className={`whitespace-nowrap hover:text-texto ${activa ? 'text-mint' : 'text-texto2'}`}
                    >
                      {c.etiqueta}
                      {activa && <span aria-hidden className="text-[8px]"> {orden.sentido === 'asc' ? '▲' : '▼'}</span>}
                    </button>
                  </th>
                );
              })}
            </tr>
          </thead>
          <tbody>
            {filas.map((v) => (
              <tr
                key={v.idVehiculo}
                onClick={() => alSeleccionar(v.idVehiculo)}
                title={`${v.idVehiculo}, ${etiquetaEstadoVehiculo(v.estado)}. Clic para ubicarla en el mapa.`}
                className={`cursor-pointer border-b border-borde/50 last:border-b-0 hover:bg-panel ${
                  idSeleccionado === v.idVehiculo ? 'bg-panel outline outline-1 -outline-offset-1 outline-mint' : ''
                }`}
              >
                <td className="px-1 py-1 align-top">
                  <button
                    type="button"
                    onClick={(e) => { e.stopPropagation(); alSeleccionar(v.idVehiculo); }}
                    aria-label={`Seleccionar ${v.idVehiculo} en el mapa`}
                    className="font-mono font-semibold"
                    style={{ color: colorDeVehiculo(v.idVehiculo) }}
                  >
                    {v.idVehiculo}
                  </button>
                  <div className="font-mono text-[10px] text-texto2 whitespace-nowrap" title="Paquetes a bordo y capacidad">
                    {formatoEntero(v.cargaActualPaquetes ?? 0)}/{formatoEntero(v.capacidadPaquetes ?? 0)}
                  </div>
                </td>
                <CeldaHora iso={v.horaSalida} fechaReloj={fechaReloj} />
                <CeldaHora iso={v.horaLlegadaEstimada} fechaReloj={fechaReloj} />
                <CeldaKm km={v.distanciaPorRecorrerKm} />
                <CeldaKm km={v.distanciaRecorridaKm} />
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      <p className="mt-1.5 text-[10px] text-texto2 leading-snug">
        Llegada y por recorrer se miden hasta el destino inmediato. Las unidades sin dato van al final.
      </p>
    </div>
  );
});

const SinDato = () => <span className="text-[10px] text-texto2 italic">Sin dato</span>;

function CeldaHora({ iso, fechaReloj }: { iso?: string | null; fechaReloj: string | null }) {
  const p = partesFechaHora(iso);
  return (
    <td className="px-0.5 py-1 text-right align-top whitespace-nowrap">
      {p ? (
        <>
          <div className="font-mono text-texto">{formatoHora(iso)}</div>
          {iso?.slice(0, 10) !== fechaReloj && (
            <div className="font-mono text-[10px] text-texto2">
              {String(p.dia).padStart(2, '0')}/{String(p.mes).padStart(2, '0')}
            </div>
          )}
        </>
      ) : (
        <SinDato />
      )}
    </td>
  );
}

function CeldaKm({ km }: { km?: number | null }) {
  return (
    <td className="px-0.5 py-1 text-right align-top whitespace-nowrap">
      {km == null ? <SinDato /> : <span className="font-mono text-texto">{formatoDecimal(km)}</span>}
    </td>
  );
}
