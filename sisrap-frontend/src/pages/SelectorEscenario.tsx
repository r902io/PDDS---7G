import { useState, type ReactElement } from 'react';
import type { EscenarioSimulacion } from '../types/api';
import { useSimulacion } from '../estado/SimulacionContext';
import { navegar } from '../estado/navegacion';
import { catalogoService } from '../services/catalogoService';
import { useConsulta } from '../hooks/useConsulta';
import { Boton } from '../components/ui/Boton';
import { Campo } from '../components/ui/Campo';
import { Panel } from '../components/ui/Panel';
import { Cargando, ErrorCarga, Nota } from '../components/ui/Avisos';
import { IconoCalendario, IconoColapso, IconoCorrecto, IconoIniciar, IconoReloj, IconoOjo } from '../components/iconos';
import { ESCENARIOS, ORDEN_ESCENARIOS, corridaActiva } from '../utilitarios/escenarios';
import { diaSimulado, formatoCoordenada, formatoEntero, formatoFecha, formatoHora, formatoSoles, hoyLocal } from '../utilitarios/formato';
import { ETIQUETA_TIPO_PLURAL, ORDEN_TIPOS, tipoDesdeCodigo } from '../utilitarios/vehiculos';

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

export function SelectorEscenario() {
  const { snapshot, sesion, iniciar, accionEnCurso, reintentarSesion } = useSimulacion();
  const [escenario, setEscenario] = useState<EscenarioSimulacion>('OPERACION_DIARIA');
  const [fechaInicio, setFechaInicio] = useState(hoyLocal());
  const [error, setError] = useState<string | null>(null);

  const almacenes = useConsulta((s) => catalogoService.almacenes(s), 'almacenes');
  const vehiculos = useConsulta((s) => catalogoService.vehiculos(s), 'vehiculos');

  const activa = corridaActiva(snapshot);
  const fechaValida = /^\d{4}-\d{2}-\d{2}$/.test(fechaInicio);

  const motivoBloqueo =
    sesion.fase === 'CARGANDO'
      ? 'Preparando la sesión.'
      : sesion.fase === 'ERROR'
        ? 'No hay una sesión disponible para iniciar.'
        : accionEnCurso === 'INICIAR'
          ? 'Solicitud de inicio en curso.'
          : !fechaValida
            ? 'Indique una fecha de inicio válida.'
            : undefined;

  const alIniciar = async () => {
    setError(null);
    const r = await iniciar(escenario, fechaInicio, null);
    if (r.ok || r.corridaEnCurso) navegar({ nombre: 'operacion' });
    else setError(r.mensaje);
  };

  const flota = ORDEN_TIPOS.map((t) => {
    const lista = (vehiculos.datos ?? []).filter((v) => tipoDesdeCodigo(v.idVehiculo) === t);
    return { tipo: t, cantidad: lista.length, muestra: lista[0] ?? null };
  });

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
            <span className="text-azul">
              <IconoOjo tamano={20} />
            </span>
            <div className="text-sm flex-1 min-w-[240px]">
              <p className="text-texto font-semibold">Hay una corrida en curso: {ESCENARIOS[snapshot.escenario].titulo}</p>
              <p className="text-texto2">
                Día {diaSimulado(snapshot.fechaHoraInicio, snapshot.relojSimulado) ?? '—'} ·{' '}
                <span className="font-mono">{formatoHora(snapshot.relojSimulado)}</span> ·{' '}
                <span className="font-mono">{formatoFecha(snapshot.relojSimulado)}</span>. No se puede iniciar otra
                hasta que termine o la detenga su controlador.
              </p>
            </div>
            <Boton variant="secundario" onClick={() => navegar({ nombre: 'operacion' })}>
              Ver la corrida
            </Boton>
          </div>
        )}

        {sesion.fase === 'ERROR' && (
          <ErrorCarga titulo="No se pudo preparar la sesión" error={sesion.error} reintentar={reintentarSesion} />
        )}

        {/* 1. Escenarios */}
        <section>
          <h2 className="text-xs uppercase tracking-wider text-texto2 font-semibold border-b border-borde pb-1.5 mb-3">
            1 · Escenario
          </h2>
          <div className="grid grid-cols-1 md:grid-cols-3 gap-4" role="radiogroup" aria-label="Escenario">
            {ORDEN_ESCENARIOS.map((id) => {
              const e = ESCENARIOS[id];
              const sel = escenario === id;
              return (
                <button
                  key={id}
                  role="radio"
                  aria-checked={sel}
                  onClick={() => setEscenario(id)}
                  className={`text-left p-5 rounded-xl border transition-colors flex flex-col gap-2 ${
                    sel ? 'bg-panel2 border-mint ring-1 ring-mint' : 'bg-panel border-borde hover:border-texto2'
                  }`}
                >
                  <div className="flex items-start justify-between">
                    <span className={`p-2 rounded ${COLOR_ESCENARIO[id]}`}>{ICONO_ESCENARIO[id]}</span>
                    {sel && (
                      <span className="text-mint flex items-center gap-1 text-xs">
                        <IconoCorrecto tamano={18} /> Elegido
                      </span>
                    )}
                  </div>
                  <h3 className="text-base font-semibold text-texto">{e.titulo}</h3>
                  <p className="text-[13px] text-texto2 leading-relaxed flex-1">{e.resumen}</p>
                  <div className="border-t border-borde pt-2 text-xs text-texto2">
                    <span className="text-texto font-semibold">Condición de término: </span>
                    {e.condicionTermino}
                  </div>
                </button>
              );
            })}
          </div>
        </section>

        {/* 2. Parámetros de la corrida */}
        <section className="grid grid-cols-1 lg:grid-cols-2 gap-4">
          <Panel titulo="2 · Inicio de la corrida" subtitulo="Indica la fecha desde la que comenzará la simulación.">
            <div className="grid grid-cols-1 gap-3">
              <Campo
                label="Fecha de inicio"
                type="date"
                value={fechaInicio}
                onChange={(e) => setFechaInicio(e.target.value)}
                nota="La corrida empieza a las 00:00"
                required
              />
            </div>
            <div className="mt-4 flex flex-col items-start gap-2">
              <Boton
                variant="primario"
                icono={<IconoIniciar tamano={16} />}
                onClick={alIniciar}
                disabled={motivoBloqueo != null}
                disabledReason={motivoBloqueo}
                motivoVisible
                className="px-6 py-2.5 text-base"
              >
                {accionEnCurso === 'INICIAR' ? 'Iniciando…' : 'Iniciar corrida'}
              </Boton>
              {error && <ErrorCarga titulo="No se pudo iniciar la corrida" error={error} className="w-full" />}
            </div>
          </Panel>

          <Panel titulo="Datos del caso" subtitulo="Almacenes y flota disponibles para la simulación.">
            <div className="space-y-3 text-xs">
              <div>
                <p className="text-texto2 mb-1">Almacenes</p>
                {almacenes.error && !almacenes.datos ? (
                  <ErrorCarga titulo="No se pudieron leer los almacenes" error={almacenes.error} reintentar={almacenes.recargar} />
                ) : !almacenes.datos ? (
                  <Cargando />
                ) : (
                  <ul className="space-y-1">
                    {almacenes.datos.map((a) => (
                      <li key={a.idAlmacen} className="flex justify-between gap-2 bg-panel border border-borde rounded px-2 py-1">
                        <span className="text-texto">
                          {a.nombre} <span className="text-texto2">({a.tipo === 'CENTRAL' ? 'central' : 'intermedio'})</span>
                        </span>
                        <span className="font-mono text-texto">{formatoCoordenada(a.ubicacionX, a.ubicacionY)}</span>
                      </li>
                    ))}
                  </ul>
                )}
              </div>
              <div>
                <p className="text-texto2 mb-1">Flota</p>
                {vehiculos.error && !vehiculos.datos ? (
                  <ErrorCarga titulo="No se pudo leer la flota" error={vehiculos.error} reintentar={vehiculos.recargar} />
                ) : !vehiculos.datos ? (
                  <Cargando />
                ) : (
                  <ul className="space-y-1">
                    {flota.map((f) => (
                      <li key={f.tipo} className="flex justify-between gap-2 bg-panel border border-borde rounded px-2 py-1">
                        <span className="text-texto">
                          <span className="font-mono">{formatoEntero(f.cantidad)}</span> {ETIQUETA_TIPO_PLURAL[f.tipo].toLowerCase()}
                        </span>
                        <span className="font-mono text-texto2">
                          {f.muestra
                            ? `${f.muestra.capacidadPaquetes} paq · ${f.muestra.velocidadKmh} km/h · ${formatoSoles(f.muestra.costoPorKm)}/km`
                            : 'No disponible'}
                        </span>
                      </li>
                    ))}
                  </ul>
                )}
              </div>
              <Nota>
                Retícula de 70 × 50 km con calles de doble sentido y turnos con cambio a las 07:00, 15:00 y 23:00. El tipo
                de cada unidad se deduce del prefijo de su código: TA auto, TM moto, TB bicicleta.
              </Nota>
            </div>
          </Panel>
        </section>
      </div>
    </div>
  );
}
