import { memo, useMemo, useState } from 'react';
import type { VehiculoSnapshot } from '../../types/api';
import type { SemaforoFiltro } from '../../types/api';
import { usePreferencia } from '../../hooks/usePreferencia';
import { formatoDecimal, formatoEntero, formatoHora, partesFechaHora } from '../../utilitarios/formato';
import {
  CAMPOS_ORDEN_UNIDADES,
  colorDeVehiculo,
  etiquetaEstadoVehiculo,
  ordenarUnidades,
  ocupacionVehiculo,
  semaforoVehiculo,
  filtrarUnidades,
  valorBusqueda,
  RANGOS_DISTANCIA_KM,
  FILTROS_LISTA_UT_INICIAL,
  ORDEN_TIPOS,
  ETIQUETA_TIPO,
  COLOR_TIPO,
  type CampoOrdenUnidad,
  type SentidoOrden,
  type FiltrosListaUT,
  type ReferenciaDistancia,
  type CriterioBusqueda,
  type RangoDistanciaId,
  type TipoVehiculo,
} from '../../utilitarios/vehiculos';

interface Props {
  vehiculos: VehiculoSnapshot[];
  /** Fecha del reloj simulado (AAAA-MM-DD), para mostrar la fecha solo cuando es otra. */
  fechaReloj: string | null;
  /** Reloj simulado completo, necesario para la búsqueda por minutos (E06/E07). */
  reloj: string | null;
  idSeleccionado: string | null;
  alSeleccionar: (id: string) => void;
}

interface Orden {
  campo: CampoOrdenUnidad;
  sentido: SentidoOrden;
}

interface Busqueda {
  criterio: CriterioBusqueda;
  referencia: ReferenciaDistancia;
  min: string;
  max: string;
}

const ORDEN_INICIAL: Orden = { campo: 'horaLlegadaEstimada', sentido: 'asc' };
const BUSQUEDA_INICIAL: Busqueda = { criterio: 'MINUTOS', referencia: 'DESTINO', min: '', max: '' };

const COLUMNAS: Array<{ campo: CampoOrdenUnidad; etiqueta: string; ayuda: string }> = [
  { campo: 'ocupacion', etiqueta: 'Ocup.', ayuda: 'Porcentaje de ocupación (carga sobre capacidad)' },
  { campo: 'horaSalida', etiqueta: 'Salida', ayuda: 'Hora de salida del último almacén' },
  { campo: 'horaLlegadaEstimada', etiqueta: 'Llegada', ayuda: 'Hora de llegada a su destino inmediato, cliente o almacén' },
  { campo: 'distanciaPorRecorrerKm', etiqueta: 'Por rec.', ayuda: 'Kilómetros que faltan para llegar a su destino' },
  { campo: 'distanciaRecorridaKm', etiqueta: 'Recorr.', ayuda: 'Kilómetros recorridos desde su salida' },
];

const SEMAFOROS: Array<{ id: SemaforoFiltro; etiqueta: string; color: string }> = [
  { id: 'VACIO', etiqueta: 'Vacío', color: '#9ca3af' },
  { id: 'VERDE', etiqueta: 'Baja', color: '#22c55e' },
  { id: 'AMARILLO', etiqueta: 'Media', color: '#f59e0b' },
  { id: 'ROJO', etiqueta: 'Alta', color: '#ef4444' },
];

function ordenValido(valor: unknown): boolean {
  const o = valor as Partial<Orden> | null;
  return !!o && CAMPOS_ORDEN_UNIDADES.includes(o.campo as CampoOrdenUnidad) && (o.sentido === 'asc' || o.sentido === 'desc');
}

function filtrosValidos(valor: unknown): boolean {
  const f = valor as Partial<FiltrosListaUT> | null;
  return !!f && Array.isArray(f.tipos) && Array.isArray(f.rangosKm) && Array.isArray(f.semaforos)
    && (f.referenciaKm === 'DESTINO' || f.referenciaKm === 'ORIGEN');
}

