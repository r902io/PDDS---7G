import { useEffect, useMemo, useRef, useState, type ReactNode } from 'react';
import type { AlmacenOperativo, SnapshotSimulacion, VehiculoOperativo, VehiculoSnapshot } from '../types/api';
import { useSimulacion } from '../estado/SimulacionContext';
import { navegar } from '../estado/navegacion';
import { catalogoService } from '../services/catalogoService';
import { useConsulta } from '../hooks/useConsulta';
import type { Seleccion } from '../hooks/useMapCanvas';
import { MapaCiudad } from '../components/mapa/MapaCiudad';
import { PanelDetalle } from '../components/mapa/PanelDetalle';
import { Boton } from '../components/ui/Boton';
import { Modal } from '../components/ui/Modal';
import { Cargando, ErrorCarga, Nota } from '../components/ui/Avisos';
import {
  IconoAlerta,
  IconoCerrar,
  IconoChevronDer,
  IconoChevronIzq,
  IconoColapso,
  IconoCorrecto,
  IconoDetener,
  IconoInfo,
  IconoMapaSvg,
} from '../components/iconos';
import {
  ESCENARIOS,
  corridaActiva,
  enColapso,
  hayCorrida,
  tipoResultado,
} from '../utilitarios/escenarios';
import {
  diaSimulado,
  formatoCoordenada,
  formatoEntero,
  formatoFechaHora,
  formatoHora,
  formatoSoles,
  horaNavegador,
  NO_DISPONIBLE,
} from '../utilitarios/formato';
import {
  COLOR_TIPO,
  ETIQUETA_TIPO_PLURAL,
  ORDEN_TIPOS,
  estaAveriado,
  estaEnMantenimiento,
  tipoDesdeCodigo,
  type TipoVehiculo,
} from '../utilitarios/vehiculos';

/** Dimensiones del enunciado; se reemplazan por las de /api/mapa cuando responde. */
const CIUDAD_ENUNCIADO = { anchoKm: 70, altoKm: 50 };

