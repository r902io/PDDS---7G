import { useMemo, useState, type ReactNode } from 'react';
import type { EstadoBloqueo, EstadoPedido, TipoAveria } from '../types/api';
import { navegar, type PestanaMantenimiento } from '../estado/navegacion';
import { useSimulacion } from '../estado/SimulacionContext';
import { catalogoService } from '../services/catalogoService';
import { useConsulta } from '../hooks/useConsulta';
import { Cargando, ErrorCarga, Nota } from '../components/ui/Avisos';
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

const PESTANAS: Array<{ id: PestanaMantenimiento; titulo: string; ruta: string }> = [
  { id: 'vehiculos', titulo: 'Vehículos', ruta: '/api/vehiculos' },
  { id: 'almacenes', titulo: 'Almacenes', ruta: '/api/almacenes' },
  { id: 'pedidos', titulo: 'Pedidos', ruta: '/api/pedidos' },
  { id: 'bloqueos', titulo: 'Bloqueos', ruta: '/api/bloqueos' },
];

export function Mantenimiento({ pestana }: { pestana: PestanaMantenimiento }) {
  return (
    <div className="flex-1 flex flex-col min-h-0">
      <nav className="flex flex-wrap items-center gap-2 px-4 sm:px-6 py-2 bg-panel border-b border-borde" aria-label="Mantenimientos">
        <span className="text-[11px] uppercase tracking-wider text-texto2 mr-1">Mantenimiento</span>
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
        <span className="ml-auto text-xs text-texto2">Solo consulta en esta entrega.</span>
      </nav>
      <div className="flex-1 overflow-y-auto px-4 sm:px-6 py-4">
        <Nota className="mb-4 max-w-4xl">
          Estas vistas leen los datos maestros del backend. Cuáles serán editables aún no está definido, por lo que no
          hay controles para crear, editar ni eliminar registros.
        </Nota>
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
  ruta,
  cantidad,
  recargar,
  cargando,
  children,
  filtros,
}: {
  titulo: string;
  ruta: string;
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
        <span className="font-mono text-xs text-texto2">{ruta}</span>
        {filtros}
        <span className="ml-auto font-mono text-xs text-texto2">{cantidad ?? ''}</span>
        <button
          onClick={recargar}
          disabled={cargando}
          title={cargando ? 'Consulta en curso' : 'Volver a consultar al backend'}
          className="flex items-center gap-1 text-xs border border-borde rounded px-2 py-1 text-texto hover:border-texto2 disabled:text-texto2 disabled:cursor-not-allowed"
        >
          <IconoRecargar tamano={13} /> {cargando ? 'Consultando…' : 'Actualizar'}
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
  if (error) return <ErrorCarga titulo="El backend no respondió a la consulta" error={error} reintentar={recargar} className="m-3" />;
  if (cargando && vacio) return <div className="p-4"><Cargando /></div>;
  if (vacio) return <p className="p-4 text-sm text-texto2">El backend no devolvió registros.</p>;
  return null;
}

// ---------------------------------------------------------------------------
// Vehículos
// ---------------------------------------------------------------------------

const ETIQUETA_AVERIA: Record<TipoAveria, string> = {
  TIPO1_MENOR: 'Tipo 1 (menor)',
  TIPO2_INTERMEDIA: 'Tipo 2 (intermedia)',
  TIPO3_MAYOR: 'Tipo 3 (mayor)',
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
        <MarcoTabla titulo="Vehículos" ruta="GET /api/vehiculos" cantidad={c.datos ? `${lista.length} unidades` : null} recargar={c.recargar} cargando={c.cargando}>
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
                      <Td>{t ? ETIQUETA_TIPO[t] : NO_DISPONIBLE}</Td>
                      <Td mono derecha>{formatoEntero(v.capacidadPaquetes)} paq</Td>
                      <Td mono derecha>{formatoDecimal(v.velocidadKmh)} km/h</Td>
                      <Td mono derecha>{formatoSoles(v.costoPorKm)}</Td>
                      <Td>
                        <Insignia texto={etiquetaEstadoVehiculo(v.estado)} tono={tonoEstadoVehiculo(v.estado)} />
                      </Td>
                      <Td mono>{formatoCoordenada(v.posicionX, v.posicionY)}</Td>
                      <Td mono>{v.idConductorActual ?? <span className="font-sans text-texto2 italic">{NO_DISPONIBLE}</span>}</Td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          )}
        </MarcoTabla>
      </div>
      {elegido && <FichaVehiculo id={elegido} cerrar={() => setElegido(null)} />}
    </div>
  );
}

