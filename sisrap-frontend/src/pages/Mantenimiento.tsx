import { useMemo, useState, type FormEvent, type MouseEvent as EventoRaton, type ReactNode } from 'react';
import type {
  AveriaVehiculo,
  BloqueoRespuesta,
  EstadoBloqueo,
  EstadoPedido,
  MantenimientoVehiculo,
  TipoAveria,
  TipoMantenimiento,
  VehiculoOperativo,
} from '../types/api';
import { navegar, type PestanaMantenimiento } from '../estado/navegacion';
import { useSimulacion } from '../estado/SimulacionContext';
import { catalogoService } from '../services/catalogoService';
import { apiFetch, textoError } from '../services/apiClient';
import { useConsulta } from '../hooks/useConsulta';
import { Cargando, ErrorCarga, Nota } from '../components/ui/Avisos';
import { Boton } from '../components/ui/Boton';
import { Modal } from '../components/ui/Modal';
import { IconoCerrar, IconoMapaSvg, IconoRecargar } from '../components/iconos';
import {
  formatoCoordenada,
  formatoDecimal,
  formatoEntero,
  formatoFechaHora,
  formatoHora,
  formatoSoles,
  NO_DISPONIBLE,
} from '../utilitarios/formato';
import { COLOR_TIPO, ETIQUETA_TIPO, etiquetaEstadoVehiculo, tipoDesdeCodigo } from '../utilitarios/vehiculos';

const PESTANAS: Array<{ id: PestanaMantenimiento; titulo: string }> = [
  { id: 'vehiculos', titulo: 'Vehículos' },
  { id: 'almacenes', titulo: 'Almacenes' },
  { id: 'pedidos', titulo: 'Pedidos' },
  { id: 'bloqueos', titulo: 'Bloqueos' },
];

interface PuntoBloqueo {
  x: number;
  y: number;
}

interface CrearBloqueoRequest {
  inicio: string;
  fin: string;
  vertices: PuntoBloqueo[];
}

function ahoraLocalInput(): string {
  const fecha = new Date();
  fecha.setMinutes(fecha.getMinutes() - fecha.getTimezoneOffset());
  return fecha.toISOString().slice(0, 16);
}

function conSegundos(valor: string): string {
  return valor.length === 16 ? `${valor}:00` : valor;
}

export function Mantenimiento({ pestana }: { pestana: PestanaMantenimiento }) {
  return (
    <div className="flex-1 flex flex-col min-h-0">
      <nav className="flex flex-wrap items-center gap-2 px-4 sm:px-6 py-2 bg-panel border-b border-borde" aria-label="Gestión operativa">
        <span className="text-[11px] uppercase tracking-wider text-texto2 mr-1">Gestión</span>
        {PESTANAS.map((p) => (
          <button
            key={p.id}
            onClick={() => navegar({ nombre: 'mantenimiento', pestana: p.id })}
            aria-current={pestana === p.id ? 'page' : undefined}
            className={`px-3 py-1 rounded-md border text-sm ${
              pestana === p.id ? 'border-mint text-mint bg-mint/10' : 'border-borde text-texto2 bg-panel2 hover:text-texto'
            }`}
          >
            {p.titulo}
          </button>
        ))}
      </nav>
      <div className="flex-1 overflow-y-auto px-4 sm:px-6 py-4">
        {pestana === 'vehiculos' && <VistaVehiculos />}
        {pestana === 'almacenes' && <VistaAlmacenes />}
        {pestana === 'pedidos' && <VistaPedidos />}
        {pestana === 'bloqueos' && <VistaBloqueos />}
      </div>
    </div>
  );
}

// ---------------------------------------------------------------------------
// Utilidades de tabla
// ---------------------------------------------------------------------------

function MarcoTabla({
  titulo,
  cantidad,
  recargar,
  cargando,
  children,
  filtros,
}: {
  titulo: string;
  cantidad: string | null;
  recargar: () => void;
  cargando: boolean;
  children: ReactNode;
  filtros?: ReactNode;
}) {
  return (
    <section className="bg-panel border border-borde rounded-lg overflow-hidden min-w-0">
      <div className="flex flex-wrap items-center gap-3 px-4 py-2.5 border-b border-borde">
        <h2 className="text-sm uppercase tracking-wider text-texto2 font-semibold">{titulo}</h2>
        {filtros}
        <span className="ml-auto font-mono text-xs text-texto2">{cantidad ?? ''}</span>
        <button
          onClick={recargar}
          disabled={cargando}
          title={cargando ? 'Actualización en curso' : 'Actualizar información'}
          className="flex items-center gap-1 text-xs border border-borde rounded px-2 py-1 text-texto hover:border-texto2 disabled:text-texto2 disabled:cursor-not-allowed"
        >
          <IconoRecargar tamano={13} /> {cargando ? 'Actualizando…' : 'Actualizar'}
        </button>
      </div>
      <div className="overflow-x-auto max-h-[calc(100vh-260px)]">{children}</div>
    </section>
  );
}

function Th({ children, derecha = false }: { children: ReactNode; derecha?: boolean }) {
  return (
    <th
      className={`sticky top-0 bg-panel2 text-texto2 font-semibold text-[11px] uppercase tracking-wide px-3 py-2 border-b border-borde whitespace-nowrap ${
        derecha ? 'text-right' : 'text-left'
      }`}
    >
      {children}
    </th>
  );
}

function Td({ children, mono = false, derecha = false }: { children: ReactNode; mono?: boolean; derecha?: boolean }) {
  return (
    <td
      className={`px-3 py-1.5 border-b border-borde/60 whitespace-nowrap text-texto ${mono ? 'font-mono' : ''} ${
        derecha ? 'text-right' : ''
      }`}
    >
      {children}
    </td>
  );
}

function Insignia({ texto, tono }: { texto: string; tono: 'ok' | 'aviso' | 'critico' | 'neutro' | 'info' }) {
  const clase = {
    ok: 'text-mint bg-mint/10',
    aviso: 'text-ambar bg-ambar/10',
    critico: 'text-rojo bg-rojo/10',
    neutro: 'text-texto2 bg-panel2',
    info: 'text-[#38bdf8] bg-[#38bdf8]/10',
  }[tono];
  return <span className={`text-[11px] font-semibold rounded px-1.5 py-0.5 ${clase}`}>{texto}</span>;
}

function EstadoConsulta({ error, cargando, vacio, recargar }: { error: string | null; cargando: boolean; vacio: boolean; recargar: () => void }) {
  if (error) return <ErrorCarga titulo="No se pudo actualizar la información" error={error} reintentar={recargar} className="m-3" />;
  if (cargando && vacio) return <div className="p-4"><Cargando /></div>;
  if (vacio) return <p className="p-4 text-sm text-texto2">No hay registros para mostrar.</p>;
  return null;
}

