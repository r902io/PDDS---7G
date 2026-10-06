import type { ReactNode } from 'react';
import { useSimulacion, type Rol } from '../estado/SimulacionContext';
import { navegar, type Vista } from '../estado/navegacion';
import {
  ESCENARIOS,
  ETIQUETA_ESTADO_SIMULACION,
  corridaActiva,
  enColapso,
  hayCorrida,
} from '../utilitarios/escenarios';
import {
  diaSemana,
  diaSimulado,
  ETIQUETA_TURNO,
  formatoFecha,
  formatoHora,
  horaNavegador,
  horasEntre,
  turnoDe,
} from '../utilitarios/formato';
import { IconoConexion, IconoSinConexion } from './iconos';

const ETIQUETA_ROL: Record<Rol, { texto: string; clase: string; ayuda: string }> = {
  CONTROLADOR: {
    texto: 'Controlador',
    clase: 'text-mint border-mint/60 bg-mint/10',
    ayuda: 'Esta sesión inició la corrida y es la única que puede detenerla.',
  },
  OBSERVADOR: {
    texto: 'Observador',
    clase: 'text-azul border-azul/60 bg-azul/10',
    ayuda: 'Otra sesión controla la corrida. Aquí se ve el mismo estado, sin controles.',
  },
  SIN_CORRIDA: {
    texto: 'Sin corrida activa',
    clase: 'text-texto2 border-borde bg-panel2',
    ayuda: 'No hay una corrida en ejecución. La sesión que inicie la próxima será su controladora.',
  },
};

export function Cabecera({ vista }: { vista: Vista }) {
  const { snapshot, rol, conexion, datosVigentes, recibidoEn, sesion } = useSimulacion();
  const activa = corridaActiva(snapshot);
  const corrida = hayCorrida(snapshot);
  const esc = snapshot?.escenario ? ESCENARIOS[snapshot.escenario] : null;
  const reloj = snapshot?.relojSimulado ?? null;
  const dia = corrida ? diaSimulado(snapshot?.fechaHoraInicio, reloj) : null;
  const turno = turnoDe(reloj);
  const colapso = enColapso(snapshot);

  // Avance sobre 5 días: solo en la simulación de cinco días.
  const horas = esc?.diasTotales ? horasEntre(snapshot?.fechaHoraInicio, reloj) : null;
  const avance = esc?.diasTotales && horas != null ? Math.min(100, (horas / (esc.diasTotales * 24)) * 100) : null;

  const rolInfo = ETIQUETA_ROL[rol];
  const estado = snapshot?.estado;
  const claseEstado =
    colapso
      ? 'text-rojo border-rojo/60 bg-rojo/10'
      : estado === 'EJECUTANDO'
        ? 'text-mint border-mint/60 bg-mint/10'
        : estado === 'ERROR'
          ? 'text-ambar border-ambar/60 bg-ambar/10'
          : 'text-texto2 border-borde bg-panel2';

  return (
    <header className="bg-panel border-b border-borde px-3 sm:px-4 py-2 flex flex-wrap items-center gap-x-4 gap-y-2 shrink-0">
      <button
        onClick={() => navegar({ nombre: 'selector' })}
        className="text-[18px] font-bold tracking-wide text-texto"
        title="Ir al selector de escenario"
      >
        Sis<span className="text-mint">Rap</span>
        <span className="hidden md:inline text-sm font-normal text-texto2"> · Visualizador</span>
      </button>

      <nav className="flex items-center gap-1 text-xs" aria-label="Secciones">
        <BotonNav activo={vista.nombre === 'selector'} onClick={() => navegar({ nombre: 'selector' })}>
          Escenarios
        </BotonNav>
        <BotonNav activo={vista.nombre === 'operacion'} onClick={() => navegar({ nombre: 'operacion' })}>
          Operación
        </BotonNav>
        <BotonNav activo={vista.nombre === 'resultado'} onClick={() => navegar({ nombre: 'resultado' })}>
          Resultado
        </BotonNav>
        <BotonNav
          activo={vista.nombre === 'mantenimiento'}
          onClick={() => navegar({ nombre: 'mantenimiento', pestana: 'vehiculos' })}
        >
          Mantenimiento
        </BotonNav>
      </nav>

      <div className="flex flex-wrap items-center gap-2 text-xs">
        <Pastilla etiqueta="Escenario" clase={esc ? 'text-texto border-borde bg-panel2' : 'text-texto2 border-borde bg-panel2'}>
          {esc ? esc.tituloCorto : 'Ninguno'}
        </Pastilla>
        <Pastilla etiqueta="Rol" clase={rolInfo.clase} titulo={rolInfo.ayuda}>
          {sesion.fase === 'LISTA' ? rolInfo.texto : sesion.fase === 'CARGANDO' ? 'Creando sesión…' : 'Sin sesión'}
        </Pastilla>
        <Pastilla etiqueta="Corrida" clase={claseEstado}>
          {estado ? (colapso ? 'Colapso logístico' : ETIQUETA_ESTADO_SIMULACION[estado]) : 'Sin datos'}
        </Pastilla>
      </div>

      {/* Día simulado y reloj, juntos, con la fecha calendario */}
      <div className="ml-auto flex items-center gap-3">
        <div
          className={`flex items-center gap-3 border rounded-lg px-3 py-1 bg-panel2 ${
            datosVigentes || !corrida ? 'border-borde' : 'border-ambar'
          }`}
        >
          <div className="flex flex-col leading-tight">
            <span className="text-[11px] uppercase tracking-wider text-texto2">Día simulado</span>
            <span className="font-mono text-base text-texto">
              {dia != null ? (
                esc?.diasTotales ? (
                  <>
                    Día {dia} <span className="text-texto2">de {esc.diasTotales}</span>
                  </>
                ) : (
                  <>Día {dia}</>
                )
              ) : (
                '—'
              )}
            </span>
          </div>
          <div className="flex flex-col items-end leading-tight">
            <span
              className={`font-mono text-2xl font-semibold tracking-wider ${
                corrida && !activa ? 'text-texto2' : 'text-texto'
              }`}
              aria-label="Hora simulada"
            >
              {reloj ? formatoHora(reloj) : '--:--'}
            </span>
            <span className="text-[11px] text-texto2">
              {reloj ? (
                <>
                  {diaSemana(reloj)} <span className="font-mono">{formatoFecha(reloj)}</span>
                </>
              ) : (
                'Sin corrida'
              )}
            </span>
          </div>
          {turno && (
            <div className="hidden lg:flex flex-col leading-tight border-l border-borde pl-3">
              <span className="text-[11px] uppercase tracking-wider text-texto2">Turno</span>
              <span className="text-xs text-texto">{ETIQUETA_TURNO[turno]}</span>
            </div>
          )}
          {avance != null && (
            <div className="flex flex-col leading-tight border-l border-borde pl-3 w-28">
              <span className="text-[11px] uppercase tracking-wider text-texto2">Avance</span>
              <span className="font-mono text-xs text-texto">{avance.toFixed(1)} % de 5 días</span>
              <span className="h-1.5 bg-bg rounded mt-0.5 overflow-hidden" aria-hidden>
                <span className="block h-full bg-azul" style={{ width: `${avance}%` }} />
              </span>
            </div>
          )}
        </div>

        <IndicadorConexion vigentes={datosVigentes} conexion={conexion} recibidoEn={recibidoEn} />
      </div>
    </header>
  );
}