function FichaVehiculo({ id, cerrar }: { id: string; cerrar: () => void }) {
  const averias = useConsulta((s) => catalogoService.averias(id, s), `averias-${id}`);
  const mants = useConsulta((s) => catalogoService.mantenimientos(id, s), `mant-${id}`);
  return (
    <aside className="w-full xl:w-[360px] shrink-0 bg-panel border border-borde rounded-lg p-4 space-y-4">
      <div className="flex items-center gap-2">
        <h3 className="font-mono text-base text-texto font-semibold">{id}</h3>
        <button onClick={cerrar} className="ml-auto text-texto2 hover:text-texto" aria-label="Cerrar ficha">
          <IconoCerrar tamano={16} />
        </button>
      </div>

      <div>
        <h4 className="text-[11px] uppercase tracking-wider text-texto2 font-semibold mb-1">Mantenimientos programados</h4>
        <p className="text-[11px] text-texto2 mb-1.5">
          Un mantenimiento preventivo deja la unidad no disponible de 00:00 a 23:59 del día programado.
        </p>
        {mants.error ? (
          <ErrorCarga titulo="No se pudieron leer los mantenimientos" error={mants.error} reintentar={mants.recargar} />
        ) : !mants.datos ? (
          <Cargando />
        ) : mants.datos.length === 0 ? (
          <p className="text-xs text-texto2">Sin mantenimientos registrados.</p>
        ) : (
          <ul className="space-y-1.5">
            {mants.datos.map((m) => (
              <li key={m.idMantenimiento} className="bg-bg border border-borde rounded px-2 py-1.5 text-xs">
                <div className="flex items-center gap-2">
                  <IconoMapaSvg forma="mantenimiento" tamano={18} />
                  <span className="text-texto font-semibold">{m.tipo === 'PREVENTIVO' ? 'Preventivo' : 'Correctivo'}</span>
                  <span className="ml-auto">
                    <Insignia texto={m.activo ? 'Vigente' : 'Cancelado'} tono={m.activo ? 'info' : 'neutro'} />
                  </span>
                </div>
                <div className="font-mono text-texto mt-0.5">
                  {formatoFechaHora(m.fechaInicio)} → {formatoFechaHora(m.fechaFin)}
                </div>
              </li>
            ))}
          </ul>
        )}
      </div>

      <div>
        <h4 className="text-[11px] uppercase tracking-wider text-texto2 font-semibold mb-1">Averías registradas</h4>
        {averias.error ? (
          <ErrorCarga titulo="No se pudieron leer las averías" error={averias.error} reintentar={averias.recargar} />
        ) : !averias.datos ? (
          <Cargando />
        ) : averias.datos.length === 0 ? (
          <p className="text-xs text-texto2">Sin averías registradas.</p>
        ) : (
          <ul className="space-y-1.5">
            {averias.datos.map((a) => (
              <li key={a.idIncidencia} className="bg-bg border border-borde rounded px-2 py-1.5 text-xs">
                <div className="flex items-center gap-2">
                  <IconoMapaSvg forma="averia" tamano={18} />
                  <span className="text-texto font-semibold">{ETIQUETA_AVERIA[a.tipoAveria] ?? a.tipoAveria}</span>
                  <span className="ml-auto">
                    <Insignia texto={a.activa ? 'Activa' : 'Resuelta'} tono={a.activa ? 'aviso' : 'ok'} />
                  </span>
                </div>
                <div className="font-mono text-texto mt-0.5">
                  {formatoFechaHora(a.fechaOcurrencia)} en {formatoCoordenada(a.ubicacionX, a.ubicacionY)}
                </div>
                <div className="text-texto2">
                  Retorno estimado:{' '}
                  <span className="font-mono text-texto">{a.horaRetornoEstimada ? formatoFechaHora(a.horaRetornoEstimada) : NO_DISPONIBLE}</span>
                </div>
              </li>
            ))}
          </ul>
        )}
      </div>
    </aside>
  );
}

// ---------------------------------------------------------------------------
// Almacenes
// ---------------------------------------------------------------------------

