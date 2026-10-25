import { useEffect, useState, type ReactElement } from 'react';
import type { ConfiguracionOperativa, EscenarioSimulacion, CargaHistorica, TipoFlota } from '../types/api';
import { useSimulacion } from '../estado/SimulacionContext';
import { navegar } from '../estado/navegacion';
import { apiFetch, textoError } from '../services/apiClient';
import { useConsulta } from '../hooks/useConsulta';
import { Boton } from '../components/ui/Boton';
import { Campo } from '../components/ui/Campo';
import { Panel } from '../components/ui/Panel';
import { Cargando, ErrorCarga } from '../components/ui/Avisos';
import { IconoCalendario, IconoColapso, IconoCorrecto, IconoIniciar, IconoReloj, IconoOjo } from '../components/iconos';
import { ESCENARIOS, ORDEN_ESCENARIOS, corridaActiva } from '../utilitarios/escenarios';
import { diaSimulado, formatoFecha, formatoHora, hoyLocal } from '../utilitarios/formato';
import { HistorialPedidos } from '../components/HistorialPedidos';
import { CalendariosCaso } from '../components/CalendariosCaso';

const ICONO_ESCENARIO: Record<EscenarioSimulacion, ReactElement> = {
  OPERACION_DIARIA: <IconoReloj tamano={24} />,
  SIMULACION_CINCO_DIAS: <IconoCalendario tamano={24} />,
  COLAPSO_LOGISTICO: <IconoColapso tamano={24} />,
};

const COLOR_ESCENARIO: Record<EscenarioSimulacion, string> = {
  OPERACION_DIARIA: 'text-mint bg-mint/10',
  SIMULACION_CINCO_DIAS: 'text-azul bg-azul/10',
  COLAPSO_LOGISTICO: 'text-rojo bg-rojo/10',
};

const ORDEN_FLOTA: TipoFlota[] = ['AUTO', 'MOTO', 'BICICLETA'];
const NOMBRES_FLOTA: Record<TipoFlota, string> = {
  AUTO: 'autos',
  MOTO: 'motos',
  BICICLETA: 'bicicletas',
};

type PlantillaEscenario = ConfiguracionOperativa & {
  escenario: EscenarioSimulacion;
  anchoKm: number;
  altoKm: number;
};
type Ediciones = Record<string, string>;

const inputNumero = 'bg-bg border border-borde rounded text-texto font-mono text-xs text-center py-1 px-1 focus:outline-none focus:border-mint focus:ring-1 focus:ring-mint disabled:opacity-60';

function numero(valor: string): number {
  return valor.trim() === '' ? NaN : Number(valor);
}

function validar(c: ConfiguracionOperativa, ancho?: number, alto?: number): string | null {
  if (c.almacenes.length !== 3 || c.flota.length !== 3) return 'Se requieren tres almacenes y tres tipos de vehículo.';
  if (c.almacenes.filter(a => a.tipo === 'CENTRAL').length !== 1) return 'Se requiere exactamente un almacén central.';
  const posiciones = new Set<string>();
  for (const a of c.almacenes) {
    const posicion = `${a.ubicacionX},${a.ubicacionY}`;
    if (posiciones.has(posicion)) return 'Los almacenes deben tener ubicaciones diferentes.';
    posiciones.add(posicion);
    if (!Number.isInteger(a.ubicacionX) || !Number.isInteger(a.ubicacionY) || a.ubicacionX < 0 || a.ubicacionY < 0 ||
      (ancho !== undefined && a.ubicacionX > ancho) || (alto !== undefined && a.ubicacionY > alto)) {
      return `Comprueba las coordenadas de ${a.nombre}.`;
    }
    if (a.tipo === 'CENTRAL' && a.capacidadMaxima !== null) return 'El almacén central debe tener inventario ilimitado.';
    if (a.tipo === 'INTERMEDIO' && (!Number.isInteger(a.capacidadMaxima) || (a.capacidadMaxima ?? 0) <= 0)) {
      return `La capacidad de ${a.nombre} debe ser un entero positivo.`;
    }
  }
  for (const f of c.flota) {
    if (!Number.isInteger(f.cantidad) || f.cantidad < 0 || f.cantidad > 200) return `Revisa la cantidad de ${NOMBRES_FLOTA[f.tipo]}.`;
    if (!Number.isInteger(f.capacidadPaquetes) || f.capacidadPaquetes <= 0) return `Revisa la capacidad de ${NOMBRES_FLOTA[f.tipo]}.`;
    if (!Number.isFinite(f.velocidadKmh) || f.velocidadKmh <= 0 || f.velocidadKmh > 999.99) return `Revisa la velocidad de ${NOMBRES_FLOTA[f.tipo]}.`;
    if (!Number.isFinite(f.costoPorKm) || f.costoPorKm < 0 || f.costoPorKm > 9999.99) return `Revisa el costo de ${NOMBRES_FLOTA[f.tipo]}.`;
  }
  if (c.flota.reduce((total, f) => total + f.cantidad, 0) === 0) return 'Debes configurar al menos un vehículo.';
  return null;
}