export function EscenarioDashboard() {
  const sim = useSimulacion();
  const { snapshot, datosVigentes, recibidoEn, errorEstado } = sim;
  const activa = corridaActiva(snapshot);
  const corrida = hayCorrida(snapshot);

  const angosta = typeof window !== 'undefined' && window.innerWidth < 1024;
  const [izqColapsado, setIzqColapsado] = useState(angosta);
  const [derColapsado, setDerColapsado] = useState(angosta);
  const [seleccion, setSeleccion] = useState<Seleccion>(null);
  const [confirmarDetener, setConfirmarDetener] = useState(false);

  // Datos complementarios del backend.
  const mapa = useConsulta((s) => catalogoService.mapa(s), 'mapa');
  const almacenes = useConsulta((s) => catalogoService.almacenes(s), `almacenes-${snapshot?.estado}`, activa ? 15_000 : null);
  const flota = useConsulta((s) => catalogoService.vehiculos(s), 'vehiculos');
  const reloj = useRef<string | null>(null);
  reloj.current = snapshot?.relojSimulado ?? null;
  const bloqueos = useConsulta(
    (s) => catalogoService.bloqueos(reloj.current ? reloj.current.slice(0, 19) : null, s),
    `bloqueos-${snapshot?.idSimulacion}-${snapshot?.estado}`,
    activa ? 10_000 : null
  );
  const bloqueosActivos = useMemo(
    () => (corrida ? (bloqueos.datos ?? []).filter((b) => b.estado === 'ACTIVO') : []),
    [bloqueos.datos, corrida]
  );

  // Esc cancela la selección.
  useEffect(() => {
    const alTeclear = (e: KeyboardEvent) => e.key === 'Escape' && setSeleccion(null);
    window.addEventListener('keydown', alTeclear);
    return () => window.removeEventListener('keydown', alTeclear);
  }, []);

  // Si la unidad seleccionada desaparece del snapshot, se mantiene: el panel lo explica.
  const vehiculos: VehiculoSnapshot[] = snapshot?.vehiculos ?? [];

  if (!snapshot) {
    return (
      <div className="flex-1 flex items-center justify-center p-6">
        {errorEstado ? (
          <div className="max-w-lg w-full space-y-3">
            <ErrorCarga
              titulo="No se pudo obtener el estado de la simulación"
              error={errorEstado}
              reintentar={() => window.location.reload()}
            />
            <Nota>
              No hay datos de simulación disponibles todavía. El mapa se mostrará cuando se recupere la conexión.
            </Nota>
          </div>
        ) : (
          <Cargando texto="Conectando con SisRap…" />
        )}
      </div>
    );
  }

  const dimensiones = mapa.datos ? { anchoKm: mapa.datos.anchoKm, altoKm: mapa.datos.altoKm } : CIUDAD_ENUNCIADO;

  return (
    <div className="flex-1 flex min-h-0 relative">
      {/* Panel izquierdo: parámetros */}
      <PanelLateral
        lado="izq"
        titulo="Parámetros de la corrida"
        colapsado={izqColapsado}
        alternar={() => setIzqColapsado((v) => !v)}
      >
        <PanelParametros
          snapshot={snapshot}
          flota={flota.datos}
          errorFlota={flota.error}
          recargarFlota={flota.recargar}
          almacenes={almacenes.datos}
          errorAlmacenes={almacenes.error}
          recargarAlmacenes={almacenes.recargar}
          pedirDetener={() => setConfirmarDetener(true)}
        />
      </PanelLateral>

      {/* Mapa */}
      <main className="flex-1 min-w-0 flex flex-col relative">
        <Banners
          snapshot={snapshot}
          datosVigentes={datosVigentes}
          recibidoEn={recibidoEn}
          errorMapa={mapa.error}
          errorBloqueos={bloqueos.error}
        />
        <div className="relative flex-1 min-h-0">
          <MapaCiudad
            dimensiones={dimensiones}
            almacenes={almacenes.datos ?? []}
            vehiculos={vehiculos}
            bloqueos={bloqueosActivos}
            seleccion={seleccion}
            alSeleccionar={setSeleccion}
            atenuado={corrida && !datosVigentes}
          />
          {seleccion && (
            <PanelDetalle
              seleccion={seleccion}
              vehiculos={vehiculos}
              catalogo={flota.datos}
              almacenes={almacenes.datos ?? []}
              alSeleccionar={setSeleccion}
            />
          )}
        </div>
      </main>

      {/* Panel derecho: indicadores */}
      <PanelLateral
        lado="der"
        titulo="Indicadores"
        colapsado={derColapsado}
        alternar={() => setDerColapsado((v) => !v)}
      >
        <PanelIndicadores snapshot={snapshot} bloqueosCargados={bloqueos.datos != null} />
      </PanelLateral>

      <Modal isOpen={confirmarDetener} onClose={() => setConfirmarDetener(false)} titulo="Detener la corrida">
        <p className="text-sm text-texto">
          La corrida terminará para todos los dispositivos que la están viendo. Esta acción no se puede deshacer.
        </p>
        <div className="flex justify-end gap-2">
          <Boton variant="secundario" onClick={() => setConfirmarDetener(false)}>
            Cancelar
          </Boton>
          <Boton
            variant="peligro"
            icono={<IconoDetener tamano={14} />}
            disabled={sim.accionEnCurso === 'DETENER'}
            disabledReason="Solicitud de detención en curso."
            onClick={async () => {
              await sim.detener();
              setConfirmarDetener(false);
            }}
          >
            Detener corrida
          </Boton>
        </div>
      </Modal>
    </div>
  );
}

// ---------------------------------------------------------------------------
// Estructura
// ---------------------------------------------------------------------------

