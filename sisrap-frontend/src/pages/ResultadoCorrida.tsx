import type { ReactNode } from 'react';
import { useSimulacion } from '../estado/SimulacionContext';
import { navegar } from '../estado/navegacion';
import { Boton } from '../components/ui/Boton';
import { Cargando, ErrorCarga, Nota } from '../components/ui/Avisos';
import { IconoAlerta, IconoColapso, IconoCorrecto, IconoDetener, IconoInfo } from '../components/iconos';
import { ESCENARIOS, corridaActiva, hayCorrida, tipoResultado, type TipoResultado } from '../utilitarios/escenarios';
import {
  diaSimulado,
  formatoDecimal,
  formatoEntero,
  formatoFechaHora,
  horasEntre,
  NO_DISPONIBLE,
} from '../utilitarios/formato';

const PRESENTACION: Record<
  TipoResultado,
  { titulo: string; clase: string; icono: ReactNode; descripcion: string }
> = {
  COMPLETADA: {
    titulo: 'Corrida completada',
    clase: 'border-mint bg-mint/10 text-mint',
    icono: <IconoCorrecto tamano={34} />,
    descripcion: 'El escenario alcanzó su condición de término sin ningún pedido fuera de plazo.',
  },
  COLAPSO: {
    titulo: 'Colapso logístico',
    clase: 'border-rojo bg-rojo/10 text-rojo',
    icono: <IconoColapso tamano={34} />,
    descripcion:
      'Se detectó el primer pedido que no se entregó dentro de su plazo. Según las reglas del caso, ese primer incumplimiento es el colapso logístico.',
  },
  ERROR: {
    titulo: 'Detenida por error o falta de datos',
    clase: 'border-ambar bg-ambar/10 text-ambar',
    icono: <IconoAlerta tamano={34} />,
    descripcion:
      'La corrida no pudo continuar. No es un colapso logístico: ocurrió un error o faltan datos necesarios para continuar.',
  },
  DETENIDA_MANUAL: {
    titulo: 'Corrida detenida manualmente',
    clase: 'border-texto2 bg-panel2 text-texto',
    icono: <IconoDetener tamano={30} />,
    descripcion: 'La sesión controladora detuvo la corrida antes de su condición de término.',
  },
};

