import React, { useState } from 'react';
import {
  ScenarioType,
  CriticalityThresholds,
  MetaheuristicParams,
} from '../types/logistics';
import { Panel } from '../components/ui/Panel';
import { Boton } from '../components/ui/Boton';
import { Campo } from '../components/ui/Campo';
import {
  Play,
  Clock,
  Calendar,
  AlertOctagon,
  Database,
  Layers,
  Settings,
  FileText,
  Sliders,
  CheckCircle2,
} from 'lucide-react';

interface SelectorEscenarioProps {
  onStartScenario: (
    scenario: ScenarioType,
    thresholds: CriticalityThresholds,
    metaheuristic: MetaheuristicParams,
    initialMonth: string
  ) => void;
}

export const SelectorEscenario: React.FC<SelectorEscenarioProps> = ({
  onStartScenario,
}) => {
  const [selectedScenario, setSelectedScenario] = useState<ScenarioType>('DIA_A_DIA');
  const [initialMonth, setInitialMonth] = useState('202609');

  // Criticality traffic light thresholds
  const [greenMinHours, setGreenMinHours] = useState(6.0);
  const [amberMinHours, setAmberMinHours] = useState(2.0);

  // Metaheuristic configuration
  const [algorithm, setAlgorithm] = useState<'SIMULATED_ANNEALING' | 'GENETIC_ALGORITHM' | 'TABU_SEARCH'>(
    'SIMULATED_ANNEALING'
  );
  const [iterations, setIterations] = useState(2500);
  const [populationSize, setPopulationSize] = useState(50);
  const [maxComputeTimeSec, setMaxComputeTimeSec] = useState(45);
  const [seed, setSeed] = useState(42);

  const handleStart = () => {
    onStartScenario(
      selectedScenario,
      {
        greenMinHours,
        amberMinHours,
        redMinHours: 0,
      },
      {
        algorithm,
        iterations,
        populationSize,
        seed,
        maxComputeTimeSec,
      },
      initialMonth
    );
  };

  return (
    <div className="min-h-screen bg-bg text-texto flex flex-col justify-between p-6">
      <div className="max-w-6xl mx-auto w-full space-y-6">
        {/* Header */}
        <div className="flex flex-col md:flex-row items-start md:items-center justify-between border-b border-borde pb-5 gap-4">
          <div>
            <div className="flex items-center gap-3">
              <span className="px-2.5 py-1 bg-mint/15 text-mint border border-mint/30 rounded text-xs font-mono font-bold tracking-wider">
                SISRAP v3.0
              </span>
              <span className="text-xs text-texto2 font-mono">PUCP • GRUPO 7G (H0981)</span>
            </div>
            <h1 className="text-2xl font-bold text-texto mt-1 tracking-tight">
              Visualizador de Planificación Logística Metaheurística
            </h1>
            <p className="text-sm text-texto2 mt-0.5">
              Monitoreo operativo de flota y red vial para PaqRap • Retícula 70x50 km (3 621 nodos)
            </p>
          </div>

          <div className="flex items-center gap-2">
            <span className="px-3 py-1.5 rounded bg-panel border border-borde text-xs font-mono text-texto2">
              AC (27,14) • A1 (12,38) • A2 (57,27)
            </span>
          </div>
        </div>

        {/* Step 1: Scenario Cards */}
        <div>
          <div className="flex items-center gap-2 mb-3">
            <span className="flex items-center justify-center w-5 h-5 rounded-full bg-mint/20 text-mint text-xs font-bold">
              1
            </span>
            <h2 className="text-sm font-semibold uppercase tracking-wider text-texto">
              Selección del Escenario de Operación
            </h2>
          </div>

          <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
            {/* Escenario 1: Día a Día */}
            <div
              onClick={() => setSelectedScenario('DIA_A_DIA')}
              className={`p-5 rounded-lg border cursor-pointer transition-all ${
                selectedScenario === 'DIA_A_DIA'
                  ? 'bg-panel2 border-mint ring-1 ring-mint'
                  : 'bg-panel border-borde hover:border-texto2/50'
              }`}
            >
              <div className="flex items-start justify-between">
                <div className="p-2 rounded bg-mint/10 text-mint">
                  <Clock className="w-6 h-6" />
                </div>
                {selectedScenario === 'DIA_A_DIA' && (
                  <CheckCircle2 className="w-5 h-5 text-mint shrink-0" />
                )}
              </div>
              <h3 className="text-base font-semibold text-texto mt-3">Operación Día a Día</h3>
              <div className="mt-1 flex items-center gap-1.5 text-xs text-texto2">
                <span className="w-2 h-2 rounded-full bg-mint inline-block" />
                <span>Tiempo real • Llegada continua</span>
              </div>
              <p className="text-xs text-texto2 mt-2 leading-relaxed">
                Ejecución continua en tiempo real. Los pedidos ingresan conforme se registran dinámicamente.
                No tiene fecha de término predefinida.
              </p>
              <div className="mt-4 pt-3 border-t border-borde/60 space-y-1.5 text-[11px] text-texto2">
                <div>
                  <span className="text-texto font-medium">Condición de Término:</span> Sin fecha fin
                </div>
                <div>
                  <span className="text-texto font-medium">Datos exigidos:</span> Archivo mes en curso
                </div>
              </div>
            </div>

            {/* Escenario 2: Simulación 5D */}
            <div
              onClick={() => setSelectedScenario('SIMULACION_5D')}
              className={`p-5 rounded-lg border cursor-pointer transition-all ${
                selectedScenario === 'SIMULACION_5D'
                  ? 'bg-panel2 border-mint ring-1 ring-mint'
                  : 'bg-panel border-borde hover:border-texto2/50'
              }`}
            >
              <div className="flex items-start justify-between">
                <div className="p-2 rounded bg-azul/10 text-azul">
                  <Calendar className="w-6 h-6" />
                </div>
                {selectedScenario === 'SIMULACION_5D' && (
                  <CheckCircle2 className="w-5 h-5 text-mint shrink-0" />
                )}
              </div>
              <h3 className="text-base font-semibold text-texto mt-3">Simulación de Cinco Días</h3>
              <div className="mt-1 flex items-center gap-1.5 text-xs text-texto2">
                <span className="w-2 h-2 rounded-full bg-azul inline-block" />
                <span>120 horas simuladas (30-60 min)</span>
              </div>
              <p className="text-xs text-texto2 mt-2 leading-relaxed">
                Simula 5 jornadas completas de operación. Se conocen de antemano la fecha de inicio y fin.
                Consolida indicadores diarios y curva de evolución.
              </p>
              <div className="mt-4 pt-3 border-t border-borde/60 space-y-1.5 text-[11px] text-texto2">
                <div>
                  <span className="text-texto font-medium">Condición de Término:</span> Fin de 120h (Día 5)
                </div>
                <div>
                  <span className="text-texto font-medium">Datos exigidos:</span> Archivos del periodo completo
                </div>
              </div>
            </div>

            {/* Escenario 3: Colapso Logístico */}
            <div
              onClick={() => setSelectedScenario('COLAPSO_LOGISTICO')}
              className={`p-5 rounded-lg border cursor-pointer transition-all ${
                selectedScenario === 'COLAPSO_LOGISTICO'
                  ? 'bg-panel2 border-mint ring-1 ring-mint'
                  : 'bg-panel border-borde hover:border-texto2/50'
              }`}
            >
              <div className="flex items-start justify-between">
                <div className="p-2 rounded bg-rojo/10 text-rojo">
                  <AlertOctagon className="w-6 h-6" />
                </div>
                {selectedScenario === 'COLAPSO_LOGISTICO' && (
                  <CheckCircle2 className="w-5 h-5 text-mint shrink-0" />
                )}
              </div>
              <h3 className="text-base font-semibold text-texto mt-3">Colapso Logístico</h3>
              <div className="mt-1 flex items-center gap-1.5 text-xs text-texto2">
                <span className="w-2 h-2 rounded-full bg-rojo inline-block" />
                <span>Estrés • Primer retraso</span>
              </div>
              <p className="text-xs text-texto2 mt-2 leading-relaxed">
                Prueba de estrés que corre hasta el primer incumplimiento de un plazo comprometido.
                Detiene el reloj de inmediato e identifica el pedido que originó el colapso.
              </p>
              <div className="mt-4 pt-3 border-t border-borde/60 space-y-1.5 text-[11px] text-texto2">
                <div>
                  <span className="text-texto font-medium">Condición de Término:</span> Primer deadline incumplido
                </div>
                <div>
                  <span className="text-texto font-medium">Datos exigidos:</span> Mes inicial (posteriores a demanda)
                </div>
              </div>
            </div>
          </div>
        </div>

        {/* Step 2: Configuration & Parameters */}
        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          {/* Criticality Traffic Light */}
          <Panel
            titulo="Semáforo de Criticidad (LE-062)"
            subtitulo="Configuración de umbrales sobre el tiempo restante hasta el plazo"
          >
            <div className="space-y-3">
              <div className="grid grid-cols-2 gap-3">
                <Campo
                  label="Umbral Verde (Conforme)"
                  type="number"
                  step="0.5"
                  value={greenMinHours}
                  onChange={(e) => setGreenMinHours(parseFloat(e.target.value) || 0)}
                  unidad="horas"
                  nota="Holgura > umbral"
                />
                <Campo
                  label="Umbral Ámbar (Alerta)"
                  type="number"
                  step="0.5"
                  value={amberMinHours}
                  onChange={(e) => setAmberMinHours(parseFloat(e.target.value) || 0)}
                  unidad="horas"
                  nota="Entre ámbar y verde"
                />
              </div>

              <div className="p-2.5 rounded bg-panel border border-borde/70 flex items-center justify-between text-xs">
                <div className="flex items-center gap-2">
                  <span className="w-2.5 h-2.5 rounded-full bg-sem-rojo inline-block" />
                  <span className="text-texto font-medium">Zona Roja (Crítico / Inminente):</span>
                </div>
                <span className="font-mono text-sem-rojo">&lt; {amberMinHours.toFixed(1)} horas</span>
              </div>

              <p className="text-[11px] text-texto2 italic">
                Regla de diseño: Los colores del semáforo siempre se acompañan de su valor o etiqueta
                para garantizar accesibilidad en capturas impresas.
              </p>
            </div>
          </Panel>

          {/* Metaheuristic Algorithm */}
          <Panel
            titulo="Motor Metaheurístico (LE-040, LE-041)"
            subtitulo="Configuración del algoritmo de optimización de rutas"
          >
            <div className="space-y-3">
              <div className="flex flex-col space-y-1">
                <label className="text-xs text-texto2">Algoritmo Planificador</label>
                <select
                  value={algorithm}
                  onChange={(e) => setAlgorithm(e.target.value as any)}
                  className="w-full bg-bg border border-borde rounded-md font-mono text-sm px-3 py-1.5 text-texto focus:border-mint focus:outline-none"
                >
                  <option value="SIMULATED_ANNEALING">Simulated Annealing (Recocido Simulado)</option>
                  <option value="GENETIC_ALGORITHM">Algoritmo Genético (AG)</option>
                  <option value="TABU_SEARCH">Búsqueda Tabú (Tabu Search)</option>
                </select>
              </div>

              <div className="grid grid-cols-2 gap-3">
                <Campo
                  label="Máx Iteraciones"
                  type="number"
                  value={iterations}
                  onChange={(e) => setIterations(parseInt(e.target.value, 10) || 1000)}
                />
                <Campo
                  label="Límite Cómputo"
                  type="number"
                  value={maxComputeTimeSec}
                  onChange={(e) => setMaxComputeTimeSec(parseInt(e.target.value, 10) || 30)}
                  unidad="seg"
                  nota="LE-042 (30-60m)"
                />
              </div>

              <div className="grid grid-cols-2 gap-3">
                <Campo
                  label="Semilla Aleatoria"
                  type="number"
                  value={seed}
                  onChange={(e) => setSeed(parseInt(e.target.value, 10) || 0)}
                  nota="LE-050 Reproducibilidad"
                />
                <Campo
                  label="Mes Inicial Archivos"
                  value={initialMonth}
                  onChange={(e) => setInitialMonth(e.target.value)}
                  nota="AAAAMM"
                />
              </div>
            </div>
          </Panel>
        </div>

        {/* Network & Fleet Specs Summary */}
        <div className="bg-panel2 border border-borde rounded-lg p-4">
          <div className="flex items-center gap-2 mb-2 text-xs font-semibold uppercase tracking-wider text-texto">
            <Layers className="w-4 h-4 text-mint" />
            <span>Resumen de Parámetros de Red y Flota (Reglas PUCP SisRap)</span>
          </div>

          <div className="grid grid-cols-1 md:grid-cols-4 gap-3 text-xs">
            <div className="bg-panel p-2.5 rounded border border-borde/60">
              <span className="text-texto2 block text-[11px]">Retícula de la Ciudad</span>
              <span className="font-mono font-semibold text-texto">70 × 50 km (3 621 nodos)</span>
              <span className="text-[10px] text-texto2 block mt-0.5">Calles de doble sentido</span>
            </div>

            <div className="bg-panel p-2.5 rounded border border-borde/60">
              <span className="text-texto2 block text-[11px]">Almacén Central (AC)</span>
              <span className="font-mono font-semibold text-sky-400">Coord (27, 14) • Stock ∞</span>
              <span className="text-[10px] text-texto2 block mt-0.5">Cian / Blanco destacado (No rojo)</span>
            </div>

            <div className="bg-panel p-2.5 rounded border border-borde/60">
              <span className="text-texto2 block text-[11px]">Almacenes Intermedios</span>
              <span className="font-mono font-semibold text-mint">A1: (12,38) • A2: (57,27)</span>
              <span className="text-[10px] text-texto2 block mt-0.5">Color Mint • 1 000 u. c/u</span>
            </div>

            <div className="bg-panel p-2.5 rounded border border-borde/60">
              <span className="text-texto2 block text-[11px]">Flota y Turnos</span>
              <span className="font-mono font-semibold text-texto">Autos, Motos, Bicicletas</span>
              <span className="text-[10px] text-texto2 block mt-0.5">3 turnos de 8h • 1h refrigerio</span>
            </div>
          </div>
        </div>

        {/* Action Button: Strictly ONLY ONE primary button per view (Estándar GUI v01) */}
        <div className="flex items-center justify-end pt-2 pb-4">
          <Boton
            variant="primario"
            icono={<Play className="w-4 h-4" />}
            onClick={handleStart}
            className="px-6 py-2.5 text-base"
          >
            Iniciar Corrida del Escenario Seleccionado
          </Boton>
        </div>
      </div>
    </div>
  );
};