function PanelLateral({
  lado,
  titulo,
  colapsado,
  alternar,
  children,
}: {
  lado: 'izq' | 'der';
  titulo: string;
  colapsado: boolean;
  alternar: () => void;
  children: ReactNode;
}) {
  const borde = lado === 'izq' ? 'border-r' : 'border-l';
  const ancho = lado === 'izq' ? 'w-[314px]' : 'w-[338px]';
  const Flecha = (lado === 'izq') !== colapsado ? IconoChevronIzq : IconoChevronDer;
  return (
    <aside
      className={`bg-panel border-borde ${borde} flex flex-col min-h-0 shrink-0 ${colapsado ? 'w-[34px]' : `${ancho} max-w-[85vw]`}`}
      aria-label={titulo}
    >
      <div className={`flex items-center px-2 py-2 border-b border-borde ${colapsado ? 'justify-center border-b-0' : 'justify-between'}`}>
        {!colapsado && <h2 className="text-xs uppercase tracking-wider text-texto2 font-semibold pl-1">{titulo}</h2>}
        <button
          onClick={alternar}
          className={`w-6 h-6 flex items-center justify-center rounded border ${colapsado ? 'border-mint text-mint' : 'border-borde text-texto2 hover:text-texto'}`}
          aria-label={colapsado ? `Mostrar ${titulo.toLowerCase()}` : `Ocultar ${titulo.toLowerCase()}`}
          aria-expanded={!colapsado}
          title={colapsado ? `Mostrar ${titulo.toLowerCase()}` : `Ocultar ${titulo.toLowerCase()}`}
        >
          <Flecha tamano={14} />
        </button>
      </div>
      {!colapsado && <div className="flex-1 overflow-y-auto p-3 space-y-3 text-xs">{children}</div>}
    </aside>
  );
}

function Bloque({ titulo, children, aclaracion }: { titulo: string; children: ReactNode; aclaracion?: string }) {
  return (
    <section className="bg-panel2 border border-borde rounded-lg p-3">
      <h3 className="text-[11px] uppercase tracking-wider text-texto2 font-semibold mb-2">{titulo}</h3>
      {aclaracion && <p className="text-[11px] text-texto2 -mt-1 mb-2 leading-snug">{aclaracion}</p>}
      {children}
    </section>
  );
}

function Fila({ k, v, mono = true, claseValor = '' }: { k: ReactNode; v: ReactNode; mono?: boolean; claseValor?: string }) {
  const noDisp = v === NO_DISPONIBLE;
  return (
    <div className="flex justify-between items-baseline gap-3 py-0.5">
      <span className="text-texto2">{k}</span>
      <span className={`${mono && !noDisp ? 'font-mono' : ''} ${noDisp ? 'text-texto2 italic' : 'text-texto'} text-[13px] text-right ${claseValor}`}>
        {v}
      </span>
    </div>
  );
}

// ---------------------------------------------------------------------------
// Banners sobre el mapa
// ---------------------------------------------------------------------------