// ---------------------------------------------------------------------------
// Vehículos
// ---------------------------------------------------------------------------

const ETIQUETA_AVERIA: Record<TipoAveria, string> = {
  TIPO1_MENOR: 'Tipo 1 · menor',
  TIPO2_INTERMEDIA: 'Tipo 2 · intermedia',
  TIPO3_MAYOR: 'Tipo 3 · mayor',
};

function tonoEstadoVehiculo(e: string): 'ok' | 'aviso' | 'info' | 'neutro' {
  if (e === 'DISPONIBLE') return 'ok';
  if (e === 'EN_AVERIA') return 'aviso';
  if (e === 'EN_MANTENIMIENTO') return 'info';
  return 'neutro';
}

function VistaVehiculos() {
  const c = useConsulta((s) => catalogoService.vehiculos(s), 'mant-vehiculos');
  const [elegido, setElegido] = useState<string | null>(null);
  const lista = c.datos ?? [];

  return (
    <div className="flex flex-col xl:flex-row gap-4 items-start">
      <div className="flex-1 min-w-0 w-full">
        <MarcoTabla
          titulo="Vehículos"
          cantidad={c.datos ? `${lista.length} unidades` : null}
          recargar={c.recargar}
          cargando={c.cargando}
        >
          <EstadoConsulta error={c.error} cargando={c.cargando} vacio={lista.length === 0} recargar={c.recargar} />
          {lista.length > 0 && (
            <table className="w-full text-[13px] border-collapse">
              <thead>
                <tr>
                  <Th>Código</Th>
                  <Th>Tipo</Th>
                  <Th derecha>Capacidad</Th>
                  <Th derecha>Velocidad</Th>
                  <Th derecha>Tarifa</Th>
                  <Th>Estado</Th>
                  <Th>Posición</Th>
                  <Th>Conductor</Th>
                </tr>
              </thead>
              <tbody>
                {lista.map((v) => {
                  const t = tipoDesdeCodigo(v.idVehiculo);
                  return (
                    <tr
                      key={v.idVehiculo}
                      onClick={() => setElegido(v.idVehiculo)}
                      className={`cursor-pointer hover:bg-panel2 ${elegido === v.idVehiculo ? 'bg-[#16304a]' : ''}`}
                    >
                      <Td mono>
                        <span className="inline-flex items-center gap-1.5">
                          {t && <IconoMapaSvg forma={t === 'AUTO' ? 'auto' : t === 'MOTO' ? 'moto' : 'bicicleta'} color={COLOR_TIPO[t]} tamano={18} />}
                          {v.idVehiculo}
                        </span>
                      </Td>
                      <Td>{t ? ETIQUETA_TIPO[t] : v.tipo || NO_DISPONIBLE}</Td>
                      <Td mono derecha>{formatoEntero(v.capacidadPaquetes)} paq</Td>
                      <Td mono derecha>{formatoDecimal(v.velocidadKmh)} km/h</Td>
                      <Td mono derecha>{formatoSoles(v.costoPorKm)}</Td>
                      <Td><Insignia texto={etiquetaEstadoVehiculo(v.estado)} tono={tonoEstadoVehiculo(v.estado)} /></Td>
                      <Td mono>{formatoCoordenada(v.posicionX, v.posicionY)}</Td>
                      <Td mono>{v.idConductorActual ?? <span className="font-sans text-texto2 italic">Sin asignar</span>}</Td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          )}
        </MarcoTabla>
      </div>
      {elegido && <FichaVehiculo id={elegido} cerrar={() => setElegido(null)} alCambiar={c.recargar} />}
    </div>
  );
}

function FichaVehiculo({ id, cerrar, alCambiar }: { id: string; cerrar: () => void; alCambiar: () => void }) {
  const { snapshot, sesion } = useSimulacion();
  const detalle = useConsulta(
    (s) => apiFetch<VehiculoOperativo>(`/api/vehiculos/${encodeURIComponent(id)}`, { autenticar: false, senal: s }),
    `vehiculo-detalle-${id}`
  );
  const averias = useConsulta((s) => catalogoService.averias(id, s), `averias-${id}`);
  const mants = useConsulta((s) => catalogoService.mantenimientos(id, s), `mant-${id}`);
  const [modalAveria, setModalAveria] = useState(false);
  const [modalMant, setModalMant] = useState(false);
  const [accion, setAccion] = useState<string | null>(null);
  const [errorAccion, setErrorAccion] = useState<string | null>(null);
  const puedeEditar = sesion.fase === 'LISTA';

  const refrescar = () => {
    detalle.recargar();
    averias.recargar();
    mants.recargar();
    alCambiar();
  };

  const registrarAveria = async (datos: { tipoAveria: TipoAveria; fechaOcurrencia: string; horaRetornoEstimada: string | null }) => {
    setAccion('averia-nueva');
    setErrorAccion(null);
    try {
      await apiFetch<AveriaVehiculo>(`/api/vehiculos/${encodeURIComponent(id)}/averias`, {
        metodo: 'POST',
        cuerpo: datos,
      });
      setModalAveria(false);
      refrescar();
    } catch (e) {
      setErrorAccion(textoError(e));
    } finally {
      setAccion(null);
    }
  };

  const resolverAveria = async (idIncidencia: number) => {
    if (!window.confirm('¿Marcar esta avería como resuelta? El vehículo volverá a actualizar su disponibilidad según las reglas del sistema.')) return;
    setAccion(`resolver-${idIncidencia}`);
    setErrorAccion(null);
    try {
      await apiFetch<AveriaVehiculo>(
        `/api/vehiculos/${encodeURIComponent(id)}/averias/${idIncidencia}/resolver`,
        { metodo: 'POST' }
      );
      refrescar();
    } catch (e) {
      setErrorAccion(textoError(e));
    } finally {
      setAccion(null);
    }
  };

  const registrarMantenimiento = async (datos: { tipo: TipoMantenimiento; fechaInicio: string; fechaFin: string }) => {
    setAccion('mant-nuevo');
    setErrorAccion(null);
    try {
      await apiFetch<MantenimientoVehiculo>(`/api/vehiculos/${encodeURIComponent(id)}/mantenimientos`, {
        metodo: 'POST',
        cuerpo: datos,
      });
      setModalMant(false);
      refrescar();
    } catch (e) {
      setErrorAccion(textoError(e));
    } finally {
      setAccion(null);
    }
  };

  const cancelarMantenimiento = async (idMantenimiento: number) => {
    if (!window.confirm('¿Cancelar este mantenimiento programado?')) return;
    setAccion(`mant-${idMantenimiento}`);
    setErrorAccion(null);
    try {
      await apiFetch<void>(`/api/vehiculos/${encodeURIComponent(id)}/mantenimientos/${idMantenimiento}`, {
        metodo: 'DELETE',
      });
      refrescar();
    } catch (e) {
      setErrorAccion(textoError(e));
    } finally {
      setAccion(null);
    }
  };

  const vehiculo = detalle.datos;

  return (
    <>
      <aside className="w-full xl:w-[410px] shrink-0 bg-panel border border-borde rounded-lg overflow-hidden">
        <div className="flex items-start gap-3 p-4 border-b border-borde bg-panel2">
          <div className="min-w-0 flex-1">
            <p className="text-[11px] uppercase tracking-wider text-texto2">Detalle del vehículo</p>
            <h3 className="font-mono text-lg text-texto font-semibold mt-0.5">{id}</h3>
            {vehiculo && (
              <div className="flex items-center gap-2 mt-1">
                <Insignia texto={etiquetaEstadoVehiculo(vehiculo.estado)} tono={tonoEstadoVehiculo(vehiculo.estado)} />
                <span className="text-xs text-texto2">{formatoCoordenada(vehiculo.posicionX, vehiculo.posicionY)}</span>
              </div>
            )}
          </div>
          <button onClick={cerrar} className="text-texto2 hover:text-texto p-1" aria-label="Cerrar ficha">
            <IconoCerrar tamano={17} />
          </button>
        </div>

        <div className="p-4 space-y-5 max-h-[calc(100vh-220px)] overflow-y-auto">
          {detalle.error && <ErrorCarga titulo="No se pudo cargar el vehículo" error={detalle.error} reintentar={detalle.recargar} />}
          {!detalle.error && !vehiculo && <Cargando texto="Cargando vehículo…" />}

          {vehiculo && (
            <div className="grid grid-cols-2 gap-2 text-xs">
              <DatoVehiculo etiqueta="Capacidad" valor={`${formatoEntero(vehiculo.capacidadPaquetes)} paq`} />
              <DatoVehiculo etiqueta="Velocidad" valor={`${formatoDecimal(vehiculo.velocidadKmh)} km/h`} />
              <DatoVehiculo etiqueta="Costo por km" valor={formatoSoles(vehiculo.costoPorKm)} />
              <DatoVehiculo etiqueta="Conductor" valor={vehiculo.idConductorActual ?? 'Sin asignar'} />
            </div>
          )}

          {errorAccion && <ErrorCarga titulo="No se pudo completar la operación" error={errorAccion} />}

          <section>
            <div className="flex items-center gap-2 mb-2">
              <div>
                <h4 className="text-[11px] uppercase tracking-wider text-texto2 font-semibold">Mantenimientos</h4>
                <p className="text-[11px] text-texto2">Programa periodos en los que la unidad no estará disponible.</p>
              </div>
              <Boton
                type="button"
                className="ml-auto px-2.5 py-1 text-xs"
                onClick={() => { setErrorAccion(null); setModalMant(true); }}
                disabled={!puedeEditar}
                disabledReason="La sesión aún no está disponible."
              >
                Programar
              </Boton>
            </div>

            {mants.error ? (
              <ErrorCarga titulo="No se pudieron cargar los mantenimientos" error={mants.error} reintentar={mants.recargar} />
            ) : !mants.datos ? (
              <Cargando />
            ) : mants.datos.length === 0 ? (
              <p className="text-xs text-texto2 py-2">No hay mantenimientos registrados.</p>
            ) : (
              <ul className="space-y-2">
                {mants.datos.map((m) => (
                  <li key={m.idMantenimiento} className="bg-bg border border-borde rounded-md px-3 py-2 text-xs">
                    <div className="flex items-center gap-2">
                      <IconoMapaSvg forma="mantenimiento" tamano={18} />
                      <span className="text-texto font-semibold">{m.tipo === 'PREVENTIVO' ? 'Preventivo' : 'Correctivo'}</span>
                      <span className="ml-auto"><Insignia texto={m.activo ? 'Vigente' : 'Cancelado'} tono={m.activo ? 'info' : 'neutro'} /></span>
                    </div>
                    <div className="font-mono text-texto mt-1">{formatoFechaHora(m.fechaInicio)} → {formatoFechaHora(m.fechaFin)}</div>
                    {m.activo && (
                      <button
                        type="button"
                        onClick={() => cancelarMantenimiento(m.idMantenimiento)}
                        disabled={accion === `mant-${m.idMantenimiento}` || !puedeEditar}
                        className="mt-1.5 text-rojo hover:underline disabled:text-texto2 disabled:no-underline disabled:cursor-not-allowed"
                      >
                        {accion === `mant-${m.idMantenimiento}` ? 'Cancelando…' : 'Cancelar mantenimiento'}
                      </button>
                    )}
                  </li>
                ))}
              </ul>
            )}
          </section>

          <section>
            <div className="flex items-center gap-2 mb-2">
              <div>
                <h4 className="text-[11px] uppercase tracking-wider text-texto2 font-semibold">Averías</h4>
                <p className="text-[11px] text-texto2">Registra una incidencia y resuélvela cuando la unidad vuelva a operar.</p>
              </div>
              <Boton
                type="button"
                className="ml-auto px-2.5 py-1 text-xs"
                onClick={() => { setErrorAccion(null); setModalAveria(true); }}
                disabled={!puedeEditar}
                disabledReason="La sesión aún no está disponible."
              >
                Registrar
              </Boton>
            </div>

            {averias.error ? (
              <ErrorCarga titulo="No se pudieron cargar las averías" error={averias.error} reintentar={averias.recargar} />
            ) : !averias.datos ? (
              <Cargando />
            ) : averias.datos.length === 0 ? (
              <p className="text-xs text-texto2 py-2">No hay averías registradas.</p>
            ) : (
              <ul className="space-y-2">
                {averias.datos.map((a) => (
                  <li key={a.idIncidencia} className="bg-bg border border-borde rounded-md px-3 py-2 text-xs">
                    <div className="flex items-center gap-2">
                      <IconoMapaSvg forma="averia" tamano={18} />
                      <span className="text-texto font-semibold">{ETIQUETA_AVERIA[a.tipoAveria] ?? a.tipoAveria}</span>
                      <span className="ml-auto"><Insignia texto={a.activa ? 'Activa' : 'Resuelta'} tono={a.activa ? 'aviso' : 'ok'} /></span>
                    </div>
                    <div className="font-mono text-texto mt-1">{formatoFechaHora(a.fechaOcurrencia)} · {formatoCoordenada(a.ubicacionX, a.ubicacionY)}</div>
                    <div className="text-texto2 mt-0.5">
                      Retorno estimado: <span className="font-mono text-texto">{a.horaRetornoEstimada ? formatoFechaHora(a.horaRetornoEstimada) : 'Sin estimación'}</span>
                    </div>
                    {a.activa && (
                      <button
                        type="button"
                        onClick={() => resolverAveria(a.idIncidencia)}
                        disabled={accion === `resolver-${a.idIncidencia}` || !puedeEditar}
                        className="mt-1.5 text-mint hover:underline disabled:text-texto2 disabled:no-underline disabled:cursor-not-allowed"
                      >
                        {accion === `resolver-${a.idIncidencia}` ? 'Resolviendo…' : 'Marcar como resuelta'}
                      </button>
                    )}
                  </li>
                ))}
              </ul>
            )}
          </section>
        </div>
      </aside>

      <Modal
        isOpen={modalMant}
        onClose={() => accion !== 'mant-nuevo' && setModalMant(false)}
        titulo="Programar mantenimiento"
        subtitulo={`Vehículo ${id}`}
      >
        <FormularioMantenimiento
          guardando={accion === 'mant-nuevo'}
          relojSim={snapshot?.relojSimulado?.slice(0, 16) ?? ''}
          cancelar={() => setModalMant(false)}
          guardar={registrarMantenimiento}
        />
      </Modal>

      <Modal
        isOpen={modalAveria}
        onClose={() => accion !== 'averia-nueva' && setModalAveria(false)}
        titulo="Registrar avería"
        subtitulo={`Vehículo ${id}`}
      >
        <FormularioAveria
          guardando={accion === 'averia-nueva'}
          relojSim={snapshot?.relojSimulado?.slice(0, 16) ?? ''}
          cancelar={() => setModalAveria(false)}
          guardar={registrarAveria}
        />
      </Modal>
    </>
  );
}

function DatoVehiculo({ etiqueta, valor }: { etiqueta: string; valor: string }) {
  return (
    <div className="bg-bg border border-borde rounded-md px-2.5 py-2">
      <div className="text-[10px] uppercase tracking-wide text-texto2">{etiqueta}</div>
      <div className="font-mono text-texto mt-0.5">{valor}</div>
    </div>
  );
}

function FormularioMantenimiento({
  guardando,
  relojSim,
  cancelar,
  guardar,
}: {
  guardando: boolean;
  relojSim: string;
  cancelar: () => void;
  guardar: (datos: { tipo: TipoMantenimiento; fechaInicio: string; fechaFin: string }) => Promise<void>;
}) {
  const base = relojSim || ahoraLocalInput();
  const [tipo, setTipo] = useState<TipoMantenimiento>('PREVENTIVO');
  const [inicio, setInicio] = useState(base);
  const [fin, setFin] = useState('');
  const [error, setError] = useState<string | null>(null);

  const enviar = async (e: FormEvent) => {
    e.preventDefault();
    if (!inicio || !fin) {
      setError('Indica la fecha y hora de inicio y fin.');
      return;
    }
    if (fin <= inicio) {
      setError('La fecha de fin debe ser posterior al inicio.');
      return;
    }
    setError(null);
    await guardar({ tipo, fechaInicio: conSegundos(inicio), fechaFin: conSegundos(fin) });
  };

  return (
    <form onSubmit={enviar} className="space-y-4">
      <label className="flex flex-col gap-1 text-xs text-texto2">
        Tipo de mantenimiento
        <select
          value={tipo}
          onChange={(e) => setTipo(e.target.value as TipoMantenimiento)}
          className="bg-bg border border-borde rounded-md px-3 py-2 text-sm text-texto focus:outline-none focus:border-mint"
        >
          <option value="PREVENTIVO">Preventivo</option>
          <option value="CORRECTIVO">Correctivo</option>
        </select>
      </label>
      <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
        <label className="flex flex-col gap-1 text-xs text-texto2">
          Inicio
          <input type="datetime-local" value={inicio} onChange={(e) => setInicio(e.target.value)} className="bg-bg border border-borde rounded-md px-3 py-2 font-mono text-sm text-texto" />
        </label>
        <label className="flex flex-col gap-1 text-xs text-texto2">
          Fin
          <input type="datetime-local" min={inicio || undefined} value={fin} onChange={(e) => setFin(e.target.value)} className="bg-bg border border-borde rounded-md px-3 py-2 font-mono text-sm text-texto" />
        </label>
      </div>
      {error && <ErrorCarga titulo="Revisa las fechas" error={error} />}
      <div className="flex justify-end gap-2">
        <Boton type="button" variant="secundario" onClick={cancelar} disabled={guardando} disabledReason="Guardando mantenimiento.">Cerrar</Boton>
        <Boton type="submit" disabled={guardando} disabledReason="Guardando mantenimiento.">{guardando ? 'Guardando…' : 'Programar mantenimiento'}</Boton>
      </div>
    </form>
  );
}

function FormularioAveria({
  guardando,
  relojSim,
  cancelar,
  guardar,
}: {
  guardando: boolean;
  relojSim: string;
  cancelar: () => void;
  guardar: (datos: { tipoAveria: TipoAveria; fechaOcurrencia: string; horaRetornoEstimada: string | null }) => Promise<void>;
}) {
  const [tipo, setTipo] = useState<TipoAveria>('TIPO1_MENOR');
  const [ocurrencia, setOcurrencia] = useState(relojSim || ahoraLocalInput());
  const [retorno, setRetorno] = useState('');
  const [error, setError] = useState<string | null>(null);

  const enviar = async (e: FormEvent) => {
    e.preventDefault();
    if (!ocurrencia) {
      setError('Indica cuándo ocurrió la avería.');
      return;
    }
    if (retorno && retorno <= ocurrencia) {
      setError('El retorno estimado debe ser posterior a la avería.');
      return;
    }
    setError(null);
    await guardar({
      tipoAveria: tipo,
      fechaOcurrencia: conSegundos(ocurrencia),
      horaRetornoEstimada: retorno ? conSegundos(retorno) : null,
    });
  };

  return (
    <form onSubmit={enviar} className="space-y-4">
      <label className="flex flex-col gap-1 text-xs text-texto2">
        Severidad
        <select
          value={tipo}
          onChange={(e) => setTipo(e.target.value as TipoAveria)}
          className="bg-bg border border-borde rounded-md px-3 py-2 text-sm text-texto focus:outline-none focus:border-mint"
        >
          <option value="TIPO1_MENOR">Tipo 1 · menor</option>
          <option value="TIPO2_INTERMEDIA">Tipo 2 · intermedia</option>
          <option value="TIPO3_MAYOR">Tipo 3 · mayor</option>
        </select>
      </label>
      <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
        <label className="flex flex-col gap-1 text-xs text-texto2">
          Fecha y hora de ocurrencia
          <input type="datetime-local" value={ocurrencia} onChange={(e) => setOcurrencia(e.target.value)} className="bg-bg border border-borde rounded-md px-3 py-2 font-mono text-sm text-texto" />
        </label>
        <label className="flex flex-col gap-1 text-xs text-texto2">
          Retorno estimado <span className="text-[10px]">(opcional)</span>
          <input type="datetime-local" min={ocurrencia || undefined} value={retorno} onChange={(e) => setRetorno(e.target.value)} className="bg-bg border border-borde rounded-md px-3 py-2 font-mono text-sm text-texto" />
        </label>
      </div>
      <Nota>Si hay una simulación en curso, el formulario toma por defecto el reloj simulado como instante de ocurrencia.</Nota>
      {error && <ErrorCarga titulo="Revisa los datos" error={error} />}
      <div className="flex justify-end gap-2">
        <Boton type="button" variant="secundario" onClick={cancelar} disabled={guardando} disabledReason="Registrando avería.">Cerrar</Boton>
        <Boton type="submit" disabled={guardando} disabledReason="Registrando avería.">{guardando ? 'Registrando…' : 'Registrar avería'}</Boton>
      </div>
    </form>
  );
}

// ---------------------------------------------------------------------------
// Almacenes
// ---------------------------------------------------------------------------

function VistaAlmacenes() {
  const c = useConsulta((s) => catalogoService.almacenes(s), 'mant-almacenes');
  const lista = c.datos ?? [];
  return (
    <MarcoTabla titulo="Almacenes" cantidad={c.datos ? `${lista.length} almacenes` : null} recargar={c.recargar} cargando={c.cargando}>
      <EstadoConsulta error={c.error} cargando={c.cargando} vacio={lista.length === 0} recargar={c.recargar} />
      {lista.length > 0 && (
        <table className="w-full text-[13px] border-collapse">
          <thead>
            <tr>
              <Th>Código</Th>
              <Th>Nombre</Th>
              <Th>Tipo</Th>
              <Th>Ubicación</Th>
              <Th derecha>Capacidad</Th>
              <Th derecha>Stock actual</Th>
              <Th>Hora de recarga</Th>
            </tr>
          </thead>
          <tbody>
            {lista.map((a) => (
              <tr key={a.idAlmacen} className="hover:bg-panel2">
                <Td mono>{a.idAlmacen}</Td>
                <Td>
                  <span className="inline-flex items-center gap-1.5">
                    <IconoMapaSvg forma={a.tipo === 'CENTRAL' ? 'almacenCentral' : 'almacenIntermedio'} tamano={18} />
                    {a.nombre}
                  </span>
                </Td>
                <Td>{a.tipo === 'CENTRAL' ? 'Central' : 'Intermedio'}</Td>
                <Td mono>{formatoCoordenada(a.ubicacionX, a.ubicacionY)}</Td>
                <Td mono derecha>{a.capacidadMaxima == null ? <span className="font-sans">Ilimitada</span> : formatoEntero(a.capacidadMaxima)}</Td>
                <Td mono derecha>{a.stockActual == null ? <span className="font-sans">Ilimitado</span> : formatoEntero(a.stockActual)}</Td>
                <Td mono>{a.horaRecarga ? formatoHora(`2000-01-01T${a.horaRecarga}`) : <span className="font-sans text-texto2">No aplica</span>}</Td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </MarcoTabla>
  );
}

// ---------------------------------------------------------------------------
// Pedidos
// ---------------------------------------------------------------------------

const ETIQUETA_PEDIDO: Record<EstadoPedido, string> = {
  PENDIENTE: 'Pendiente',
  EN_RUTA: 'En ruta',
  ENTREGADO: 'Entregado',
  REASIGNADO: 'Reasignado',
  RETRASADO: 'Fuera de plazo',
};
const TAMANIO_PAGINA = 100;

function VistaPedidos() {
  const { snapshot } = useSimulacion();
  const [pagina, setPagina] = useState(0);
  const consulta = useConsulta(
    s => catalogoService.pedidos({ pagina, tamanio: TAMANIO_PAGINA }, s),
    `pedidos-${snapshot?.idSimulacion ?? 'sin-corrida'}-${pagina}`,
    snapshot?.estado === 'EJECUTANDO' ? 8_000 : null
  );
  const lista = consulta.datos?.contenido ?? [];
  const totalPaginas = consulta.datos ? Math.max(1, Math.ceil(consulta.datos.total / TAMANIO_PAGINA)) : 1;

  return (
    <MarcoTabla
      titulo="Pedidos generados en la corrida"
      cantidad={consulta.datos ? `${formatoEntero(consulta.datos.total)} pedidos` : null}
      recargar={consulta.recargar}
      cargando={consulta.cargando}
    >
      <EstadoConsulta error={consulta.error} cargando={consulta.cargando} vacio={lista.length === 0} recargar={consulta.recargar} />
      {lista.length > 0 && (
        <>
          <table className="w-full text-[13px] border-collapse">
            <thead>
              <tr>
                <Th>Pedido</Th>
                <Th>Cliente</Th>
                <Th derecha>Cantidad</Th>
                <Th derecha>Plazo</Th>
                <Th>Llegada</Th>
                <Th>Entrega real</Th>
                <Th>Ubicación</Th>
                <Th>Estado</Th>
              </tr>
            </thead>
            <tbody>
              {lista.map(p => (
                <tr key={p.idPedido} className="hover:bg-panel2">
                  <Td mono>{p.idPedido}</Td>
                  <Td mono>{p.idCliente}</Td>
                  <Td mono derecha>{formatoEntero(p.cantidadQq)} paq</Td>
                  <Td mono derecha>{p.horasLimite} h</Td>
                  <Td mono>{formatoFechaHora(p.fechaLlegada)}</Td>
                  <Td mono>{p.fechaEntregaReal ? formatoFechaHora(p.fechaEntregaReal) : <span className="font-sans text-texto2">—</span>}</Td>
                  <Td mono>{formatoCoordenada(p.ubicacionX, p.ubicacionY)}</Td>
                  <Td><Insignia texto={ETIQUETA_PEDIDO[p.estado] ?? p.estado} tono={p.estado === 'ENTREGADO' ? 'ok' : p.estado === 'RETRASADO' ? 'critico' : 'neutro'} /></Td>
                </tr>
              ))}
            </tbody>
          </table>
          <Paginacion pagina={pagina} total={totalPaginas} cambiar={setPagina} />
        </>
      )}
    </MarcoTabla>
  );
}

function Paginacion({ pagina, total, cambiar }: { pagina: number; total: number; cambiar: (p: number) => void }) {
  return (
    <div className="flex items-center justify-end gap-2 px-4 py-2 text-xs text-texto2 border-t border-borde">
      <button className="border border-borde rounded px-2 py-1 text-texto disabled:text-texto2 disabled:cursor-not-allowed" disabled={pagina === 0} onClick={() => cambiar(pagina - 1)}>
        Anterior
      </button>
      <span>Página <span className="font-mono text-texto">{pagina + 1}</span> de <span className="font-mono text-texto">{total}</span></span>
      <button className="border border-borde rounded px-2 py-1 text-texto disabled:text-texto2 disabled:cursor-not-allowed" disabled={pagina + 1 >= total} onClick={() => cambiar(pagina + 1)}>
        Siguiente
      </button>
    </div>
  );
}

// ---------------------------------------------------------------------------
// Bloqueos
// ---------------------------------------------------------------------------

const ESTADOS_BLOQUEO: EstadoBloqueo[] = ['PROGRAMADO', 'ACTIVO', 'FINALIZADO', 'CANCELADO'];

function VistaBloqueos() {
  const { snapshot, sesion } = useSimulacion();
  const relojSim = snapshot?.relojSimulado?.slice(0, 16) ?? '';
  const [instante, setInstante] = useState(relojSim);
  const [estado, setEstado] = useState<EstadoBloqueo | ''>('');
  const [pagina, setPagina] = useState(0);
  const [modal, setModal] = useState(false);
  const [creando, setCreando] = useState(false);
  const [errorAccion, setErrorAccion] = useState<string | null>(null);
  const [eliminando, setEliminando] = useState<number | null>(null);

  const c = useConsulta((s) => catalogoService.bloqueos(instante ? `${instante}:00` : null, s), `bloqueos-${instante}`);
  const mapa = useConsulta((s) => catalogoService.mapa(s), 'mant-bloqueos-mapa');
  const filtrados = useMemo(() => (c.datos ?? []).filter((b) => !estado || b.estado === estado), [c.datos, estado]);
  const totalPaginas = Math.max(1, Math.ceil(filtrados.length / TAMANIO_PAGINA));
  const visibles = filtrados.slice(pagina * TAMANIO_PAGINA, (pagina + 1) * TAMANIO_PAGINA);
  const puedeEditar = sesion.fase === 'LISTA';

  const crear = async (datos: CrearBloqueoRequest) => {
    setCreando(true);
    setErrorAccion(null);
    try {
      await apiFetch('/api/bloqueos', { metodo: 'POST', cuerpo: datos });
      setModal(false);
      setPagina(0);
      c.recargar();
    } catch (e) {
      setErrorAccion(textoError(e));
    } finally {
      setCreando(false);
    }
  };

  const eliminar = async (idIncidencia: number) => {
    if (!window.confirm('¿Eliminar este bloqueo? Se conservará como cancelado en el historial para mantener la trazabilidad.')) return;
    setEliminando(idIncidencia);
    setErrorAccion(null);
    try {
      await apiFetch<void>(`/api/bloqueos/${idIncidencia}`, { metodo: 'DELETE' });
      c.recargar();
    } catch (e) {
      setErrorAccion(textoError(e));
    } finally {
      setEliminando(null);
    }
  };

  return (
    <>
      <MarcoTabla
        titulo="Bloqueos viales"
        cantidad={c.datos ? `${formatoEntero(filtrados.length)} de ${formatoEntero(c.datos.length)}` : null}
        recargar={c.recargar}
        cargando={c.cargando}
        filtros={
          <div className="flex flex-wrap items-center gap-2 text-xs text-texto2">
            <label className="flex items-center gap-1">
              Ver estado en
              <input
                type="datetime-local"
                className="bg-bg border border-borde rounded px-2 py-1 font-mono text-xs text-texto"
                value={instante}
                onChange={(e) => { setInstante(e.target.value); setPagina(0); }}
              />
            </label>
            {relojSim && instante !== relojSim && (
              <button className="text-azul underline" onClick={() => { setInstante(relojSim); setPagina(0); }}>Usar hora de simulación</button>
            )}
            <label className="flex items-center gap-1">
              Estado
              <select className="bg-bg border border-borde rounded px-2 py-1 text-xs text-texto" value={estado} onChange={(e) => { setEstado(e.target.value as EstadoBloqueo | ''); setPagina(0); }}>
                <option value="">Todos</option>
                {ESTADOS_BLOQUEO.map((e) => <option key={e} value={e}>{e.charAt(0) + e.slice(1).toLowerCase()}</option>)}
              </select>
            </label>
            <Boton
              type="button"
              className="ml-1 py-1 px-3 text-xs"
              onClick={() => { setErrorAccion(null); setModal(true); }}
              disabled={!puedeEditar}
              disabledReason="La sesión aún no está disponible."
            >
              Nuevo bloqueo
            </Boton>
          </div>
        }
      >
        {errorAccion && <ErrorCarga titulo="No se pudo completar la operación" error={errorAccion} className="m-3" />}
        <EstadoConsulta error={c.error} cargando={c.cargando} vacio={visibles.length === 0} recargar={c.recargar} />
        {visibles.length > 0 && (
          <>
            <table className="w-full text-[13px] border-collapse">
              <thead>
                <tr>
                  <Th>Incidencia</Th>
                  <Th>Inicio</Th>
                  <Th>Fin</Th>
                  <Th>Estado</Th>
                  <Th>Tramo</Th>
                  <Th>Acciones</Th>
                </tr>
              </thead>
              <tbody>
                {visibles.map((b) => (
                  <tr key={b.idIncidencia} className="hover:bg-panel2">
                    <Td mono>{b.idIncidencia}</Td>
                    <Td mono>{formatoFechaHora(b.inicio)}</Td>
                    <Td mono>{formatoFechaHora(b.fin)}</Td>
                    <Td><Insignia texto={b.estado.charAt(0) + b.estado.slice(1).toLowerCase()} tono={b.estado === 'ACTIVO' ? 'critico' : b.estado === 'PROGRAMADO' ? 'aviso' : b.estado === 'FINALIZADO' ? 'ok' : 'neutro'} /></Td>
                    <Td mono>{b.vertices.map((v) => formatoCoordenada(v.x, v.y)).join(' → ')}</Td>
                    <Td>
                      {b.estado === 'PROGRAMADO' || b.estado === 'ACTIVO' ? (
                        <button
                          type="button"
                          onClick={() => eliminar(b.idIncidencia)}
                          disabled={eliminando === b.idIncidencia || !puedeEditar}
                          className="text-rojo hover:underline disabled:text-texto2 disabled:no-underline disabled:cursor-not-allowed"
                        >
                          {eliminando === b.idIncidencia ? 'Eliminando…' : 'Eliminar'}
                        </button>
                      ) : <span className="text-texto2">—</span>}
                    </Td>
                  </tr>
                ))}
              </tbody>
            </table>
            <Paginacion pagina={pagina} total={totalPaginas} cambiar={setPagina} />
          </>
        )}
      </MarcoTabla>

      <Modal
        isOpen={modal}
        onClose={() => !creando && setModal(false)}
        titulo="Nuevo bloqueo vial"
        subtitulo="Indica el periodo y el recorrido que quedará temporalmente inhabilitado."
        tamano="ancho"
      >
        <FormularioBloqueo
          relojSim={relojSim}
          ancho={mapa.datos?.anchoKm ?? 70}
          alto={mapa.datos?.altoKm ?? 50}
          bloqueos={c.datos ?? []}
          guardando={creando}
          cancelar={() => setModal(false)}
          guardar={crear}
        />
      </Modal>
    </>
  );
}

function FormularioBloqueo({
  relojSim,
  ancho,
  alto,
  bloqueos,
  guardando,
  cancelar,
  guardar,
}: {
  relojSim: string;
  ancho: number;
  alto: number;
  bloqueos: BloqueoRespuesta[];
  guardando: boolean;
  cancelar: () => void;
  guardar: (datos: CrearBloqueoRequest) => Promise<void>;
}) {
  const [inicio, setInicio] = useState(relojSim || ahoraLocalInput());
  const [fin, setFin] = useState('');
  const [vertices, setVertices] = useState<PuntoBloqueo[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [avisoMapa, setAvisoMapa] = useState<string | null>(null);

  const agregarPunto = (punto: PuntoBloqueo) => {
    setError(null);
    setAvisoMapa(null);
    setVertices((actuales) => {
      const ultimo = actuales[actuales.length - 1];
      if (ultimo && ultimo.x === punto.x && ultimo.y === punto.y) {
        setAvisoMapa('Ese punto ya es el último del recorrido. Selecciona otro nodo de la cuadrícula.');
        return actuales;
      }
      return [...actuales, punto];
    });
  };

  const deshacer = () => {
    setError(null);
    setAvisoMapa(null);
    setVertices((actuales) => actuales.slice(0, -1));
  };

  const limpiar = () => {
    setError(null);
    setAvisoMapa(null);
    setVertices([]);
  };

  const validar = (): string | null => {
    if (!inicio || !fin) return 'Indica la fecha y hora de inicio y fin.';
    if (fin <= inicio) return 'La fecha de fin debe ser posterior al inicio.';
    if (vertices.length < 2) return 'Marca al menos dos puntos en el mapa para definir el tramo bloqueado.';
    for (const v of vertices) {
      if (!Number.isInteger(v.x) || !Number.isInteger(v.y)) return 'Los puntos del recorrido deben coincidir con nodos de la cuadrícula.';
      if (v.x < 0 || v.x > ancho || v.y < 0 || v.y > alto) return `El recorrido debe mantenerse dentro de la ciudad (${ancho} × ${alto} km).`;
    }
    for (let i = 0; i < vertices.length - 1; i += 1) {
      const a = vertices[i];
      const b = vertices[i + 1];
      if (a.x === b.x && a.y === b.y) return 'Dos puntos consecutivos no pueden ser iguales.';
      if (a.x !== b.x && a.y !== b.y) return 'Cada tramo debe ser horizontal o vertical.';
    }
    return null;
  };

  const enviar = async (e: FormEvent) => {
    e.preventDefault();
    const mensaje = validar();
    setError(mensaje);
    if (mensaje) return;
    await guardar({ inicio: conSegundos(inicio), fin: conSegundos(fin), vertices });
  };

  return (
    <form onSubmit={enviar} className="space-y-4">
      <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
        <label className="flex flex-col gap-1 text-xs text-texto2">
          Inicio
          <input type="datetime-local" value={inicio} onChange={(e) => setInicio(e.target.value)} className="bg-bg border border-borde rounded-md px-3 py-2 font-mono text-sm text-texto" />
        </label>
        <label className="flex flex-col gap-1 text-xs text-texto2">
          Fin
          <input type="datetime-local" min={inicio || undefined} value={fin} onChange={(e) => setFin(e.target.value)} className="bg-bg border border-borde rounded-md px-3 py-2 font-mono text-sm text-texto" />
        </label>
      </div>

      <section className="border border-borde rounded-lg overflow-hidden bg-bg">
        <div className="flex flex-wrap items-center gap-3 px-3 py-2.5 bg-panel border-b border-borde">
          <div className="min-w-0">
            <h3 className="text-sm font-semibold text-texto">Señala el tramo en el mapa</h3>
            <p className="text-[11px] text-texto2 mt-0.5">
              Haz clic en el punto inicial y continúa marcando el recorrido. Los siguientes puntos se ajustan automáticamente al eje horizontal o vertical más cercano.
            </p>
          </div>
          <div className="ml-auto flex items-center gap-2">
            <button
              type="button"
              onClick={deshacer}
              disabled={vertices.length === 0 || guardando}
              className="text-xs border border-borde rounded px-2.5 py-1.5 text-texto hover:border-texto2 disabled:text-texto2 disabled:cursor-not-allowed"
            >
              Deshacer
            </button>
            <button
              type="button"
              onClick={limpiar}
              disabled={vertices.length === 0 || guardando}
              className="text-xs border border-borde rounded px-2.5 py-1.5 text-rojo hover:border-rojo disabled:text-texto2 disabled:cursor-not-allowed"
            >
              Limpiar
            </button>
          </div>
        </div>

        <div className="p-3 sm:p-4">
          <MapaSeleccionBloqueo
            ancho={ancho}
            alto={alto}
            vertices={vertices}
            bloqueos={bloqueos}
            deshabilitado={guardando}
            alAgregar={agregarPunto}
          />

          <div className="flex flex-wrap items-center gap-2 mt-3 min-h-7">
            <span className="text-[11px] uppercase tracking-wide text-texto2">Recorrido</span>
            {vertices.length === 0 ? (
              <span className="text-xs text-texto2">Aún no has marcado ningún punto.</span>
            ) : (
              vertices.map((v, i) => (
                <span key={`${i}-${v.x}-${v.y}`} className="inline-flex items-center gap-1 rounded-full border border-borde bg-panel px-2 py-1 text-xs text-texto">
                  <span className="text-texto2">{i + 1}</span>
                  <span className="font-mono">{formatoCoordenada(v.x, v.y)}</span>
                </span>
              ))
            )}
          </div>
          {avisoMapa && <p className="text-xs text-ambar mt-2">{avisoMapa}</p>}
        </div>
      </section>

      <Nota tono="info">
        Los bloqueos existentes se muestran en rojo. El recorrido que estás creando aparece resaltado y cada clic se ajusta a un nodo entero de la cuadrícula.
      </Nota>

      {error && <ErrorCarga titulo="Revisa los datos del bloqueo" error={error} />}
      <div className="flex justify-end gap-2">
        <Boton type="button" variant="secundario" onClick={cancelar} disabled={guardando} disabledReason="Guardando bloqueo.">Cerrar</Boton>
        <Boton
          type="submit"
          disabled={guardando || vertices.length < 2}
          disabledReason={guardando ? 'Guardando bloqueo.' : 'Marca al menos dos puntos en el mapa.'}
        >
          {guardando ? 'Guardando…' : 'Crear bloqueo'}
        </Boton>
      </div>
    </form>
  );
}

function MapaSeleccionBloqueo({
  ancho,
  alto,
  vertices,
  bloqueos,
  deshabilitado,
  alAgregar,
}: {
  ancho: number;
  alto: number;
  vertices: PuntoBloqueo[];
  bloqueos: BloqueoRespuesta[];
  deshabilitado: boolean;
  alAgregar: (punto: PuntoBloqueo) => void;
}) {
  const [cursor, setCursor] = useState<PuntoBloqueo | null>(null);
  const ultimo = vertices[vertices.length - 1] ?? null;

  const coordenadaDesdeEvento = (e: EventoRaton<SVGSVGElement>): PuntoBloqueo => {
    const rect = e.currentTarget.getBoundingClientRect();
    const x = Math.max(0, Math.min(ancho, Math.round(((e.clientX - rect.left) / rect.width) * ancho)));
    const y = Math.max(0, Math.min(alto, Math.round(alto - ((e.clientY - rect.top) / rect.height) * alto)));
    return { x, y };
  };

  const ajustarAlEje = (punto: PuntoBloqueo): PuntoBloqueo => {
    if (!ultimo) return punto;
    if (punto.x === ultimo.x || punto.y === ultimo.y) return punto;
    const dx = Math.abs(punto.x - ultimo.x);
    const dy = Math.abs(punto.y - ultimo.y);
    return dx >= dy ? { x: punto.x, y: ultimo.y } : { x: ultimo.x, y: punto.y };
  };

  const mover = (e: EventoRaton<SVGSVGElement>) => {
    if (deshabilitado) return;
    setCursor(ajustarAlEje(coordenadaDesdeEvento(e)));
  };

  const seleccionar = (e: EventoRaton<SVGSVGElement>) => {
    if (deshabilitado) return;
    const punto = ajustarAlEje(coordenadaDesdeEvento(e));
    alAgregar(punto);
    setCursor(punto);
  };

  const ySvg = (y: number) => alto - y;
  const puntosSeleccionados = vertices.map((v) => `${v.x},${ySvg(v.y)}`).join(' ');
  const puntosVistaPrevia = ultimo && cursor
    ? `${ultimo.x},${ySvg(ultimo.y)} ${cursor.x},${ySvg(cursor.y)}`
    : '';
  const bloqueosVisibles = bloqueos.filter((b) => b.estado === 'PROGRAMADO' || b.estado === 'ACTIVO');

  return (
    <div className="relative mx-auto w-full max-w-[820px] select-none" style={{ aspectRatio: `${ancho} / ${alto}` }}>
      <svg
        viewBox={`0 0 ${ancho} ${alto}`}
        className={`block w-full h-full rounded-md border border-borde bg-[#0a111e] ${deshabilitado ? 'cursor-not-allowed opacity-70' : 'cursor-crosshair'}`}
        onMouseMove={mover}
        onMouseLeave={() => setCursor(null)}
        onClick={seleccionar}
        role="application"
        aria-label={`Mapa de ${ancho} por ${alto} kilómetros para seleccionar un bloqueo vial`}
      >
        <rect x="0" y="0" width={ancho} height={alto} fill="#0a111e" />

        {Array.from({ length: ancho + 1 }, (_, x) => (
          <line
            key={`gx-${x}`}
            x1={x}
            y1={0}
            x2={x}
            y2={alto}
            stroke={x % 10 === 0 ? '#334766' : '#17263c'}
            strokeWidth={x % 10 === 0 ? 1.1 : 0.55}
            vectorEffect="non-scaling-stroke"
          />
        ))}
        {Array.from({ length: alto + 1 }, (_, y) => (
          <line
            key={`gy-${y}`}
            x1={0}
            y1={y}
            x2={ancho}
            y2={y}
            stroke={y % 10 === 0 ? '#334766' : '#17263c'}
            strokeWidth={y % 10 === 0 ? 1.1 : 0.55}
            vectorEffect="non-scaling-stroke"
          />
        ))}

        {bloqueosVisibles.map((b) => (
          <polyline
            key={`existente-${b.idIncidencia}`}
            points={b.vertices.map((v) => `${v.x},${ySvg(v.y)}`).join(' ')}
            fill="none"
            stroke="#ef4444"
            strokeOpacity={b.estado === 'ACTIVO' ? 0.72 : 0.38}
            strokeWidth={4}
            strokeLinecap="round"
            strokeLinejoin="round"
            vectorEffect="non-scaling-stroke"
            pointerEvents="none"
          />
        ))}

        {puntosVistaPrevia && (
          <polyline
            points={puntosVistaPrevia}
            fill="none"
            stroke="#fbbf24"
            strokeOpacity={0.75}
            strokeWidth={3}
            strokeDasharray="7 5"
            strokeLinecap="round"
            vectorEffect="non-scaling-stroke"
            pointerEvents="none"
          />
        )}

        {vertices.length > 1 && (
          <polyline
            points={puntosSeleccionados}
            fill="none"
            stroke="#34d399"
            strokeWidth={4}
            strokeLinecap="round"
            strokeLinejoin="round"
            vectorEffect="non-scaling-stroke"
            pointerEvents="none"
          />
        )}

        {vertices.map((v, i) => (
          <g key={`punto-${i}-${v.x}-${v.y}`} pointerEvents="none">
            <circle
              cx={v.x}
              cy={ySvg(v.y)}
              r={0.72}
              fill={i === 0 ? '#38bdf8' : '#34d399'}
              stroke="#ffffff"
              strokeWidth={1.5}
              vectorEffect="non-scaling-stroke"
            />
          </g>
        ))}

        {cursor && (
          <circle
            cx={cursor.x}
            cy={ySvg(cursor.y)}
            r={0.62}
            fill="#fbbf24"
            stroke="#ffffff"
            strokeWidth={1.25}
            vectorEffect="non-scaling-stroke"
            pointerEvents="none"
          />
        )}
      </svg>

      <div className="absolute left-2 top-2 pointer-events-none rounded bg-panel/95 border border-borde px-2 py-1 text-[11px] text-texto2 shadow">
        <span className="inline-flex items-center gap-1.5 mr-3"><span className="w-3 h-1 rounded bg-rojo" /> Existente</span>
        <span className="inline-flex items-center gap-1.5"><span className="w-3 h-1 rounded bg-mint" /> Nuevo</span>
      </div>

      <div className="absolute right-2 bottom-2 pointer-events-none rounded bg-panel/95 border border-borde px-2 py-1 font-mono text-xs text-texto shadow">
        {cursor ? `X ${cursor.x} · Y ${cursor.y}` : `Ciudad ${ancho} × ${alto} km`}
      </div>
    </div>
  );
}
