import React, { useState } from 'react';
import {
  SimulationState,
  Order,
  Vehicle,
  Incident,
} from '../types/logistics';
import { useMapCanvas } from '../hooks/useMapCanvas';
import { Boton } from '../components/ui/Boton';
import { Tarjeta } from '../components/ui/Tarjeta';
import { IndicadorDesglosado } from '../components/ui/Indicador';
import { SemaforoBadge } from '../components/ui/SemaforoBadge';
import { DetalleIncidenciaModal } from './DetalleIncidenciaModal';
import { ColapsoModal } from './ColapsoModal';
import { CierreSimulacionModal } from './CierreSimulacionModal';
import {
  Clock,
  Layers,
  ChevronLeft,
  ChevronRight,
  Maximize2,
  AlertTriangle,
  ShieldAlert,
  Truck,
  RotateCcw,
  FileText,
  Upload,
  Info,
  ExternalLink,
  Flame,
} from 'lucide-react';

interface EscenarioDashboardProps {
  state: SimulationState;
  onReset: () => void;
  onBackToSelector: () => void;
  onReportIncident: (incidentReq: any) => void;
}

export const EscenarioDashboard: React.FC<EscenarioDashboardProps> = ({
  state,
  onReset,
  onBackToSelector,
  onReportIncident,
}) => {
  // Collapsible panels state
  const [leftPanelCollapsed, setLeftPanelCollapsed] = useState(false);
  const [rightPanelCollapsed, setRightPanelCollapsed] = useState(false);

  // Selected item state for detail inspection
  const [selectedVehicleId, setSelectedVehicleId] = useState<string | null>(null);
  const [selectedOrder, setSelectedOrder] = useState<Order | null>(null);
  const [activeIncidentModal, setActiveIncidentModal] = useState<Incident | null>(null);

  // Format help modal
  const [showFileFormatModal, setShowFileFormatModal] = useState(false);

  // Simulation closure and collapse modals
  const [showClosureModal, setShowClosureModal] = useState(false);
  const [showCollapseModal, setShowCollapseModal] = useState(false);

  // Quick incident injection form inside parameters panel
  const [quickIncidentVehicle, setQuickIncidentVehicle] = useState(state.vehicles[0]?.id || '');
  const [quickRoadBlockDesc, setQuickRoadBlockDesc] = useState('Obras de emergencia en calzada');

  // Map Canvas Hook
  const { canvasRef, hoveredCoord, hoveredVehicle, fitToView, handlers } = useMapCanvas(
    state.warehouses,
    state.vehicles,
    state.orders,
    state.roadBlocks,
    selectedVehicleId,
    setSelectedVehicleId,
    setSelectedOrder
  );

  const selectedVehicle = selectedVehicleId
    ? state.vehicles.find((v) => v.id === selectedVehicleId)
    : null;

  const scenarioTitles = {
    DIA_A_DIA: 'Escenario: Operación Día a Día (Tiempo Real)',
    SIMULACION_5D: 'Escenario: Simulación de Cinco Días (120 Horas)',
    COLAPSO_LOGISTICO: 'Escenario: Simulación hasta Colapso Logístico',
  };

  const shiftLabels = {
    MANANA: 'Turno Mañana (07:00 - 15:00)',
    TARDE: 'Turno Tarde (15:00 - 23:00)',
    NOCHE: 'Turno Noche (23:00 - 07:00)',
  };

  return (
    <div className="h-screen w-screen bg-bg text-texto flex flex-col overflow-hidden select-none">
      {/* 1. Cabecera Superior (Estándar GUI v01) */}
      <header className="h-14 bg-panel2 border-b border-borde px-4 flex items-center justify-between shrink-0 z-20">
        <div className="flex items-center gap-4">
          <button
            onClick={onBackToSelector}
            className="text-xs text-texto2 hover:text-texto flex items-center gap-1.5 px-2.5 py-1 rounded border border-borde/70 hover:bg-panel transition-colors"
          >
            <ChevronLeft className="w-4 h-4" />
            <span>Selector</span>
          </button>

          <div>
            <div className="flex items-center gap-2">
              <span className="text-xs font-semibold uppercase tracking-wider text-mint">
                {scenarioTitles[state.scenario]}
              </span>
              <span className="w-1.5 h-1.5 rounded-full bg-mint animate-pulse" />
            </div>
            <div className="text-[11px] text-texto2 font-mono flex items-center gap-2">
              <span>{shiftLabels[state.clock.currentShift]}</span>
              <span>•</span>
              <span>{state.clock.formattedDate}</span>
            </div>
          </div>
        </div>

        {/* Central Clock */}
        <div className="flex items-center gap-6">
          <div className="text-center">
            <span className="text-[10px] text-texto2 uppercase tracking-wider block">Reloj Simulado</span>
            <div className="text-2xl font-mono font-bold text-texto tracking-wider">
              {state.clock.formattedTime}
            </div>
          </div>

          <div className="border-l border-borde/60 pl-4 text-left">
            <span className="text-[10px] text-texto2 uppercase tracking-wider block">
              {state.scenario === 'DIA_A_DIA'
                ? 'Tiempo Transcurrido'
                : state.scenario === 'SIMULACION_5D'
                ? 'Avance de Corrida'
                : 'Día de Estrés'}
            </span>
            <div className="text-sm font-mono font-semibold text-mint">
              {state.scenario === 'DIA_A_DIA' && (
                <span>Día {state.clock.simulatedDay} (+{state.clock.elapsedSimulatedHours.toFixed(1)}h)</span>
              )}
              {state.scenario === 'SIMULACION_5D' && (
                <span>Día {state.clock.simulatedDay} / 5 ({Math.min(100, (state.clock.elapsedSimulatedHours / 120) * 100).toFixed(1)}%)</span>
              )}
              {state.scenario === 'COLAPSO_LOGISTICO' && (
                <span>Día {state.clock.simulatedDay} (Bajo Presión)</span>
              )}
            </div>
          </div>
        </div>

        {/* Header Right Actions */}
        <div className="flex items-center gap-2">
          {state.hasCollapsed && (
            <Boton
              variant="peligro"
              icono={<Flame className="w-4 h-4" />}
              onClick={() => setShowCollapseModal(true)}
              className="text-xs py-1.5"
            >
              Ver Colapso Declarado
            </Boton>
          )}

          {state.isFinished && state.scenario === 'SIMULACION_5D' && (
            <Boton
              variant="primario"
              icono={<Info className="w-4 h-4" />}
              onClick={() => setShowClosureModal(true)}
              className="text-xs py-1.5"
            >
              Ver Cierre 5D
            </Boton>
          )}

          <Boton
            variant="secundario"
            icono={<RotateCcw className="w-3.5 h-3.5" />}
            onClick={onReset}
            className="text-xs py-1 px-3"
            title="Reiniciar escenario a su estado inicial (LE-099)"
          >
            Reiniciar Escenario
          </Boton>
        </div>
      </header>

      {/* Main Workspace: 3 Columns (Left Params, Center Canvas Map, Right KPIs) */}
      <div className="flex-1 flex overflow-hidden relative">
        {/* 2. Columna Izquierda: Parámetros y Archivos */}
        <div
          className={`bg-panel border-r border-borde flex flex-col transition-all duration-300 z-10 ${
            leftPanelCollapsed ? 'w-10' : 'w-80'
          }`}
        >
          {/* Collapse toggle */}
          <div className="h-9 border-b border-borde/70 flex items-center justify-between px-2.5 bg-panel2">
            {!leftPanelCollapsed && (
              <span className="text-xs font-semibold uppercase tracking-wider text-texto">
                Parámetros y Archivos
              </span>
            )}
            <button
              onClick={() => setLeftPanelCollapsed(!leftPanelCollapsed)}
              className="p-1 hover:bg-panel rounded text-texto2 hover:text-texto ml-auto"
              title={leftPanelCollapsed ? 'Expandir panel izquierdo' : 'Colapsar panel izquierdo'}
            >
              {leftPanelCollapsed ? <ChevronRight className="w-4 h-4" /> : <ChevronLeft className="w-4 h-4" />}
            </button>
          </div>

          {!leftPanelCollapsed && (
            <div className="flex-1 overflow-y-auto p-3 space-y-4 text-xs">
              {/* Monthly Files Library */}
              <div className="bg-panel2 border border-borde rounded-md p-3 space-y-2">
                <div className="flex items-center justify-between">
                  <span className="font-semibold uppercase tracking-wider text-texto text-[11px] flex items-center gap-1.5">
                    <FileText className="w-3.5 h-3.5 text-mint" />
                    <span>Archivos Mensuales (LE-006, LE-085)</span>
                  </span>
                  <button
                    onClick={() => setShowFileFormatModal(true)}
                    className="text-[10px] text-azul hover:underline flex items-center gap-0.5"
                  >
                    Formato
                  </button>
                </div>

                <div className="space-y-1.5 pt-1">
                  {state.monthlyFiles.map((file, idx) => (
                    <div
                      key={idx}
                      className="p-2 rounded bg-panel border border-borde/60 flex items-center justify-between text-[11px]"
                    >
                      <div>
                        <div className="font-mono text-texto font-medium">{file.filename}</div>
                        <div className="text-[10px] text-texto2">
                          {file.type === 'PEDIDOS' ? 'Demanda Mensual' : 'Bloqueos Viales'} • {file.recordCount} registros
                        </div>
                      </div>
                      <span className="px-1.5 py-0.5 rounded bg-mint/15 text-mint font-mono text-[10px]">
                        OK
                      </span>
                    </div>
                  ))}
                </div>
              </div>

              {/* Traffic Light Config Summary */}
              <div className="bg-panel2 border border-borde rounded-md p-3 space-y-2">
                <span className="font-semibold uppercase tracking-wider text-texto text-[11px]">
                  Semáforo de Criticidad (LE-062)
                </span>
                <div className="space-y-1 text-[11px]">
                  <div className="flex items-center justify-between">
                    <span className="text-sem-verde flex items-center gap-1.5">
                      <span className="w-2 h-2 rounded-full bg-sem-verde" />
                      <span>Verde (Conforme):</span>
                    </span>
                    <span className="font-mono text-texto">&gt; {state.thresholds.greenMinHours.toFixed(1)}h</span>
                  </div>
                  <div className="flex items-center justify-between">
                    <span className="text-sem-ambar flex items-center gap-1.5">
                      <span className="w-2 h-2 rounded-full bg-sem-ambar" />
                      <span>Ámbar (Alerta):</span>
                    </span>
                    <span className="font-mono text-texto">
                      {state.thresholds.amberMinHours.toFixed(1)}h a {state.thresholds.greenMinHours.toFixed(1)}h
                    </span>
                  </div>
                  <div className="flex items-center justify-between">
                    <span className="text-sem-rojo flex items-center gap-1.5">
                      <span className="w-2 h-2 rounded-full bg-sem-rojo" />
                      <span>Rojo (Crítico):</span>
                    </span>
                    <span className="font-mono text-texto">&lt; {state.thresholds.amberMinHours.toFixed(1)}h</span>
                  </div>
                </div>
              </div>

              {/* Fleet Specs */}
              <div className="bg-panel2 border border-borde rounded-md p-3 space-y-2">
                <span className="font-semibold uppercase tracking-wider text-texto text-[11px] flex items-center gap-1.5">
                  <Truck className="w-3.5 h-3.5 text-azul" />
                  <span>Composición de Flota y Parámetros</span>
                </span>
                <div className="space-y-1.5 text-[11px]">
                  <div className="p-1.5 rounded bg-panel border border-borde/50 flex justify-between">
                    <span className="text-azul font-medium">Autos (3 unidades):</span>
                    <span className="font-mono text-texto">24 u. • 40 km/h • S/ 8/km</span>
                  </div>
                  <div className="p-1.5 rounded bg-panel border border-borde/50 flex justify-between">
                    <span className="text-violeta font-medium">Motos (3 unidades):</span>
                    <span className="font-mono text-texto">8 u. • 25 km/h • S/ 6/km</span>
                  </div>
                  <div className="p-1.5 rounded bg-panel border border-borde/50 flex justify-between">
                    <span className="text-mint font-medium">Bicicletas (2 unidades):</span>
                    <span className="font-mono text-texto">4 u. • 12 km/h • S/ 3/km</span>
                  </div>
                </div>
              </div>

              {/* Quick Incident Simulation Injector (LE-079, LE-083) */}
              <div className="bg-panel2 border border-borde rounded-md p-3 space-y-2.5">
                <span className="font-semibold uppercase tracking-wider text-texto text-[11px] flex items-center gap-1.5">
                  <AlertTriangle className="w-3.5 h-3.5 text-rojo" />
                  <span>Inyectar Incidencia de Prueba</span>
                </span>
                <p className="text-[10px] text-texto2">
                  Prueba la replanificación inmediata metaheurística ante avería de unidad o bloqueo vial.
                </p>

                <div className="space-y-2">
                  <div className="flex gap-1">
                    <select
                      value={quickIncidentVehicle}
                      onChange={(e) => setQuickIncidentVehicle(e.target.value)}
                      className="bg-bg border border-borde text-texto font-mono text-[11px] rounded px-2 py-1 flex-1"
                    >
                      {state.vehicles.map((v) => (
                        <option key={v.id} value={v.id}>
                          {v.plate} ({v.type})
                        </option>
                      ))}
                    </select>
                    <Boton
                      variant="peligro"
                      className="text-[10px] py-1 px-2.5"
                      disabled={!quickIncidentVehicle}
                      title={!quickIncidentVehicle ? 'Sin vehículos en flota' : 'Reportar avería'}
                      onClick={() =>
                        quickIncidentVehicle &&
                        onReportIncident({
                          type: 'AVERIA_UNIDAD',
                          vehicleId: quickIncidentVehicle,
                          description: 'Falla mecánica imprevista en ruta',
                        })
                      }
                    >
                      Averiar
                    </Boton>
                  </div>

                  <div className="flex gap-1">
                    <input
                      type="text"
                      placeholder="Motivo bloqueo"
                      value={quickRoadBlockDesc}
                      onChange={(e) => setQuickRoadBlockDesc(e.target.value)}
                      className="bg-bg border border-borde text-texto text-[11px] rounded px-2 py-1 flex-1"
                    />
                    <Boton
                      variant="secundario"
                      className="text-[10px] py-1 px-2.5"
                      onClick={() =>
                        onReportIncident({
                          type: 'BLOQUEO_VIA',
                          roadSegment: { fromX: 25, fromY: 20, toX: 28, toY: 20 },
                          description: quickRoadBlockDesc,
                        })
                      }
                    >
                      Bloquear
                    </Boton>
                  </div>
                </div>
              </div>
            </div>
          )}
        </div>

        {/* 3. Área Central: Mapa Canvas 2D (70x50 km) */}
        <div className="flex-1 relative bg-bg flex flex-col overflow-hidden">
          {/* Canvas Viewport Controls */}
          <div className="absolute top-3 right-3 z-10 flex items-center gap-2">
            <button
              onClick={fitToView}
              className="bg-panel2/90 border border-borde px-2.5 py-1.5 rounded-md text-xs text-texto hover:bg-panel flex items-center gap-1.5 backdrop-blur-sm shadow-md transition-colors"
              title="Ajustar y centrar cuadrícula"
            >
              <Maximize2 className="w-3.5 h-3.5 text-mint" />
              <span>Centrar Mapa</span>
            </button>
          </div>

          {/* Interactive Canvas Container (flex-grow: 1, min-h-0, overflow: hidden) */}
          <div className="flex-1 min-h-0 w-full relative overflow-hidden cursor-crosshair">
            <canvas
              ref={canvasRef}
              className="w-full h-full block"
              {...handlers}
            />
          </div>

          {/* Map Legend (flex-shrink: 0, fixed height, permanently visible at bottom border) */}
          <div className="h-11 shrink-0 w-full bg-panel2/95 border-t border-borde px-4 flex items-center justify-between text-xs text-texto2 z-10 overflow-x-auto whitespace-nowrap">
            <div className="flex items-center gap-4 overflow-x-auto py-1">
              <span className="text-[10px] uppercase font-bold text-texto tracking-wider">Leyenda:</span>

              <div className="flex items-center gap-1.5">
                <span className="w-2.5 h-2.5 rounded-full bg-azul inline-block" />
                <span className="text-[11px]">Auto (40 km/h)</span>
              </div>

              <div className="flex items-center gap-1.5">
                <span className="w-2.5 h-2.5 rounded-full bg-violeta inline-block" />
                <span className="text-[11px]">Moto (25 km/h)</span>
              </div>

              <div className="flex items-center gap-1.5">
                <span className="w-2.5 h-2.5 rounded-full bg-mint inline-block" />
                <span className="text-[11px]">Bicicleta (12 km/h)</span>
              </div>

              <div className="flex items-center gap-1.5">
                <span className="w-3 h-3 bg-sky-400 border border-white inline-block shadow-sm" />
                <span className="text-[11px] text-white font-medium">AC Central (27,14) [Cian/Blanco • No rojo]</span>
              </div>

              <div className="flex items-center gap-1.5">
                <span className="w-2.5 h-2.5 bg-mint inline-block" />
                <span className="text-[11px]">A1 (12,38) • A2 (57,27) [Mint]</span>
              </div>

              <div className="flex items-center gap-1.5">
                <span className="w-2.5 h-2.5 rounded-full bg-sem-verde inline-block" />
                <span className="text-[11px]">Cliente (Semáforo)</span>
              </div>

              <div className="flex items-center gap-1.5">
                <span className="w-4 h-1 border-t-2 border-dashed border-rojo inline-block" />
                <span className="text-[11px]">Bloqueo Vial</span>
              </div>

              <div className="flex items-center gap-1.5">
                <span className="w-4 h-1 border-t-2 border-ambar inline-block" />
                <span className="text-[11px]">Ruta Alternativa</span>
              </div>
            </div>

            <div className="font-mono text-[11px] text-texto2">
              Retícula 70 × 50 km • Calles Doble Sentido
            </div>
          </div>
        </div>

        {/* 4. Columna Derecha: Indicadores y Monitoreo */}
        <div
          className={`bg-panel border-l border-borde flex flex-col transition-all duration-300 z-10 ${
            rightPanelCollapsed ? 'w-10' : 'w-84'
          }`}
        >
          {/* Collapse toggle */}
          <div className="h-9 border-b border-borde/70 flex items-center justify-between px-2.5 bg-panel2">
            <button
              onClick={() => setRightPanelCollapsed(!rightPanelCollapsed)}
              className="p-1 hover:bg-panel rounded text-texto2 hover:text-texto mr-auto"
              title={rightPanelCollapsed ? 'Expandir panel derecho' : 'Colapsar panel derecho'}
            >
              {rightPanelCollapsed ? <ChevronLeft className="w-4 h-4" /> : <ChevronRight className="w-4 h-4" />}
            </button>
            {!rightPanelCollapsed && (
              <span className="text-xs font-semibold uppercase tracking-wider text-texto">
                Dashboard de Indicadores
              </span>
            )}
          </div>

          {!rightPanelCollapsed && (
            <div className="flex-1 overflow-y-auto p-3 space-y-3 text-xs">
              {/* Compliance & Minimum Slack Card (LE-066, LE-075) */}
              <div className="space-y-2">
                <Tarjeta
                  titulo="Cumplimiento de Plazos (LE-066)"
                  valor={`${state.kpis.totalDeliveredOnTime} pedidos`}
                  contexto="Política estricta PaqRap de cero demoras en reparto"
                  alerta="conforme"
                  porcentaje={100}
                />

                <div className="bg-panel border border-borde rounded-md p-3">
                  <span className="text-[11px] uppercase tracking-wider text-texto2 block">
                    Holgura Mínima Vigente (LE-075)
                  </span>
                  {state.kpis.minSlackOrderId ? (
                    <>
                      <div className="flex items-center justify-between mt-1">
                        <span className="text-xl font-mono font-bold text-texto">
                          +{state.kpis.minSlackHours.toFixed(1)}h
                        </span>
                        <SemaforoBadge
                          nivel={
                            state.kpis.minSlackHours >= state.thresholds.greenMinHours
                              ? 'VERDE'
                              : state.kpis.minSlackHours >= state.thresholds.amberMinHours
                              ? 'AMBAR'
                              : 'ROJO'
                          }
                          slackHours={state.kpis.minSlackHours}
                        />
                      </div>
                      <div className="text-[10px] text-texto2 mt-1">
                        Pedido más crítico: <span className="font-mono text-texto font-medium">{state.kpis.minSlackOrderId}</span> (Cliente {state.kpis.minSlackClientCode})
                      </div>
                    </>
                  ) : (
                    <div className="text-[11px] text-texto2 mt-1 italic">
                      Sin datos de holgura del backend (—)
                    </div>
                  )}
                </div>
              </div>

              {/* Fleet Utilization (LE-070) */}
              <div className="bg-panel border border-borde rounded-md p-3 space-y-2">
                <div className="flex items-center justify-between">
                  <span className="text-xs uppercase font-medium tracking-wider text-texto2">
                    Utilización de Flota (LE-070)
                  </span>
                  <span className="font-mono font-bold text-texto">
                    {state.kpis.overallUtilizationPercentage}%
                  </span>
                </div>

                <div className="space-y-2 pt-1 text-[11px]">
                  <div>
                    <div className="flex justify-between text-texto2">
                      <span className="text-azul font-medium">Autos:</span>
                      <span className="font-mono">{state.kpis.utilizationByType.AUTO.active} / {state.kpis.utilizationByType.AUTO.total} activos</span>
                    </div>
                    <div className="w-full bg-panel2 h-1.5 rounded-full overflow-hidden mt-1 border border-borde/40">
                      <div
                        className="h-full bg-azul"
                        style={{ width: `${state.kpis.utilizationByType.AUTO.percentage}%` }}
                      />
                    </div>
                  </div>

                  <div>
                    <div className="flex justify-between text-texto2">
                      <span className="text-violeta font-medium">Motos:</span>
                      <span className="font-mono">{state.kpis.utilizationByType.MOTO.active} / {state.kpis.utilizationByType.MOTO.total} activos</span>
                    </div>
                    <div className="w-full bg-panel2 h-1.5 rounded-full overflow-hidden mt-1 border border-borde/40">
                      <div
                        className="h-full bg-violeta"
                        style={{ width: `${state.kpis.utilizationByType.MOTO.percentage}%` }}
                      />
                    </div>
                  </div>

                  <div>
                    <div className="flex justify-between text-texto2">
                      <span className="text-mint font-medium">Bicicletas:</span>
                      <span className="font-mono">{state.kpis.utilizationByType.BICICLETA.active} / {state.kpis.utilizationByType.BICICLETA.total} activos</span>
                    </div>
                    <div className="w-full bg-panel2 h-1.5 rounded-full overflow-hidden mt-1 border border-borde/40">
                      <div
                        className="h-full bg-mint"
                        style={{ width: `${state.kpis.utilizationByType.BICICLETA.percentage}%` }}
                      />
                    </div>
                  </div>
                </div>
              </div>

              {/* Distance and Cost Breakdown (LE-068, LE-069) */}
              <IndicadorDesglosado
                titulo="Distancia Recorrida (LE-069)"
                valorConsolidado={`${state.kpis.totalDistanceKm.toFixed(1)}`}
                unidad="km"
                filas={[
                  { label: 'Autos (40 km/h)', value: `${state.kpis.distanceByType.AUTO.toFixed(1)} km`, colorClass: 'text-azul' },
                  { label: 'Motos (25 km/h)', value: `${state.kpis.distanceByType.MOTO.toFixed(1)} km`, colorClass: 'text-violeta' },
                  { label: 'Bicicletas (12 km/h)', value: `${state.kpis.distanceByType.BICICLETA.toFixed(1)} km`, colorClass: 'text-mint' },
                ]}
                contexto="Suma de trayectos ortogonales en la retícula"
              />

              <IndicadorDesglosado
                titulo="Costo Operativo Acumulado (LE-068)"
                valorConsolidado={`S/ ${state.kpis.totalCostSoles.toFixed(2)}`}
                filas={[
                  { label: 'Autos (S/ 8.00/km)', value: `S/ ${state.kpis.costByType.AUTO.toFixed(2)}`, colorClass: 'text-azul' },
                  { label: 'Motos (S/ 6.00/km)', value: `S/ ${state.kpis.costByType.MOTO.toFixed(2)}`, colorClass: 'text-violeta' },
                  { label: 'Bicicletas (S/ 3.00/km)', value: `S/ ${state.kpis.costByType.BICICLETA.toFixed(2)}`, colorClass: 'text-mint' },
                ]}
                contexto="Evaluación de la función objetivo metaheurística"
              />

              {/* Active Incidents List (LE-065) */}
              <div className="bg-panel border border-borde rounded-md p-3 space-y-2">
                <div className="flex items-center justify-between">
                  <span className="text-xs uppercase font-medium tracking-wider text-texto2">
                    Incidencias Activas (LE-065)
                  </span>
                  <span className="font-mono text-rojo font-bold">
                    {state.incidents.filter((i) => i.active).length}
                  </span>
                </div>

                <div className="space-y-1.5 pt-1">
                  {state.incidents.map((inc) => (
                    <div
                      key={inc.id}
                      onClick={() => setActiveIncidentModal(inc)}
                      className="p-2 rounded bg-panel2 border border-borde/70 hover:border-texto2/60 cursor-pointer transition-colors"
                    >
                      <div className="flex items-center justify-between">
                        <span className="font-medium text-texto text-[11px] truncate flex items-center gap-1.5">
                          {inc.type === 'BLOQUEO_VIA' ? (
                            <ShieldAlert className="w-3.5 h-3.5 text-ambar shrink-0" />
                          ) : (
                            <AlertTriangle className="w-3.5 h-3.5 text-rojo shrink-0" />
                          )}
                          <span>{inc.title}</span>
                        </span>
                        <span className="font-mono text-[10px] text-texto2">{inc.timestamp}</span>
                      </div>
                      <div className="text-[10px] text-mint mt-1 flex items-center justify-between">
                        <span>Ver detalle replanificación</span>
                        <ExternalLink className="w-3 h-3 text-texto2" />
                      </div>
                    </div>
                  ))}
                </div>
              </div>
            </div>
          )}
        </div>
      </div>

      {/* Selected Vehicle or Order Floating Inspector */}
      {selectedVehicle && (
        <div className="absolute bottom-12 left-1/2 -translate-x-1/2 z-30 bg-panel2 border border-mint rounded-lg p-3 shadow-2xl flex items-center gap-4 text-xs animate-in fade-in slide-in-from-bottom-2">
          <div className="flex items-center gap-2">
            <span className="w-2.5 h-2.5 rounded-full bg-mint" />
            <span className="font-mono font-bold text-texto">{selectedVehicle.plate}</span>
            <span className="text-texto2 font-mono">({selectedVehicle.type})</span>
          </div>
          <div className="border-l border-borde/60 pl-3">
            <span className="text-texto2">Conductor: </span>
            <span className="text-texto font-medium">{selectedVehicle.driverName}</span>
          </div>
          <div className="border-l border-borde/60 pl-3 font-mono">
            <span className="text-texto2">Carga: </span>
            <span className="text-mint font-semibold">{selectedVehicle.currentLoad} / {selectedVehicle.maxCapacity} pkgs</span>
          </div>
          <div className="border-l border-borde/60 pl-3 font-mono">
            <span className="text-texto2">Destino: </span>
            <span className="text-ambar font-semibold">{selectedVehicle.destinationType || 'EN ESPERA'}</span>
          </div>
          <button
            onClick={() => setSelectedVehicleId(null)}
            className="text-texto2 hover:text-texto ml-2 px-1 text-sm font-bold"
          >
            ×
          </button>
        </div>
      )}

      {/* Selected Order Floating Inspector */}
      {selectedOrder && !selectedVehicle && (
        <div className="absolute bottom-12 left-1/2 -translate-x-1/2 z-30 bg-panel2 border border-ambar rounded-lg p-3 shadow-2xl flex items-center gap-4 text-xs">
          <div className="flex items-center gap-2">
            <span className="w-2.5 h-2.5 rounded-full bg-ambar" />
            <span className="font-mono font-bold text-texto">{selectedOrder.id}</span>
            <span className="text-texto2 font-mono">({selectedOrder.clientCode})</span>
          </div>
          <div className="border-l border-borde/60 pl-3 font-mono">
            <span className="text-texto2">Cant: </span>
            <span className="text-texto font-semibold">{selectedOrder.deliveredQuantity} / {selectedOrder.quantity} pkgs</span>
          </div>
          <div className="border-l border-borde/60 pl-3">
            <SemaforoBadge nivel={selectedOrder.criticality} slackHours={selectedOrder.slackHours} />
          </div>
          <button
            onClick={() => setSelectedOrder(null)}
            className="text-texto2 hover:text-texto ml-2 px-1 text-sm font-bold"
          >
            ×
          </button>
        </div>
      )}

      {/* Modals */}
      <DetalleIncidenciaModal
        incident={activeIncidentModal}
        onClose={() => setActiveIncidentModal(null)}
        vehicles={state.vehicles}
        orders={state.orders}
      />

      <ColapsoModal
        report={showCollapseModal ? state.collapseReport || null : null}
        onClose={() => setShowCollapseModal(false)}
        onReset={onReset}
      />

      <CierreSimulacionModal
        isOpen={showClosureModal}
        onClose={() => setShowClosureModal(false)}
        state={state}
        onReset={onReset}
      />

      {/* Input File Format Guide Modal */}
      {showFileFormatModal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-bg/80 backdrop-blur-sm">
          <div className="w-full max-w-lg bg-panel2 border border-borde rounded-lg p-4 space-y-3">
            <div className="flex items-center justify-between border-b border-borde pb-2">
              <h3 className="text-sm font-bold text-texto uppercase">Estructura de Archivos de Entrada</h3>
              <button
                onClick={() => setShowFileFormatModal(false)}
                className="text-texto2 hover:text-texto text-sm font-bold"
              >
                ×
              </button>
            </div>
            <div className="text-xs space-y-3 text-texto2">
              <div>
                <span className="text-texto font-semibold block">1. Archivo de Pedidos (ventasaaaamm.txt):</span>
                <code className="block bg-panel p-2 rounded font-mono text-[11px] text-mint mt-1">
                  ##d##h##m:posX,posY,cIdCliente,qq,hl
                </code>
                <p className="mt-1 text-[11px]">
                  Ejemplo: <code className="text-texto">11d13h31m:45,43,c9167,12,36</code> (día 11 a las 13:31, nodo 45,43, cliente c9167, 12 paquetes, deadline 36 horas).
                </p>
              </div>

              <div>
                <span className="text-texto font-semibold block">2. Archivo de Bloqueos (aaaamm.bloqueadas):</span>
                <code className="block bg-panel p-2 rounded font-mono text-[11px] text-mint mt-1">
                  ##d##h##m-##d##h##m:x1,y1,x2,y2
                </code>
                <p className="mt-1 text-[11px]">
                  Ejemplo: <code className="text-texto">01d06h00m-01d15h00m:31,21,34,21</code> (bloqueo entre los nodos 31,21 y 34,21 del día 1 de 06:00 a 15:00).
                </p>
              </div>
            </div>
            <div className="flex justify-end pt-2">
              <Boton variant="secundario" onClick={() => setShowFileFormatModal(false)}>
                Entendido
              </Boton>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};