function BotonNav({ activo, onClick, children }: { activo: boolean; onClick: () => void; children: ReactNode }) {
  return (
    <button
      onClick={onClick}
      aria-current={activo ? 'page' : undefined}
      className={`px-2.5 py-1 rounded-md border ${
        activo ? 'border-mint text-mint bg-mint/10' : 'border-borde text-texto2 hover:text-texto hover:border-texto2'
      }`}
    >
      {children}
    </button>
  );
}

function Pastilla({
  etiqueta,
  clase,
  titulo,
  children,
}: {
  etiqueta: string;
  clase: string;
  titulo?: string;
  children: ReactNode;
}) {
  return (
    <span className={`inline-flex items-center gap-1.5 border rounded-full px-2.5 py-0.5 ${clase}`} title={titulo}>
      <span className="text-[10px] uppercase tracking-wider text-texto2">{etiqueta}</span>
      <span className="font-medium">{children}</span>
    </span>
  );
}

function IndicadorConexion({
  vigentes,
  conexion,
  recibidoEn,
}: {
  vigentes: boolean;
  conexion: string;
  recibidoEn: number | null;
}) {
  if (vigentes) {
    return (
      <span className="flex items-center gap-1.5 text-xs text-mint" title="Conectado al flujo en tiempo real del backend">
        <IconoConexion tamano={16} /> En vivo
      </span>
    );
  }
  return (
    <span
      className="flex items-center gap-1.5 text-xs text-ambar"
      title="Sin datos en tiempo real del backend"
      role="status"
    >
      <IconoSinConexion tamano={16} />
      <span className="flex flex-col leading-tight">
        <span>{conexion === 'CONECTANDO' || conexion === 'SIN_INICIAR' ? 'Conectando…' : 'Sin conexión en vivo'}</span>
        <span className="text-[11px] text-texto2">
          Último dato: <span className="font-mono">{horaNavegador(recibidoEn)}</span>
        </span>
      </span>
    </span>
  );
}
