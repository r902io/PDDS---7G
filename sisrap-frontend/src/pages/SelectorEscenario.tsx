import { useState, type ReactElement } from 'react';
import type { ConfiguracionOperativa, EscenarioSimulacion, CargaHistorica, TipoFlota } from '../types/api';
import { useSimulacion } from '../estado/SimulacionContext';
import { navegar } from '../estado/navegacion';
import { configuracionService } from '../services/configuracionService';
import { catalogoService } from '../services/catalogoService';
import { apiFetch, textoError } from '../services/apiClient';
import { useConsulta } from '../hooks/useConsulta';
import { Boton } from '../components/ui/Boton';
import { Campo } from '../components/ui/Campo';
import { Panel } from '../components/ui/Panel';
import { Cargando, ErrorCarga } from '../components/ui/Avisos';
import { IconoCalendario, IconoColapso, IconoCorrecto, IconoIniciar, IconoReloj, IconoOjo } from '../components/iconos';
import { ESCENARIOS, ORDEN_ESCENARIOS, corridaActiva } from '../utilitarios/escenarios';
import { diaSimulado, formatoFecha, formatoHora, hoyLocal } from '../utilitarios/formato';
import { tipoDesdeCodigo } from '../utilitarios/vehiculos';
import { HistorialPedidos } from '../components/HistorialPedidos';

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

const inputNumero = 'bg-bg border border-borde rounded text-texto font-mono text-xs text-center py-1 px-1 focus:outline-none focus:border-mint focus:ring-1 focus:ring-mint disabled:opacity-60';

function numero(valor: string): number {
  return valor.trim() === '' ? NaN : Number(valor);
}