function Banners({
  snapshot,
  datosVigentes,
  recibidoEn,
  errorMapa,
  errorBloqueos,
}: {
  snapshot: SnapshotSimulacion;
  datosVigentes: boolean;
  recibidoEn: number | null;
  errorMapa: string | null;
  errorBloqueos: string | null;
}) {
  const { aviso, cerrarAviso } = useSimulacion();
  const resultado = tipoResultado(snapshot);
  const corrida = hayCorrida(snapshot);

  return (
    <div className="shrink-0 flex flex-col">
      {aviso && (
        <div
          role="status"
          className={`flex items-start gap-2 px-4 py-2 text-xs border-b ${
            aviso.tipo === 'error' ? 'bg-rojo/10 border-rojo/60 text-texto' : 'bg-azul/10 border-azul/60 text-texto'
          }`}
        >
          <span className={aviso.tipo === 'error' ? 'text-rojo' : 'text-azul'}>
            <IconoInfo tamano={16} />
          </span>
          <p className="flex-1">{aviso.texto}</p>
          <button onClick={cerrarAviso} aria-label="Cerrar aviso" className="text-texto2 hover:text-texto">
            <IconoCerrar tamano={14} />
          </button>
        </div>
      )}

      {corrida && !datosVigentes && (
        <div role="alert" className="flex items-center gap-2 px-4 py-2 text-xs bg-ambar/10 border-b border-ambar text-texto">
          <span className="text-ambar">
            <IconoAlerta tamano={16} />
          </span>
          <p>
            <b className="text-ambar">Conexión en tiempo real perdida.</b> Se muestra el último estado recibido a las{' '}
            <span className="font-mono">{horaNavegador(recibidoEn)}</span> (hora de este dispositivo); puede no estar
            vigente. Reconectando automáticamente…
          </p>
        </div>
      )}

      {!corrida && (
        <div className="flex flex-wrap items-center gap-3 px-4 py-2 text-xs bg-panel2 border-b border-borde text-texto">
          <span className="text-texto2">
            <IconoInfo tamano={16} />
          </span>
          <p className="flex-1">No hay una corrida en curso. El mapa muestra la ciudad y los almacenes.</p>
          <Boton variant="secundario" className="text-xs py-1" onClick={() => navegar({ nombre: 'selector' })}>
            Elegir escenario
          </Boton>
        </div>
      )}

      {resultado && <BannerResultado tipo={resultado} snapshot={snapshot} />}

      {(errorMapa || errorBloqueos) && (
        <div className="px-4 py-1.5 text-[11px] bg-panel border-b border-borde text-texto2 flex flex-col gap-0.5">
          {errorMapa && (
            <span>
              <b className="text-ambar">No se pudo cargar el mapa:</b> se muestra temporalmente la retícula de 70 × 50 km. {errorMapa}
            </span>
          )}
          {errorBloqueos && (
            <span>
              <b className="text-ambar">Bloqueos no disponibles:</b> {errorBloqueos}
            </span>
          )}
        </div>
      )}
    </div>
  );
}

function BannerResultado({ tipo, snapshot }: { tipo: NonNullable<ReturnType<typeof tipoResultado>>; snapshot: SnapshotSimulacion }) {
  const conf = {
    COLAPSO: {
      clase: 'bg-rojo/15 border-rojo text-rojo',
      icono: <IconoColapso tamano={18} />,
      titulo: 'COLAPSO LOGÍSTICO',
      texto: 'Un pedido no se entregó dentro de su plazo.',
    },
    COMPLETADA: {
      clase: 'bg-mint/10 border-mint text-mint',
      icono: <IconoCorrecto tamano={18} />,
      titulo: 'CORRIDA COMPLETADA',
      texto: 'El escenario alcanzó su condición de término sin incumplimientos.',
    },
    ERROR: {
      clase: 'bg-ambar/10 border-ambar text-ambar',
      icono: <IconoAlerta tamano={18} />,
      titulo: 'DETENIDA POR ERROR O FALTA DE DATOS',
      texto: snapshot.mensaje ?? 'No se informó la causa.',
    },
    DETENIDA_MANUAL: {
      clase: 'bg-panel2 border-texto2 text-texto',
      icono: <IconoDetener tamano={16} />,
      titulo: 'CORRIDA DETENIDA',
      texto: 'La sesión controladora detuvo la corrida.',
    },
  }[tipo];
  return (
    <div className={`flex flex-wrap items-center gap-3 px-4 py-2 border-b ${conf.clase}`} role="status">
      {conf.icono}
      <p className="text-sm font-bold tracking-wide">{conf.titulo}</p>
      <p className="text-xs text-texto flex-1 min-w-[200px]">
        {conf.texto} Reloj al detenerse: <span className="font-mono">{formatoFechaHora(snapshot.relojSimulado)}</span>.
      </p>
      <Boton variant="secundario" className="text-xs py-1" onClick={() => navegar({ nombre: 'resultado' })}>
        Ver resultado
      </Boton>
    </div>
  );
}