interface EntradaNumeroProps {
  etiqueta: string;
  valor: string;
  cambiar: (texto: string) => void;
  disabled: boolean;
  ancho?: string;
  minimo?: number;
  maximo?: number;
  paso?: number;
}

function EntradaNumero({ etiqueta, valor, cambiar, disabled, ancho = 'w-12', minimo = 0, maximo, paso = 1 }: EntradaNumeroProps) {
  return (
    <input
      type="number"
      aria-label={etiqueta}
      title={etiqueta}
      value={valor}
      onChange={e => cambiar(e.target.value)}
      onFocus={e => e.currentTarget.select()}
      min={minimo}
      max={maximo}
      step={paso}
      disabled={disabled}
      className={`${inputNumero} ${ancho}`}
    />
  );
}

export function SelectorEscenario() {
  const { snapshot, sesion, iniciar, accionEnCurso, reintentarSesion } = useSimulacion();
  const [escenario, setEscenario] = useState<EscenarioSimulacion>('OPERACION_DIARIA');
  const [fechaInicio, setFechaInicio] = useState(hoyLocal());
  const [horaInicio, setHoraInicio] = useState('00:00');
  const [errorInicio, setErrorInicio] = useState<string | null>(null);
  const [errorGuardar, setErrorGuardar] = useState<string | null>(null);
  const [guardando, setGuardando] = useState(false);
  const [guardadas, setGuardadas] = useState<Partial<Record<EscenarioSimulacion, PlantillaEscenario>>>({});
  const [edicionesPorEscenario, setEdicionesPorEscenario] = useState<Partial<Record<EscenarioSimulacion, Ediciones>>>({});
  const [calendarioOcupado, setCalendarioOcupado] = useState(false);
  const [historicosOcupados, setHistoricosOcupados] = useState(false);
  const ediciones = edicionesPorEscenario[escenario] ?? {};
  const setEdiciones = (valor: Ediciones | ((prev: Ediciones) => Ediciones)) => {
    setEdicionesPorEscenario(prev => ({ ...prev,
      [escenario]: typeof valor === 'function' ? valor(prev[escenario] ?? {}) : valor,
    }));
  };
  const [mensajeGuardado, setMensajeGuardado] = useState(false);

  const consulta = useConsulta(s => apiFetch<PlantillaEscenario>(
    `/api/configuracion-operativa/escenarios/${escenario}`, { autenticar: false, senal: s }), `plantilla-${escenario}`);
  useEffect(() => {
    if (consulta.datos) {
      const recibida = consulta.datos;
      setGuardadas(prev => ({ ...prev, [recibida.escenario]: recibida }));
    }
  }, [consulta.datos]);
  const consultaHistoricos = useConsulta(async s => ({
    escenario,
    cargas: await apiFetch<CargaHistorica[]>(`/api/pedidos/cargas-historicas?escenario=${escenario}`,
      { autenticar: false, senal: s }),
  }), `historicos-${escenario}`);
  const historicosDelEscenario = consultaHistoricos.datos?.escenario === escenario;
  const historicos = {
    ...consultaHistoricos,
    datos: historicosDelEscenario ? consultaHistoricos.datos!.cargas : null,
    cargando: consultaHistoricos.cargando || !historicosDelEscenario,
  };

  const activa = corridaActiva(snapshot);
  const configuracionBackend = guardadas[escenario] ?? (consulta.datos?.escenario === escenario ? consulta.datos : null);
  const base = configuracionBackend;
  const editables = Boolean(configuracionBackend) && !activa && !guardando && !calendarioOcupado && !historicosOcupados && !accionEnCurso && !consulta.cargando && !consulta.error;
  const pendientes = Object.keys(ediciones).length > 0;

  const leer = (clave: string, valor: number) => ediciones[clave] ?? String(valor);
  const cambiar = (clave: string, valor: string) => {
    setEdiciones(prev => ({ ...prev, [clave]: valor }));
    setMensajeGuardado(false);
    setErrorGuardar(null);
  };
  const anchoKm = numero(leer('mapa.ancho', configuracionBackend?.anchoKm ?? 70));
  const altoKm = numero(leer('mapa.alto', configuracionBackend?.altoKm ?? 50));
  const dimensionesValidas = Number.isInteger(anchoKm) && Number.isInteger(altoKm)
    && anchoKm >= 1 && anchoKm <= 150 && altoKm >= 1 && altoKm <= 150;
  const borrador: PlantillaEscenario | null = configuracionBackend ? {
    escenario, anchoKm, altoKm,
    almacenes: configuracionBackend.almacenes.map(a => ({
      ...a,
      ubicacionX: numero(leer(`a.${a.idAlmacen}.x`, a.ubicacionX)),
      ubicacionY: numero(leer(`a.${a.idAlmacen}.y`, a.ubicacionY)),
      capacidadMaxima: a.tipo === 'CENTRAL' ? null : numero(leer(`a.${a.idAlmacen}.stock`, a.capacidadMaxima ?? 0)),
    })),
    flota: configuracionBackend.flota.map(f => ({
      ...f,
      cantidad: numero(leer(`f.${f.tipo}.cantidad`, f.cantidad)),
      capacidadPaquetes: numero(leer(`f.${f.tipo}.capacidad`, f.capacidadPaquetes)),
      velocidadKmh: numero(leer(`f.${f.tipo}.velocidad`, f.velocidadKmh)),
      costoPorKm: numero(leer(`f.${f.tipo}.costo`, f.costoPorKm)),
    })),
  } : null;
  const errorValidacion = !dimensionesValidas ? 'El ancho y alto deben ser enteros entre 1 y 150 km.'
    : borrador ? validar(borrador, anchoKm, altoKm) : null;

  async function guardar() {
    if (!borrador || !pendientes || errorValidacion || !editables) return;
    setGuardando(true);
    setErrorGuardar(null);
    setMensajeGuardado(false);
    try {
      const respuesta = await apiFetch<PlantillaEscenario>(`/api/configuracion-operativa/escenarios/${escenario}`,
        { metodo: 'PUT', cuerpo: borrador });
      setGuardadas(prev => ({ ...prev, [escenario]: respuesta }));
      setEdiciones({});
      setMensajeGuardado(true);
    } catch (e) {
      setErrorGuardar(textoError(e));
    } finally {
      setGuardando(false);
    }
  }

  const fechaValida = /^\d{4}-\d{2}-\d{2}$/.test(fechaInicio);
  const horaValida = /^([01]\d|2[0-3]):[0-5]\d$/.test(horaInicio);
  const motivoBloqueo =
    sesion.fase === 'CARGANDO' ? 'Preparando la sesión.' :
    sesion.fase === 'ERROR' ? 'No hay una sesión disponible para iniciar.' :
    accionEnCurso === 'INICIAR' ? 'Solicitud de inicio en curso.' :
    activa ? 'Existe otra corrida en curso.' :
    guardando || calendarioOcupado || historicosOcupados ? 'Guardando los datos del caso.' :
    !configuracionBackend || consulta.cargando ? 'Cargando la configuración del escenario.' :
    consulta.error ? 'No se pudo verificar la configuración del escenario.' :
    pendientes ? 'Guarda los cambios del mapa, almacenes y flota antes de iniciar.' :
    errorValidacion ??
    (historicos.error ? 'No se pudo verificar la carga histórica del escenario.' :
    historicos.cargando || !historicos.datos ? 'Consultando los históricos del escenario.' :
    historicos.datos.length === 0 ? 'Carga al menos un mes histórico en Datos del caso.' :
    !fechaValida ? 'Indica una fecha de inicio válida.' :
    !horaValida ? 'Indica una hora de inicio válida.' : undefined);

  async function alIniciar() {
    if (motivoBloqueo) return;
    setErrorInicio(null);
    const semilla = crypto.getRandomValues(new Uint32Array(1))[0] & 0x7fffffff;
    const resultado = await iniciar(escenario, fechaInicio, semilla, horaInicio);
    if (resultado.ok || resultado.corridaEnCurso) navegar({ nombre: 'operacion' });
    else setErrorInicio(resultado.mensaje);
  }

  return (
    <div className="flex-1 overflow-y-auto">
      <div className="max-w-6xl mx-auto w-full px-4 sm:px-8 py-6 space-y-6">
        <div>
          <h1 className="text-2xl font-semibold text-texto">Seleccione el escenario de operación</h1>
          <p className="text-sm text-texto2 mt-1 max-w-3xl leading-relaxed">
            Solo puede ejecutarse una corrida a la vez. Quien la inicia puede detenerla; los demás usuarios pueden
            seguir su avance en tiempo real como observadores.
          </p>
        </div>

        {activa && snapshot?.escenario && (
          <div className="flex flex-wrap items-center gap-3 border border-azul/60 bg-azul/10 rounded-lg px-4 py-3">
            <span className="text-azul"><IconoOjo tamano={20} /></span>
            <div className="text-sm flex-1 min-w-[240px]">
              <p className="text-texto font-semibold">Hay una corrida en curso: {ESCENARIOS[snapshot.escenario].titulo}</p>
              <p className="text-texto2">
                Día {diaSimulado(snapshot.fechaHoraInicio, snapshot.relojSimulado) ?? '—'} ·{' '}
                <span className="font-mono">{formatoHora(snapshot.relojSimulado)}</span> ·{' '}
                <span className="font-mono">{formatoFecha(snapshot.relojSimulado)}</span>.
                No se puede iniciar otra hasta que termine o la detenga su controlador.
              </p>
            </div>
            <Boton variant="secundario" onClick={() => navegar({ nombre: 'operacion' })}>Ver la corrida</Boton>
          </div>
        )}

        {sesion.fase === 'ERROR' && <ErrorCarga titulo="No se pudo preparar la sesión" error={sesion.error} reintentar={reintentarSesion} />}

        <section>
          <h2 className="text-xs uppercase tracking-wider text-texto2 font-semibold border-b border-borde pb-1.5 mb-3">1 · Escenario</h2>
          <div className="grid grid-cols-1 md:grid-cols-3 gap-4" role="radiogroup" aria-label="Escenario">
            {ORDEN_ESCENARIOS.map(id => {
              const e = ESCENARIOS[id];
              const elegido = escenario === id;
              return (
                <button key={id} role="radio" aria-checked={elegido} disabled={guardando || calendarioOcupado || historicosOcupados || !!accionEnCurso}
                  onClick={() => { setEscenario(id); setMensajeGuardado(false); setErrorGuardar(null); setErrorInicio(null); }}
                  className={`text-left p-5 rounded-xl border transition-colors flex flex-col gap-2 ${
                    elegido ? 'bg-panel2 border-mint ring-1 ring-mint' : 'bg-panel border-borde hover:border-texto2'
                  }`}>
                  <div className="flex items-start justify-between">
                    <span className={`p-2 rounded ${COLOR_ESCENARIO[id]}`}>{ICONO_ESCENARIO[id]}</span>
                    {elegido && <span className="text-mint flex items-center gap-1 text-xs"><IconoCorrecto tamano={18} /> Elegido</span>}
                  </div>
                  <h3 className="text-base font-semibold text-texto">{e.titulo}</h3>
                  <p className="text-[13px] text-texto2 leading-relaxed flex-1">{e.resumen}</p>
                  <div className="border-t border-borde pt-2 text-xs text-texto2">
                    <span className="text-texto font-semibold">Condición de término: </span>{e.condicionTermino}
                  </div>
                </button>
              );
            })}
          </div>
        </section>

        <section className="grid grid-cols-1 lg:grid-cols-[minmax(280px,0.8fr)_minmax(0,1.2fr)] items-start gap-4">
          <Panel className="h-[320px] sm:h-[300px] overflow-y-auto lg:sticky lg:top-4" titulo="2 · Inicio de la corrida" subtitulo="Indica la fecha y hora desde las que comenzará la simulación.">
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
              <Campo label="Fecha de inicio" type="date" value={fechaInicio}
                onChange={e => setFechaInicio(e.target.value)} disabled={activa || !!accionEnCurso} required />
              <Campo label="Hora de inicio" type="time" value={horaInicio} step={60}
                onChange={e => setHoraInicio(e.target.value)} disabled={activa || !!accionEnCurso} required />
            </div>
            <div className="mt-4 flex flex-col items-start gap-2">
              <Boton variant="primario" icono={<IconoIniciar tamano={16} />} onClick={alIniciar}
                disabled={motivoBloqueo != null} disabledReason={motivoBloqueo}
                motivoVisible className="px-6 py-2.5 text-base">
                {accionEnCurso === 'INICIAR' ? 'Iniciando…' : 'Iniciar corrida'}
              </Boton>
              {errorInicio && <ErrorCarga titulo="No se pudo iniciar la corrida" error={errorInicio} className="w-full" />}
            </div>
          </Panel>

          <Panel key={escenario} titulo="Datos del caso" subtitulo={`Configuración de ${ESCENARIOS[escenario].titulo.toLowerCase()}.`}>
            <div className="space-y-4 text-xs">
              <div className="rounded-lg border border-borde bg-panel p-3 space-y-3">
                <h4 className="text-sm font-semibold text-texto">Mapa de la ciudad</h4>
                <div className="flex flex-wrap items-center gap-4">
                  <label className="flex items-center gap-2 text-texto2">Ancho (km)
                    <EntradaNumero etiqueta="Ancho del mapa en kilómetros" valor={leer('mapa.ancho', base?.anchoKm ?? 70)}
                      cambiar={v => cambiar('mapa.ancho', v)} disabled={!editables} minimo={1} maximo={150} ancho="w-20" />
                  </label>
                  <label className="flex items-center gap-2 text-texto2">Alto (km)
                    <EntradaNumero etiqueta="Alto del mapa en kilómetros" valor={leer('mapa.alto', base?.altoKm ?? 50)}
                      cambiar={v => cambiar('mapa.alto', v)} disabled={!editables} minimo={1} maximo={150} ancho="w-20" />
                  </label>
                </div>
                {borrador && dimensionesValidas && <svg viewBox={`-5 -5 ${anchoKm + 10} ${altoKm + 10}`}
                  role="img" aria-label="Vista previa de las dimensiones y almacenes" className="h-40 w-full rounded bg-bg">
                  <defs><pattern id="reticula-configuracion" width="5" height="5" patternUnits="userSpaceOnUse">
                    <path d="M 5 0 L 0 0 0 5" fill="none" stroke="currentColor" strokeWidth="0.15" className="text-borde" />
                  </pattern></defs>
                  <rect width={anchoKm} height={altoKm} fill="url(#reticula-configuracion)" stroke="currentColor" strokeWidth="0.3" className="text-texto2" />
                  {borrador.almacenes.filter(a => Number.isFinite(a.ubicacionX) && Number.isFinite(a.ubicacionY)).map(a =>
                    <circle key={a.idAlmacen} cx={a.ubicacionX} cy={altoKm - a.ubicacionY} r="1.3"
                      fill="currentColor" className={a.tipo === 'CENTRAL' ? 'text-mint' : 'text-azul'}>
                      <title>{a.nombre}: ({a.ubicacionX}, {a.ubicacionY})</title>
                    </circle>)}
                </svg>}
                <p className="text-texto2">Coordenadas enteras: X de 0 a {dimensionesValidas ? anchoKm : 'ancho'} e Y de 0 a {dimensionesValidas ? altoKm : 'alto'}.
                  Los almacenes, destinos históricos y bloqueos deben quedar dentro del mapa.</p>
              </div>
              <div>
                <p className="text-texto2 mb-1">Almacenes</p>
                {!base ? <Cargando /> : (
                  <ul className="space-y-1">
                    {base.almacenes.map(a => (
                      <li key={a.idAlmacen} className="flex items-center justify-between flex-wrap gap-x-2 gap-y-1 bg-panel border border-borde rounded px-2 py-1">
                        <span className="text-texto">{a.nombre} <span className="text-texto2">({a.tipo === 'CENTRAL' ? 'central' : 'intermedio'})</span></span>
                        <div className="flex items-center flex-wrap gap-1 font-mono text-texto2">
                          <span>(</span>
                          <EntradaNumero etiqueta={`Coordenada X de ${a.nombre}`} valor={leer(`a.${a.idAlmacen}.x`, a.ubicacionX)}
                            cambiar={v => cambiar(`a.${a.idAlmacen}.x`, v)} disabled={!editables} maximo={dimensionesValidas ? anchoKm : undefined} />
                          <span>,</span>
                          <EntradaNumero etiqueta={`Coordenada Y de ${a.nombre}`} valor={leer(`a.${a.idAlmacen}.y`, a.ubicacionY)}
                            cambiar={v => cambiar(`a.${a.idAlmacen}.y`, v)} disabled={!editables} maximo={dimensionesValidas ? altoKm : undefined} />
                          <span>)</span>
                          {a.tipo === 'INTERMEDIO' && <>
                            <span className="ml-2">stock</span>
                            <EntradaNumero etiqueta={`Capacidad de ${a.nombre} en paquetes`} valor={leer(`a.${a.idAlmacen}.stock`, a.capacidadMaxima ?? 0)}
                              cambiar={v => cambiar(`a.${a.idAlmacen}.stock`, v)} disabled={!editables} minimo={1} ancho="w-16" />
                          </>}
                        </div>
                      </li>
                    ))}
                  </ul>
                )}
              </div>
              <div>
                <p className="text-texto2 mb-1">Flota</p>
                {!base ? <Cargando /> : (
                  <ul className="space-y-1">
                    {ORDEN_FLOTA.map(tipo => {
                      const f = base.flota.find(x => x.tipo === tipo);
                      if (!f) return null;
                      return (
                        <li key={tipo} className="flex items-center justify-between flex-wrap gap-x-2 gap-y-1 bg-panel border border-borde rounded px-2 py-1">
                          <div className="flex gap-1 items-center text-texto">
                            <EntradaNumero etiqueta={`Cantidad de ${NOMBRES_FLOTA[tipo]}`} valor={leer(`f.${tipo}.cantidad`, f.cantidad)}
                              cambiar={v => cambiar(`f.${tipo}.cantidad`, v)} disabled={!editables} maximo={200} />
                            <strong>{NOMBRES_FLOTA[tipo]}</strong>
                          </div>
                          <div className="flex items-center flex-wrap gap-1 font-mono text-texto2">
                            <EntradaNumero etiqueta={`Capacidad de ${NOMBRES_FLOTA[tipo]} en paquetes`} valor={leer(`f.${tipo}.capacidad`, f.capacidadPaquetes)}
                              cambiar={v => cambiar(`f.${tipo}.capacidad`, v)} disabled={!editables} minimo={1} />
                            <span>paq ·</span>
                            <EntradaNumero etiqueta={`Velocidad de ${NOMBRES_FLOTA[tipo]} en km/h`} valor={leer(`f.${tipo}.velocidad`, f.velocidadKmh)}
                              cambiar={v => cambiar(`f.${tipo}.velocidad`, v)} disabled={!editables} minimo={0.01} paso={0.01} ancho="w-16" />
                            <span>km/h · S/</span>
                            <EntradaNumero etiqueta={`Costo por kilómetro de ${NOMBRES_FLOTA[tipo]}`} valor={leer(`f.${tipo}.costo`, f.costoPorKm)}
                              cambiar={v => cambiar(`f.${tipo}.costo`, v)} disabled={!editables} paso={0.01} ancho="w-16" />
                            <span>/km</span>
                          </div>
                        </li>
                      );
                    })}
                  </ul>
                )}
              </div>
              <div className="flex flex-wrap items-center justify-between gap-2 pt-1">
                <span className={`text-xs ${pendientes ? 'text-ambar' : 'text-texto2'}`}>
                  {pendientes ? 'Cambios sin guardar' : mensajeGuardado ? 'Cambios guardados' : 'Guardado para este escenario'}
                </span>
                <div className="flex flex-wrap gap-2">
                <Boton variant="secundario" disabled={!pendientes || !editables}
                  onClick={() => { setEdiciones({}); setErrorGuardar(null); setMensajeGuardado(false); }}>Descartar</Boton>
                <Boton variant="secundario" disabled={!pendientes || !editables || !!errorValidacion}
                  disabledReason={errorValidacion ?? undefined} onClick={guardar}>
                  {guardando ? 'Guardando…' : 'Guardar cambios'}
                </Boton>
                </div>
              </div>
              {errorValidacion && <p className="text-xs text-rojo" role="alert">{errorValidacion}</p>}
              {errorGuardar && <ErrorCarga titulo="No se pudo guardar" error={errorGuardar} />}
              {consulta.error &&
                <ErrorCarga titulo="No se pudo consultar la configuración" error={consulta.error} reintentar={consulta.recargar} />}
              <CalendariosCaso bloqueado={activa || guardando || historicosOcupados || pendientes || !configuracionBackend || !!accionEnCurso}
                alOcupado={setCalendarioOcupado} fechaInicio={fechaInicio} horaInicio={horaInicio} escenario={escenario} />
              <p className="text-xs text-texto2">Cada escenario conserva sus propios datos del caso. Los cambios sin guardar se conservan al cambiar de escenario.</p>
              <HistorialPedidos escenario={escenario} consulta={historicos} alOcupado={setHistoricosOcupados}
                bloqueado={activa || guardando || calendarioOcupado || pendientes || !configuracionBackend || consulta.cargando || !!consulta.error || !!accionEnCurso} />
              <p className="text-xs text-texto2">La carga y eliminación de históricos se aplica únicamente al escenario seleccionado.</p>
            </div>
          </Panel>
        </section>
      </div>
    </div>
  );
}