/**
 * Lista de UT del panel.
 * E06-E09: búsqueda por distancia (minutos o km) al destino o al origen; resalta coincidencias.
 * E10/E11/E12: filtros permanentes (tipo, rango de km, semáforo) que se combinan y operan sobre el resultado.
 * E13-E17: orden por ocupación, hora de salida/llegada y distancias, ascendente o descendente.
 */
export const ListaVehiculos = memo(function ListaVehiculos({ vehiculos, fechaReloj, reloj, idSeleccionado, alSeleccionar }: Props) {
  const [orden, setOrden] = usePreferencia<Orden>('sisrap.lista-unidades.orden', ORDEN_INICIAL, ordenValido);
  const [filtros, setFiltros] = usePreferencia<FiltrosListaUT>('sisrap.lista-unidades.filtros', FILTROS_LISTA_UT_INICIAL, filtrosValidos);
  const [busqueda, setBusqueda] = useState<Busqueda>(BUSQUEDA_INICIAL);
  const [mostrarFiltros, setMostrarFiltros] = useState(false);

  // E10/E11/E12 (filtro permanente) seguido de E13-E17 (orden). La búsqueda no reduce la lista, solo resalta.
  const filas = useMemo(
    () => ordenarUnidades(filtrarUnidades(vehiculos, filtros), orden.campo, orden.sentido),
    [vehiculos, filtros, orden.campo, orden.sentido]
  );

  const min = busqueda.min.trim() === '' ? null : Number(busqueda.min);
  const max = busqueda.max.trim() === '' ? null : Number(busqueda.max);
  const busquedaActiva = min != null || max != null;

  // E06-E09: una UT coincide si su valor cae en [min, max] (cualquiera de los dos es opcional).
  const coincide = useMemo(() => {
    const set = new Set<string>();
    if (!busquedaActiva) return set;
    for (const v of filas) {
      const val = valorBusqueda(v, busqueda.criterio, busqueda.referencia, reloj);
      if (val == null) continue;
      if (min != null && val < min) continue;
      if (max != null && val > max) continue;
      set.add(v.idVehiculo);
    }
    return set;
  }, [filas, busqueda.criterio, busqueda.referencia, reloj, min, max, busquedaActiva]);

  const alOrdenar = (campo: CampoOrdenUnidad) => {
    const invertir = orden.campo === campo && orden.sentido === 'asc';
    setOrden({ campo, sentido: invertir ? 'desc' : 'asc' });
  };

  const alternarTipo = (tipo: TipoVehiculo) => {
    setFiltros({
      ...filtros,
      tipos: filtros.tipos.includes(tipo) ? filtros.tipos.filter((t) => t !== tipo) : [...filtros.tipos, tipo],
    });
  };
  const alternarSemaforo = (s: SemaforoFiltro) => {
    setFiltros({
      ...filtros,
      semaforos: filtros.semaforos.includes(s) ? filtros.semaforos.filter((x) => x !== s) : [...filtros.semaforos, s],
    });
  };
  const alternarRango = (id: RangoDistanciaId) => {
    setFiltros({
      ...filtros,
      rangosKm: filtros.rangosKm.includes(id) ? filtros.rangosKm.filter((x) => x !== id) : [...filtros.rangosKm, id],
    });
  };

  const unidadMin = busqueda.criterio === 'MINUTOS' ? 'min' : 'km';

  return (
    <div className="space-y-2">
      {/* E06-E09: BÚSQUEDA (momentánea, resalta coincidencias) */}
      <div className="bg-panel2/70 border border-borde rounded-md p-2 space-y-1.5">
        <span className="text-[10px] font-bold text-texto2 block">BUSCAR POR DISTANCIA (E06–E09)</span>
        <div className="flex flex-wrap gap-1">
          <select
            aria-label="Criterio de búsqueda"
            className="flex-1 min-w-[72px] bg-panel border border-borde rounded px-1 py-1 text-[11px] text-texto"
            value={busqueda.criterio}
            onChange={(e) => setBusqueda({ ...busqueda, criterio: e.target.value as CriterioBusqueda })}
          >
            <option value="MINUTOS">Minutos</option>
            <option value="KM">Km</option>
          </select>
          <select
            aria-label="Referencia de la distancia"
            className="flex-1 min-w-[90px] bg-panel border border-borde rounded px-1 py-1 text-[11px] text-texto"
            value={busqueda.referencia}
            onChange={(e) => setBusqueda({ ...busqueda, referencia: e.target.value as ReferenciaDistancia })}
          >
            <option value="DESTINO">Al destino (llegada)</option>
            <option value="ORIGEN">Al origen (salida)</option>
          </select>
        </div>
        <div className="flex items-center gap-1 text-[11px]">
          <input
            type="number"
            inputMode="numeric"
            aria-label={`Mínimo en ${unidadMin}`}
            placeholder={`mín ${unidadMin}`}
            className="w-full bg-panel border border-borde rounded px-1 py-1 text-texto"
            value={busqueda.min}
            onChange={(e) => setBusqueda({ ...busqueda, min: e.target.value })}
          />
          <span className="text-texto2">–</span>
          <input
            type="number"
            inputMode="numeric"
            aria-label={`Máximo en ${unidadMin}`}
            placeholder={`máx ${unidadMin}`}
            className="w-full bg-panel border border-borde rounded px-1 py-1 text-texto"
            value={busqueda.max}
            onChange={(e) => setBusqueda({ ...busqueda, max: e.target.value })}
          />
          {busquedaActiva && (
            <button
              type="button"
              onClick={() => setBusqueda({ ...busqueda, min: '', max: '' })}
              className="shrink-0 border border-borde rounded px-2 py-1 text-texto2 hover:text-texto"
              title="Limpiar la búsqueda"
            >
              ✕
            </button>
          )}
        </div>
        {busquedaActiva && (
          <p className="text-[10px] text-texto2">
            {coincide.size} UT resaltada{coincide.size === 1 ? '' : 's'} de {filas.length}.
          </p>
        )}
      </div>

      {/* E10/E11/E12: FILTROS PERMANENTES (operan sobre el resultado) */}
      <div className="bg-panel2/70 border border-borde rounded-md">
        <button
          type="button"
          onClick={() => setMostrarFiltros((v) => !v)}
          aria-expanded={mostrarFiltros}
          className="w-full flex items-center justify-between px-2 py-1.5 text-[10px] font-bold text-texto2"
        >
          <span>FILTROS PERMANENTES (E10–E12) · {filas.length}/{vehiculos.length} UT</span>
          <span className="font-normal">{mostrarFiltros ? 'Ocultar ▲' : 'Mostrar ▼'}</span>
        </button>
        {mostrarFiltros && (
          <div className="px-2 pb-2 space-y-2">
            {/* E10: tipo */}
            <div>
              <span className="text-[10px] text-texto2 block mb-1">Tipo de UT (E10)</span>
              <div className="flex gap-1">
                {ORDEN_TIPOS.map((tipo) => {
                  const activo = filtros.tipos.includes(tipo);
                  return (
                    <button
                      key={tipo}
                      type="button"
                      onClick={() => alternarTipo(tipo)}
                      aria-pressed={activo}
                      className={`flex-1 py-1 rounded text-[10px] font-semibold border transition-colors ${
                        activo ? 'border-texto text-texto' : 'border-borde text-texto2 opacity-50'
                      }`}
                      style={activo ? { backgroundColor: `${COLOR_TIPO[tipo]}22` } : undefined}
                    >
                      {ETIQUETA_TIPO[tipo]}
                    </button>
                  );
                })}
              </div>
            </div>
            {/* E12: semáforo */}
            <div>
              <span className="text-[10px] text-texto2 block mb-1">Semáforo de capacidad (E12)</span>
              <div className="flex gap-1">
                {SEMAFOROS.map((s) => {
                  const activo = filtros.semaforos.includes(s.id);
                  return (
                    <button
                      key={s.id}
                      type="button"
                      onClick={() => alternarSemaforo(s.id)}
                      aria-pressed={activo}
                      className={`flex-1 flex items-center justify-center gap-1 py-1 rounded text-[10px] font-semibold border transition-colors ${
                        activo ? 'border-texto text-texto' : 'border-borde text-texto2 opacity-50'
                      }`}
                    >
                      <span className="w-1.5 h-1.5 rounded-full" style={{ backgroundColor: s.color }} />
                      {s.etiqueta}
                    </button>
                  );
                })}
              </div>
            </div>
            {/* E11: rango de distancia en km */}
            <div>
              <div className="flex items-center justify-between mb-1">
                <span className="text-[10px] text-texto2">Rango de distancia (E11)</span>
                <select
                  aria-label="Referencia del rango de distancia"
                  className="bg-panel border border-borde rounded px-1 py-0.5 text-[10px] text-texto"
                  value={filtros.referenciaKm}
                  onChange={(e) => setFiltros({ ...filtros, referenciaKm: e.target.value as ReferenciaDistancia })}
                >
                  <option value="DESTINO">al destino</option>
                  <option value="ORIGEN">al origen</option>
                </select>
              </div>
              <div className="grid grid-cols-2 gap-1">
                {RANGOS_DISTANCIA_KM.map((r) => {
                  const activo = filtros.rangosKm.includes(r.id);
                  return (
                    <button
                      key={r.id}
                      type="button"
                      onClick={() => alternarRango(r.id)}
                      aria-pressed={activo}
                      className={`py-1 rounded text-[10px] font-semibold border transition-colors ${
                        activo ? 'bg-azul/20 border-azul text-texto' : 'border-borde text-texto2 opacity-60'
                      }`}
                    >
                      {r.etiqueta}
                    </button>
                  );
                })}
              </div>
              <p className="text-[9px] text-texto2 mt-1">Sin rango seleccionado = sin filtro de distancia.</p>
            </div>
          </div>
        )}
      </div>

      {/* E13-E17: tabla ordenable */}
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
            {filas.length === 0 ? (
              <tr>
                <td colSpan={COLUMNAS.length + 1} className="px-2 py-3 text-center text-[11px] text-texto2 italic">
                  Ninguna UT pasa los filtros actuales.
                </td>
              </tr>
            ) : (
              filas.map((v) => {
                const resaltada = coincide.has(v.idVehiculo);
                const seleccionada = idSeleccionado === v.idVehiculo;
                return (
                  <tr
                    key={v.idVehiculo}
                    onClick={() => alSeleccionar(v.idVehiculo)}
                    title={`${v.idVehiculo}, ${etiquetaEstadoVehiculo(v.estado)}. Clic para ubicarla en el mapa.`}
                    className={`cursor-pointer border-b border-borde/50 last:border-b-0 hover:bg-panel ${
                      seleccionada ? 'bg-panel outline outline-1 -outline-offset-1 outline-mint' : ''
                    } ${resaltada && !seleccionada ? 'bg-ambar/10' : ''}`}
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
                    <CeldaOcupacion v={v} />
                    <CeldaHora iso={v.horaSalida} fechaReloj={fechaReloj} />
                    <CeldaHora iso={v.horaLlegadaEstimada} fechaReloj={fechaReloj} />
                    <CeldaKm km={v.distanciaPorRecorrerKm} />
                    <CeldaKm km={v.distanciaRecorridaKm} />
                  </tr>
                );
              })
            )}
          </tbody>
        </table>
      </div>
      <p className="mt-1.5 text-[10px] text-texto2 leading-snug">
        La búsqueda resalta coincidencias sin quitar filas; los filtros sí reducen la lista y se mantienen al cerrar el panel.
        El origen es el almacén central del que sale cada UT.
      </p>
    </div>
  );
});

const SinDato = () => <span className="text-[10px] text-texto2 italic">Sin dato</span>;

function CeldaOcupacion({ v }: { v: VehiculoSnapshot }) {
  const pct = ocupacionVehiculo(v);
  const sem = semaforoVehiculo(v);
  const color = SEMAFOROS.find((s) => s.id === sem)?.color ?? '#9ca3af';
  return (
    <td className="px-0.5 py-1 text-right align-top whitespace-nowrap">
      {pct == null ? (
        <SinDato />
      ) : (
        <span className="inline-flex items-center justify-end gap-1 font-mono text-texto">
          <span className="w-1.5 h-1.5 rounded-full" style={{ backgroundColor: color }} />
          {formatoDecimal(pct)}%
        </span>
      )}
    </td>
  );
}

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