function validar(c: ConfiguracionOperativa, ancho?: number, alto?: number): string | null {
  if (c.almacenes.length !== 3 || c.flota.length !== 3) return 'Se requieren tres almacenes y tres tipos de vehículo.';
  if (c.almacenes.filter(a => a.tipo === 'CENTRAL').length !== 1) return 'Se requiere exactamente un almacén central.';
  for (const a of c.almacenes) {
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
  const [errorInicio, setErrorInicio] = useState<string | null>(null);
  const [errorGuardar, setErrorGuardar] = useState<string | null>(null);
  const [guardando, setGuardando] = useState(false);
  const [guardada, setGuardada] = useState<ConfiguracionOperativa | null>(null);
  const [ediciones, setEdiciones] = useState<Record<string, string>>({});
  const [mensajeGuardado, setMensajeGuardado] = useState(false);

  const consulta = useConsulta(s => configuracionService.consultar(s), 'plantilla-operativa');
  const mapa = useConsulta(s => catalogoService.mapa(s), 'dimensiones-ciudad');
  const almacenesActuales = useConsulta(s => catalogoService.almacenes(s), 'catalogo-almacenes');
  const vehiculosActuales = useConsulta(s => catalogoService.vehiculos(s), 'catalogo-vehiculos');
  const historicos = useConsulta(s => apiFetch<CargaHistorica[]>('/api/pedidos/cargas-historicas', { autenticar: false, senal: s }), 'historicos-previos');

  const activa = corridaActiva(snapshot);
  const configuracionBackend = guardada ?? consulta.datos;
  const respaldo: ConfiguracionOperativa | null = !configuracionBackend && almacenesActuales.datos && vehiculosActuales.datos ? {
    almacenes: almacenesActuales.datos.map(a => ({
      idAlmacen: a.idAlmacen, nombre: a.nombre, tipo: a.tipo, ubicacionX: a.ubicacionX,
      ubicacionY: a.ubicacionY, capacidadMaxima: a.capacidadMaxima,
    })),
    flota: ORDEN_FLOTA.flatMap(tipo => {
      const unidades = vehiculosActuales.datos!.filter(v => tipoDesdeCodigo(v.idVehiculo) === tipo);
      if (!unidades.length) return [];
      return [{ tipo, cantidad: unidades.length, capacidadPaquetes: unidades[0].capacidadPaquetes,
        velocidadKmh: unidades[0].velocidadKmh, costoPorKm: unidades[0].costoPorKm }];
    }),
  } : null;
  const base = configuracionBackend ?? respaldo;
  const editables = Boolean(configuracionBackend) && !activa && !guardando;
  const pendientes = Object.keys(ediciones).length > 0;

  const leer = (clave: string, valor: number) => ediciones[clave] ?? String(valor);
  const cambiar = (clave: string, valor: string) => {
    setEdiciones(prev => ({ ...prev, [clave]: valor }));
    setMensajeGuardado(false);
    setErrorGuardar(null);
  };
  const borrador: ConfiguracionOperativa | null = configuracionBackend ? {
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
  const errorValidacion = borrador ? validar(borrador, mapa.datos?.anchoKm, mapa.datos?.altoKm) : null;

  async function guardar() {
    if (!borrador || !pendientes || errorValidacion || !editables) return;
    setGuardando(true);
    setErrorGuardar(null);
    setMensajeGuardado(false);
    try {
      const respuesta = await configuracionService.guardar(borrador);
      setGuardada(respuesta);
      setEdiciones({});
      setMensajeGuardado(true);
    } catch (e) {
      setErrorGuardar(textoError(e));
    } finally {
      setGuardando(false);
    }
  }

  const fechaValida = /^\d{4}-\d{2}-\d{2}$/.test(fechaInicio);
  const motivoBloqueo =
    sesion.fase === 'CARGANDO' ? 'Preparando la sesión.' :
    sesion.fase === 'ERROR' ? 'No hay una sesión disponible para iniciar.' :
    accionEnCurso === 'INICIAR' ? 'Solicitud de inicio en curso.' :
    activa ? 'Existe otra corrida en curso.' :
    guardando ? 'Guardando la configuración.' :
    pendientes ? 'Guarda los cambios de almacenes y flota antes de iniciar.' :
    errorValidacion ??
    (historicos.error || !historicos.datos ? 'No se pudo verificar la carga histórica.' :
    historicos.datos.length === 0 ? 'Carga al menos un mes histórico en Datos del caso.' :
    !fechaValida ? 'Indica una fecha de inicio válida.' : undefined);

  async function alIniciar() {
    if (motivoBloqueo) return;
    setErrorInicio(null);
    const semilla = crypto.getRandomValues(new Uint32Array(1))[0] & 0x7fffffff;
    const resultado = await iniciar(escenario, fechaInicio, semilla);
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
                <button key={id} role="radio" aria-checked={elegido} onClick={() => setEscenario(id)}
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

        <section className="grid grid-cols-1 lg:grid-cols-2 gap-4">
          <Panel titulo="2 · Inicio de la corrida" subtitulo="Indica la fecha desde la que comenzará la simulación.">
            <Campo label="Fecha de inicio" type="date" value={fechaInicio}
              onChange={e => setFechaInicio(e.target.value)} nota="La corrida empieza a las 00:00" required />
            <div className="mt-4 flex flex-col items-start gap-2">
              <Boton variant="primario" icono={<IconoIniciar tamano={16} />} onClick={alIniciar}
                disabled={motivoBloqueo != null} disabledReason={motivoBloqueo}
                motivoVisible className="px-6 py-2.5 text-base">
                {accionEnCurso === 'INICIAR' ? 'Iniciando…' : 'Iniciar corrida'}
              </Boton>
              {errorInicio && <ErrorCarga titulo="No se pudo iniciar la corrida" error={errorInicio} className="w-full" />}
            </div>
          </Panel>

          <Panel titulo="Datos del caso" subtitulo="Almacenes y flota para la próxima simulación.">
            <div className="space-y-3 text-xs">
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
                            cambiar={v => cambiar(`a.${a.idAlmacen}.x`, v)} disabled={!editables} maximo={mapa.datos?.anchoKm} />
                          <span>,</span>
                          <EntradaNumero etiqueta={`Coordenada Y de ${a.nombre}`} valor={leer(`a.${a.idAlmacen}.y`, a.ubicacionY)}
                            cambiar={v => cambiar(`a.${a.idAlmacen}.y`, v)} disabled={!editables} maximo={mapa.datos?.altoKm} />
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
                  {pendientes ? 'Cambios sin guardar' : mensajeGuardado ? 'Cambios guardados' : 'La configuración se aplica a la próxima corrida'}
                </span>
                <Boton variant="secundario" disabled={!pendientes || !editables || !!errorValidacion}
                  disabledReason={errorValidacion ?? undefined} onClick={guardar}>
                  {guardando ? 'Guardando…' : 'Guardar cambios'}
                </Boton>
              </div>
              {errorValidacion && <p className="text-xs text-rojo" role="alert">{errorValidacion}</p>}
              {errorGuardar && <ErrorCarga titulo="No se pudo guardar" error={errorGuardar} />}
              {consulta.error && !configuracionBackend &&
                <ErrorCarga titulo="No se pudo consultar la configuración" error={consulta.error} reintentar={consulta.recargar} />}
              <HistorialPedidos consulta={historicos} bloqueado={activa || guardando} />
              <p className="text-xs text-texto2 border-l-2 border-azul pl-2.5 leading-relaxed">
                Retícula de {mapa.datos ? `${mapa.datos.anchoKm} × ${mapa.datos.altoKm} km` : 'la ciudad'} con calles de doble sentido y turnos con cambio a las 07:00, 15:00 y 23:00.
              </p>
            </div>
          </Panel>
        </section>
      </div>
    </div>
  );
}
