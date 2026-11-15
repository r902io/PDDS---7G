import type { ReactNode } from 'react';
import { useSimulacion } from '../estado/SimulacionContext';
import { navegar, type Vista } from '../estado/NavegacionApp';
import { ESCENARIOS, corridaActiva, hayCorrida } from '../utilitarios/escenarios';
import { diaSimulado, ETIQUETA_TURNO, formatoFecha, formatoHora, partesFechaHora, turnoDe } from '../utilitarios/formato';
import { TiemposOperacion } from './TiemposOperacion';

export function Cabecera({ vista }: { vista: Vista }) {
  const { snapshot, datosVigentes } = useSimulacion();
  const mostrarOperacion = vista.nombre === 'operacion' && hayCorrida(snapshot) && snapshot != null;
  const escenario = snapshot?.escenario ? ESCENARIOS[snapshot.escenario] : null;
  const reloj = snapshot?.relojSimulado ?? null;
  const dia = diaSimulado(snapshot?.fechaHoraInicio, reloj);
  const turno = turnoDe(reloj);
  const hora = partesFechaHora(reloj);
  const inicioRefrigerio = turno === 'T1' ? 11 : turno === 'T2' ? 19 : turno === 'T3' ? 3 : null;
  const enRefrigerio = inicioRefrigerio != null && hora?.hora === inicioRefrigerio;
  const horarioRefrigerio = inicioRefrigerio == null ? '—' : `${String(inicioRefrigerio).padStart(2, '0')}:00–${String(inicioRefrigerio + 1).padStart(2, '0')}:00`;

  return (
    <header className="shrink-0 bg-panel border-b border-borde text-texto">
      <div className="flex flex-wrap items-center gap-x-8 gap-y-1 px-4 sm:px-6 py-1">
        <button onClick={() => navegar({ nombre: 'selector' })} className="min-h-11 text-xl font-bold tracking-wide focus-visible:outline-2 focus-visible:outline-mint" aria-label="SisRap · Inicio">
          Sis<span className="text-mint">Rap</span>
        </button>
        <nav className="flex flex-wrap items-center gap-1" aria-label="Secciones">
          <BotonNav activo={vista.nombre === 'selector'} onClick={() => navegar({ nombre: 'selector' })}>Escenarios</BotonNav>
          <BotonNav activo={vista.nombre === 'datos'} onClick={() => navegar({ nombre: 'datos' })}>Parámetros del escenario</BotonNav>
          <BotonNav activo={vista.nombre === 'operacion'} onClick={() => navegar({ nombre: 'operacion' })}>Operación</BotonNav>
          <BotonNav activo={vista.nombre === 'resultado'} onClick={() => navegar({ nombre: 'resultado' })}>Resultados</BotonNav>
          <BotonNav activo={vista.nombre === 'mantenimiento'} onClick={() => navegar({ nombre: 'mantenimiento', pestana: 'vehiculos' })}>Mantenimiento</BotonNav>
        </nav>
      </div>
      {mostrarOperacion && (
        <div className="grid grid-cols-1 lg:grid-cols-[minmax(0,1fr)_auto_minmax(0,1fr)] items-center gap-x-3 gap-y-1 border-t border-borde px-4 py-1">
          <div className="lg:col-start-2 flex flex-wrap justify-center items-center gap-x-5 gap-y-1 text-center">
            <div className="flex items-center gap-3 text-left">
              <div><p className="text-xs font-medium text-texto2">Hora simulada</p>
              <p className="text-xl font-semibold tabular-nums tracking-tight leading-tight text-mint" aria-label="Hora simulada">{formatoHora(reloj)}</p></div>
              <p className="text-xs text-texto2 tabular-nums">{formatoFecha(reloj)}
                {dia != null && <> · Día {escenario?.diasTotales ? Math.min(dia, escenario.diasTotales) : dia}</>}
              </p>
            </div>
            <div className="sm:border-l border-borde sm:pl-5">
              <p className="text-sm text-texto2">Turno de trabajo</p>
              <p className="text-base font-semibold tabular-nums">{turno ? ETIQUETA_TURNO[turno] : '—'}</p>
            </div>
            <div className={`rounded-md px-3 py-1 ${enRefrigerio && corridaActiva(snapshot) && datosVigentes ? 'border border-ambar/50 bg-ambar/10 text-ambar' : 'bg-panel2 text-texto'}`}>
              <p className="text-sm">{enRefrigerio && corridaActiva(snapshot) && datosVigentes ? 'En refrigerio' : 'Refrigerio · hora simulada'}</p>
              <p className="text-base font-semibold tabular-nums">{horarioRefrigerio}</p>
            </div>
          </div>
          <div className="justify-self-end lg:col-start-3">
            <TiemposOperacion snapshot={snapshot} />
          </div>
        </div>
      )}
    </header>
  );
}

function BotonNav({ activo, onClick, children }: { activo: boolean; onClick: () => void; children: ReactNode }) {
  return <button onClick={onClick} aria-current={activo ? 'page' : undefined}
    className={`min-h-11 rounded-md px-4 py-2 text-[15px] font-medium transition-colors focus-visible:outline-2 focus-visible:outline-mint ${activo ? 'bg-mint/10 text-mint' : 'text-texto2 hover:bg-panel2 hover:text-texto'}`}>
    {children}
  </button>;
}