export function ResultadoCorrida() {
  const { snapshot, errorEstado } = useSimulacion();

  if (!snapshot) {
    return (
      <div className="flex-1 flex items-center justify-center p-6">
        {errorEstado ? (
          <ErrorCarga titulo="No se pudo obtener el estado de la simulación" error={errorEstado} className="max-w-lg" />
        ) : (
          <Cargando />
        )}
      </div>
    );
  }

  if (!hayCorrida(snapshot)) {
    return (
      <Centro>
        <IconoInfo tamano={30} />
        <h1 className="text-xl font-semibold text-texto">Aún no hay resultados</h1>
        <p className="text-sm text-texto2">Todavía no se ha registrado una corrida.</p>
        <Boton onClick={() => navegar({ nombre: 'selector' })}>Elegir escenario</Boton>
      </Centro>
    );
  }

  if (corridaActiva(snapshot)) {
    return (
      <Centro>
        <span className="text-azul">
          <IconoInfo tamano={30} />
        </span>
        <h1 className="text-xl font-semibold text-texto">La corrida sigue en curso</h1>
        <p className="text-sm text-texto2">El resultado estará disponible cuando termine.</p>
        <Boton onClick={() => navegar({ nombre: 'operacion' })}>Ver la operación</Boton>
      </Centro>
    );
  }

  const tipo = tipoResultado(snapshot)!;
  const pres = PRESENTACION[tipo];
  const esc = snapshot.escenario ? ESCENARIOS[snapshot.escenario] : null;
  const horas = horasEntre(snapshot.fechaHoraInicio, snapshot.relojSimulado);
  const dia = diaSimulado(snapshot.fechaHoraInicio, snapshot.relojSimulado);
  const p = snapshot.pedidos;

  return (
    <div className="flex-1 overflow-y-auto">
      <div className="max-w-4xl mx-auto px-4 sm:px-8 py-6 space-y-5">
        <div className={`border-2 rounded-xl p-5 flex items-start gap-4 ${pres.clase}`} role="status">
          <span className="shrink-0">{pres.icono}</span>
          <div>
            <p className="text-[11px] uppercase tracking-wider text-texto2">Resultado de la corrida</p>
            <h1 className="text-2xl font-bold">{pres.titulo}</h1>
            <p className="text-sm text-texto mt-1 leading-relaxed">{pres.descripcion}</p>
            {snapshot.mensaje && (
              <p className="text-sm text-texto mt-2">
                <span className="text-texto2">Detalle:</span> {snapshot.mensaje}
              </p>
            )}
          </div>
        </div>

        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          <Tarjeta titulo="Corrida">
            <Dato k="Escenario" v={esc?.titulo ?? NO_DISPONIBLE} />
            <Dato k="Condición de término" v={esc?.condicionTermino ?? NO_DISPONIBLE} />
            <Dato k="Inicio" v={formatoFechaHora(snapshot.fechaHoraInicio)} mono />
            <Dato
              k={tipo === 'COLAPSO' ? 'Instante de detección' : 'Reloj al detenerse'}
              v={formatoFechaHora(snapshot.relojSimulado)}
              mono
            />
            <Dato k="Día alcanzado" v={dia != null ? (esc?.diasTotales ? `Día ${dia} de ${esc.diasTotales}` : `Día ${dia}`) : NO_DISPONIBLE} mono />
            <Dato k="Tiempo simulado" v={horas != null ? `${formatoDecimal(horas)} h` : NO_DISPONIBLE} mono />
          </Tarjeta>

          <Tarjeta titulo="Pedidos">
            <Dato k="Entregados" v={formatoEntero(p.entregados)} mono />
            <Dato k="En ruta al detenerse" v={formatoEntero(p.enRuta)} mono />
            <Dato k="Pendientes" v={formatoEntero(p.pendientes)} mono />
            <Dato k="Reasignados" v={formatoEntero(p.reasignados)} mono />
            <Dato k="Futuros" v={formatoEntero(p.futuros)} mono />
            <Dato k="Fuera de plazo" v={formatoEntero(p.retrasados)} mono destacado={p.retrasados > 0} />
            <Dato k="Total del periodo" v={formatoEntero(p.total)} mono />
          </Tarjeta>
        </div>

        {tipo === 'COLAPSO' && (
          <Tarjeta titulo="Origen del colapso">
            <Dato k="Pedido que lo originó" v={NO_DISPONIBLE} />
            <Dato k="Instante exacto del incumplimiento" v={NO_DISPONIBLE} />
            <Nota className="mt-2">
              El sistema muestra el momento en que detectó el primer incumplimiento. El pedido específico no está disponible en el resumen actual.
            </Nota>
          </Tarjeta>
        )}

        <Tarjeta titulo="Costos y recorrido">
          <Dato k="Distancia total" v={NO_DISPONIBLE} />
          <Dato k="Costo total" v={NO_DISPONIBLE} />
          <Dato k="Utilización por tipo de vehículo" v={NO_DISPONIBLE} />
        </Tarjeta>

        <div className="flex gap-2">
          <Boton onClick={() => navegar({ nombre: 'selector' })}>Nueva corrida</Boton>
          <Boton variant="secundario" onClick={() => navegar({ nombre: 'operacion' })}>
            Ver el mapa final
          </Boton>
        </div>
      </div>
    </div>
  );
}

function Centro({ children }: { children: ReactNode }) {
  return <div className="flex-1 flex flex-col items-center justify-center gap-3 p-6 text-center text-texto2">{children}</div>;
}

function Tarjeta({ titulo, children }: { titulo: string; children: ReactNode }) {
  return (
    <section className="bg-panel border border-borde rounded-lg p-4">
      <h2 className="text-xs uppercase tracking-wider text-texto2 font-semibold mb-2">{titulo}</h2>
      {children}
    </section>
  );
}

function Dato({ k, v, mono = false, destacado = false }: { k: string; v: string; mono?: boolean; destacado?: boolean }) {
  const noDisp = v === NO_DISPONIBLE;
  return (
    <div className="flex justify-between gap-4 py-1 text-sm border-b border-borde/50 last:border-b-0">
      <span className="text-texto2">{k}</span>
      <span
        className={`text-right ${mono && !noDisp ? 'font-mono' : ''} ${
          noDisp ? 'text-texto2 italic' : destacado ? 'text-rojo font-bold' : 'text-texto'
        }`}
      >
        {v}
      </span>
    </div>
  );
}