function VistaAlmacenes() {
  const c = useConsulta((s) => catalogoService.almacenes(s), 'mant-almacenes');
  const lista = c.datos ?? [];
  return (
    <MarcoTabla titulo="Almacenes" ruta="GET /api/almacenes" cantidad={c.datos ? `${lista.length} almacenes` : null} recargar={c.recargar} cargando={c.cargando}>
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

const ESTADOS_PEDIDO: EstadoPedido[] = ['PENDIENTE', 'EN_RUTA', 'ENTREGADO', 'REASIGNADO', 'RETRASADO'];
const ETIQUETA_PEDIDO: Record<EstadoPedido, string> = {
  PENDIENTE: 'Pendiente',
  EN_RUTA: 'En ruta',
  ENTREGADO: 'Entregado',
  REASIGNADO: 'Reasignado',
  RETRASADO: 'Fuera de plazo',
};
const TAMANIO_PAGINA = 100;

function VistaPedidos() {
  const [anio, setAnio] = useState('');
  const [mes, setMes] = useState('');
  const [estado, setEstado] = useState<EstadoPedido | ''>('');
  const [pagina, setPagina] = useState(0);

  const anioN = /^\d{4}$/.test(anio) ? Number(anio) : undefined;
  const mesN = /^\d{1,2}$/.test(mes) && Number(mes) >= 1 && Number(mes) <= 12 ? Number(mes) : undefined;
  const clave = `pedidos-${anioN}-${mesN}-${estado}-${pagina}`;
  const c = useConsulta(
    (s) => catalogoService.pedidos({ anio: anioN, mes: mesN, estado: estado || undefined, pagina, tamanio: TAMANIO_PAGINA }, s),
    clave
  );
  const lista = c.datos?.contenido ?? [];
  const totalPaginas = c.datos ? Math.max(1, Math.ceil(c.datos.total / TAMANIO_PAGINA)) : 1;

  const claseInput = 'bg-bg border border-borde rounded px-2 py-1 font-mono text-xs text-texto w-20';

  return (
    <MarcoTabla
      titulo="Pedidos"
      ruta="GET /api/pedidos"
      cantidad={c.datos ? `${formatoEntero(c.datos.total)} pedidos` : null}
      recargar={c.recargar}
      cargando={c.cargando}
      filtros={
        <div className="flex flex-wrap items-center gap-2 text-xs text-texto2">
          <label className="flex items-center gap-1">
            Año
            <input className={claseInput} value={anio} placeholder="AAAA" inputMode="numeric" onChange={(e) => { setAnio(e.target.value); setPagina(0); }} />
          </label>
          <label className="flex items-center gap-1">
            Mes
            <input className={`${claseInput} w-14`} value={mes} placeholder="MM" inputMode="numeric" onChange={(e) => { setMes(e.target.value); setPagina(0); }} />
          </label>
          <label className="flex items-center gap-1">
            Estado
            <select
              className="bg-bg border border-borde rounded px-2 py-1 text-xs text-texto"
              value={estado}
              onChange={(e) => { setEstado(e.target.value as EstadoPedido | ''); setPagina(0); }}
            >
              <option value="">Todos</option>
              {ESTADOS_PEDIDO.map((e) => (
                <option key={e} value={e}>{ETIQUETA_PEDIDO[e]}</option>
              ))}
            </select>
          </label>
        </div>
      }
    >
      <EstadoConsulta error={c.error} cargando={c.cargando} vacio={lista.length === 0} recargar={c.recargar} />
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
              {lista.map((p) => (
                <tr key={p.idPedido} className="hover:bg-panel2">
                  <Td mono>{p.idPedido}</Td>
                  <Td mono>{p.idCliente}</Td>
                  <Td mono derecha>{formatoEntero(p.cantidadQq)} paq</Td>
                  <Td mono derecha>{p.horasLimite} h</Td>
                  <Td mono>{formatoFechaHora(p.fechaLlegada)}</Td>
                  <Td mono>{p.fechaEntregaReal ? formatoFechaHora(p.fechaEntregaReal) : <span className="font-sans text-texto2">—</span>}</Td>
                  <Td mono>{formatoCoordenada(p.ubicacionX, p.ubicacionY)}</Td>
                  <Td>
                    <Insignia
                      texto={ETIQUETA_PEDIDO[p.estado] ?? p.estado}
                      tono={p.estado === 'ENTREGADO' ? 'ok' : p.estado === 'RETRASADO' ? 'critico' : 'neutro'}
                    />
                  </Td>
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
      <button
        className="border border-borde rounded px-2 py-1 text-texto disabled:text-texto2 disabled:cursor-not-allowed"
        disabled={pagina === 0}
        title={pagina === 0 ? 'Ya está en la primera página' : undefined}
        onClick={() => cambiar(pagina - 1)}
      >
        Anterior
      </button>
      <span>
        Página <span className="font-mono text-texto">{pagina + 1}</span> de <span className="font-mono text-texto">{total}</span>
      </span>
      <button
        className="border border-borde rounded px-2 py-1 text-texto disabled:text-texto2 disabled:cursor-not-allowed"
        disabled={pagina + 1 >= total}
        title={pagina + 1 >= total ? 'Ya está en la última página' : undefined}
        onClick={() => cambiar(pagina + 1)}
      >
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
  const { snapshot } = useSimulacion();
  const relojSim = snapshot?.relojSimulado?.slice(0, 16) ?? '';
  const [instante, setInstante] = useState(relojSim);
  const [estado, setEstado] = useState<EstadoBloqueo | ''>('');
  const [pagina, setPagina] = useState(0);

  const c = useConsulta((s) => catalogoService.bloqueos(instante ? `${instante}:00` : null, s), `bloqueos-${instante}`);
  const filtrados = useMemo(() => (c.datos ?? []).filter((b) => !estado || b.estado === estado), [c.datos, estado]);
  const totalPaginas = Math.max(1, Math.ceil(filtrados.length / TAMANIO_PAGINA));
  const visibles = filtrados.slice(pagina * TAMANIO_PAGINA, (pagina + 1) * TAMANIO_PAGINA);

  return (
    <MarcoTabla
      titulo="Bloqueos"
      ruta="GET /api/bloqueos"
      cantidad={c.datos ? `${formatoEntero(filtrados.length)} de ${formatoEntero(c.datos.length)}` : null}
      recargar={c.recargar}
      cargando={c.cargando}
      filtros={
        <div className="flex flex-wrap items-center gap-2 text-xs text-texto2">
          <label className="flex items-center gap-1" title="Instante en que se evalúa el estado de cada bloqueo">
            Estado evaluado en
            <input
              type="datetime-local"
              className="bg-bg border border-borde rounded px-2 py-1 font-mono text-xs text-texto"
              value={instante}
              onChange={(e) => { setInstante(e.target.value); setPagina(0); }}
            />
          </label>
          {relojSim && instante !== relojSim && (
            <button className="text-azul underline" onClick={() => { setInstante(relojSim); setPagina(0); }}>
              Usar reloj simulado
            </button>
          )}
          <label className="flex items-center gap-1">
            Estado
            <select
              className="bg-bg border border-borde rounded px-2 py-1 text-xs text-texto"
              value={estado}
              onChange={(e) => { setEstado(e.target.value as EstadoBloqueo | ''); setPagina(0); }}
            >
              <option value="">Todos</option>
              {ESTADOS_BLOQUEO.map((e) => (
                <option key={e} value={e}>{e.charAt(0) + e.slice(1).toLowerCase()}</option>
              ))}
            </select>
          </label>
        </div>
      }
    >
      {!instante && (
        <p className="px-4 pt-2 text-[11px] text-texto2">Sin instante indicado, el backend evalúa el estado con la hora de su servidor.</p>
      )}
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
                <Th>Tramo (vértices)</Th>
              </tr>
            </thead>
            <tbody>
              {visibles.map((b) => (
                <tr key={b.idIncidencia} className="hover:bg-panel2">
                  <Td mono>{b.idIncidencia}</Td>
                  <Td mono>{formatoFechaHora(b.inicio)}</Td>
                  <Td mono>{formatoFechaHora(b.fin)}</Td>
                  <Td>
                    <Insignia
                      texto={b.estado.charAt(0) + b.estado.slice(1).toLowerCase()}
                      tono={b.estado === 'ACTIVO' ? 'critico' : b.estado === 'PROGRAMADO' ? 'aviso' : 'neutro'}
                    />
                  </Td>
                  <Td mono>{b.vertices.map((v) => formatoCoordenada(v.x, v.y)).join(' → ')}</Td>
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