// ---------------------------------------------------------------------------
// Panel izquierdo
// ---------------------------------------------------------------------------

function PanelParametros({
  snapshot,
  flota,
  errorFlota,
  recargarFlota,
  almacenes,
  errorAlmacenes,
  recargarAlmacenes,
  pedirDetener,
}: {
  snapshot: SnapshotSimulacion;
  flota: VehiculoOperativo[] | null;
  errorFlota: string | null;
  recargarFlota: () => void;
  almacenes: AlmacenOperativo[] | null;
  errorAlmacenes: string | null;
  recargarAlmacenes: () => void;
  pedirDetener: () => void;
}) {
  const { rol, accionEnCurso } = useSimulacion();
  const activa = corridaActiva(snapshot);
  const esc = snapshot.escenario ? ESCENARIOS[snapshot.escenario] : null;

  return (
    <>
      <Bloque titulo="Control">
        {rol === 'CONTROLADOR' ? (
          <div className="space-y-2">
            <p className="text-texto2 leading-snug">Esta sesión inició la corrida y es la única que puede detenerla.</p>
            <Boton
              variant="peligro"
              icono={<IconoDetener tamano={14} />}
              onClick={pedirDetener}
              disabled={accionEnCurso === 'DETENER'}
              disabledReason="Solicitud de detención en curso."
              className="w-full"
            >
              Detener corrida
            </Boton>
          </div>
        ) : rol === 'OBSERVADOR' ? (
          <p className="text-texto2 leading-snug">
            Modo <b className="text-azul">observador</b>: otra sesión controla esta corrida. Usted ve el mismo estado en
            tiempo real, sin controles.
          </p>
        ) : (
          <p className="text-texto2 leading-snug">
            No hay una corrida activa. Puede iniciar una desde{' '}
            <button className="text-azul underline" onClick={() => navegar({ nombre: 'selector' })}>
              el selector de escenario
            </button>
            .
          </p>
        )}
        {snapshot.estado === 'PAUSADA' && (
          <Nota tono="aviso" className="mt-2">
            La corrida está pausada. Esta pantalla se mantiene en modo de consulta.
          </Nota>
        )}
      </Bloque>

      <Bloque titulo="Corrida">
        <Fila k="Escenario" v={esc ? esc.titulo : NO_DISPONIBLE} mono={false} />
        <Fila k="Término" v={esc ? esc.condicionTermino : NO_DISPONIBLE} mono={false} claseValor="max-w-[170px]" />
        <Fila k="Inicio" v={formatoFechaHora(snapshot.fechaHoraInicio)} />
        <Fila
          k="Fin"
          v={
            snapshot.escenario === 'SIMULACION_CINCO_DIAS'
              ? formatoFechaHora(snapshot.fechaHoraFin)
              : snapshot.escenario
                ? 'Sin fecha de fin conocida'
                : NO_DISPONIBLE
          }
          mono={snapshot.escenario === 'SIMULACION_CINCO_DIAS'}
        />
        {snapshot.mensaje && (
          <p className="mt-2 pt-2 border-t border-borde text-[11px] text-texto2 leading-snug">
            <span className="text-texto">Detalle:</span> {snapshot.mensaje}
          </p>
        )}
      </Bloque>

      <Bloque
        titulo="Composición de la flota"
        aclaracion={activa ? 'Solo lectura durante la corrida.' : 'Estado actual de la flota.'}
      >
        {errorFlota && !flota ? (
          <ErrorCarga titulo="No se pudo leer la flota" error={errorFlota} reintentar={recargarFlota} />
        ) : !flota ? (
          <Cargando />
        ) : (
          <table className="w-full text-xs">
            <thead>
              <tr className="text-texto2 text-right">
                <th className="text-left font-semibold pb-1">Tipo</th>
                <th className="font-semibold pb-1">Cant.</th>
                <th className="font-semibold pb-1">Paq.</th>
                <th className="font-semibold pb-1">km/h</th>
                <th className="font-semibold pb-1">S/ km</th>
              </tr>
            </thead>
            <tbody>
              {ORDEN_TIPOS.map((t) => {
                const lista = flota.filter((v) => tipoDesdeCodigo(v.idVehiculo) === t);
                const m = lista[0];
                return (
                  <tr key={t} className="text-right">
                    <td className="text-left py-0.5">
                      <span className="inline-flex items-center gap-1 text-texto">
                        <IconoMapaSvg forma={t === 'AUTO' ? 'auto' : t === 'MOTO' ? 'moto' : 'bicicleta'} color={COLOR_TIPO[t]} tamano={18} />
                        {ETIQUETA_TIPO_PLURAL[t]}
                      </span>
                    </td>
                    <td className="font-mono text-texto">{lista.length}</td>
                    <td className="font-mono text-texto">{m ? m.capacidadPaquetes : '—'}</td>
                    <td className="font-mono text-texto">{m ? m.velocidadKmh : '—'}</td>
                    <td className="font-mono text-texto">{m ? formatoSoles(m.costoPorKm).replace('S/ ', '') : '—'}</td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        )}
      </Bloque>

      <Bloque titulo="Almacenes">
        {errorAlmacenes && !almacenes ? (
          <ErrorCarga titulo="No se pudieron leer los almacenes" error={errorAlmacenes} reintentar={recargarAlmacenes} />
        ) : !almacenes ? (
          <Cargando />
        ) : (
          almacenes.map((a) => (
            <Fila
              key={a.idAlmacen}
              k={
                <span>
                  {a.nombre} <span className="font-mono">{formatoCoordenada(a.ubicacionX, a.ubicacionY)}</span>
                </span>
              }
              v={a.stockActual == null ? 'Stock ilimitado' : `${formatoEntero(a.stockActual)} / ${formatoEntero(a.capacidadMaxima)}`}
              mono={a.stockActual != null}
            />
          ))
        )}
      </Bloque>
    </>
  );
}

// ---------------------------------------------------------------------------
// Panel derecho
// ---------------------------------------------------------------------------

function PanelIndicadores({ snapshot, bloqueosCargados }: { snapshot: SnapshotSimulacion; bloqueosCargados: boolean }) {
  const p = snapshot.pedidos;
  const colapso = enColapso(snapshot);
  const corrida = hayCorrida(snapshot);
  const dia = diaSimulado(snapshot.fechaHoraInicio, snapshot.relojSimulado);

  const porTipo = ORDEN_TIPOS.map((t) => {
    const lista = snapshot.vehiculos.filter((v) => tipoDesdeCodigo(v.idVehiculo) === t);
    return {
      tipo: t,
      total: lista.length,
      enRuta: lista.filter((v) => v.estado === 'EN_RUTA' || v.estado === 'RETORNANDO_ALMACEN').length,
      averiadas: lista.filter((v) => estaAveriado(v.estado)).length,
      mantenimiento: lista.filter((v) => estaEnMantenimiento(v.estado)).length,
    };
  });

  return (
    <>
      <section
        className={`rounded-lg border p-3 ${colapso ? 'border-rojo bg-rojo/10' : 'border-borde bg-panel2'}`}
        aria-live="polite"
      >
        <h3 className="text-[11px] uppercase tracking-wider text-texto2 font-semibold mb-1">Situación</h3>
        {!corrida ? (
          <p className="text-texto2">Sin corrida.</p>
        ) : colapso ? (
          <div className="flex items-start gap-2 text-rojo">
            <IconoColapso tamano={22} />
            <div>
              <p className="font-bold text-sm">Colapso logístico</p>
              <p className="text-texto text-xs">
                <span className="font-mono">{p.retrasados}</span> pedido(s) fuera de plazo. El primer incumplimiento
                produce el colapso.
              </p>
            </div>
          </div>
        ) : (
          <div className="flex items-start gap-2 text-mint">
            <IconoCorrecto tamano={22} />
            <div>
              <p className="font-bold text-sm">Sin incumplimientos</p>
              <p className="text-texto text-xs">
                Ningún pedido ha vencido su plazo{dia != null ? <> hasta el día <span className="font-mono">{dia}</span>, <span className="font-mono">{formatoHora(snapshot.relojSimulado)}</span></> : null}.
              </p>
            </div>
          </div>
        )}
      </section>

      <Bloque titulo="Pedidos del periodo" aclaracion="Resumen actualizado de la corrida.">
        <div className="grid grid-cols-2 gap-2 mb-2">
          <Cifra etiqueta="Entregados" valor={p.entregados} clase="text-mint" />
          <Cifra etiqueta="En ruta" valor={p.enRuta} clase="text-azul" />
        </div>
        <Fila k="Pendientes" v={formatoEntero(p.pendientes)} />
        <Fila k="Reasignados" v={formatoEntero(p.reasignados)} />
        <Fila k="Futuros (aún no llegan)" v={formatoEntero(p.futuros)} />
        <Fila
          k="Fuera de plazo"
          v={formatoEntero(p.retrasados)}
          claseValor={p.retrasados > 0 ? '!text-rojo font-bold' : ''}
        />
        <div className="border-t border-borde mt-1.5 pt-1.5">
          <Fila k="Total del periodo" v={formatoEntero(p.total)} />
        </div>
      </Bloque>

      <Bloque titulo="Holgura mínima">
        <Fila k="Holgura mínima" v={NO_DISPONIBLE} />
        <Fila k="Pedido crítico" v={NO_DISPONIBLE} />
      </Bloque>

      <Bloque titulo="Flota" aclaracion="Estados informados en el snapshot.">
        <table className="w-full text-xs">
          <thead>
            <tr className="text-texto2 text-right">
              <th className="text-left font-semibold pb-1">Tipo</th>
              <th className="font-semibold pb-1">En ruta</th>
              <th className="font-semibold pb-1" title="Averiadas">Aver.</th>
              <th className="font-semibold pb-1" title="En mantenimiento">Mant.</th>
            </tr>
          </thead>
          <tbody>
            {porTipo.map((f) => (
              <tr key={f.tipo} className="text-right">
                <td className="text-left py-0.5 text-texto">{ETIQUETA_TIPO_PLURAL[f.tipo as TipoVehiculo]}</td>
                <td className="font-mono text-texto">
                  {f.enRuta} / {f.total}
                </td>
                <td className={`font-mono ${f.averiadas ? 'text-ambar' : 'text-texto'}`}>{f.averiadas}</td>
                <td className={`font-mono ${f.mantenimiento ? 'text-[#38bdf8]' : 'text-texto'}`}>{f.mantenimiento}</td>
              </tr>
            ))}
          </tbody>
        </table>
        <div className="border-t border-borde mt-2 pt-1.5">
          <Fila k="Distancia recorrida" v={NO_DISPONIBLE} />
          <Fila k="Costo acumulado" v={NO_DISPONIBLE} />
        </div>
      </Bloque>

      <Bloque titulo="Incidencias">
        <Fila k="Bloqueos activos" v={formatoEntero(snapshot.bloqueosActivos)} />
        {!bloqueosCargados && corrida && <p className="text-[11px] text-texto2">Cargando el trazado de los bloqueos…</p>}
        <Nota className="mt-1.5">
          Los bloqueos son programados desde el archivo mensual. El registro de averías tipo 1, 2 y 3 aún no está
          integrado en este visualizador.
        </Nota>
      </Bloque>
    </>
  );
}

function Cifra({ etiqueta, valor, clase }: { etiqueta: string; valor: number; clase: string }) {
  return (
    <div className="bg-bg border border-borde rounded-md px-2 py-1.5">
      <div className="text-[11px] text-texto2">{etiqueta}</div>
      <div className={`font-mono text-2xl font-bold ${clase}`}>{formatoEntero(valor)}</div>
    </div>
  );
}
