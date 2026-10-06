/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
 */

import React, { useState, useEffect } from 'react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { useSimulation } from './hooks/useSimulation';
import { ensureSession } from './services/sessionService';
import { SelectorEscenario } from './pages/SelectorEscenario';
import { EscenarioDashboard } from './pages/EscenarioDashboard';
import { ScenarioType, CriticalityThresholds, MetaheuristicParams } from './types/logistics';

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 5000,
      retry: 1,
    },
  },
});

type AppView = 'SELECTOR' | 'DASHBOARD';

function MainApp() {
  const [currentView, setCurrentView] = useState<AppView>('SELECTOR');
  useEffect(() => {
    ensureSession().catch(() => {});
  }, []);
  const {
    state,
    isLoading,
    startSimulationAsync,
    resetSimulation,
    pauseSimulation,
    resumeSimulation,
    reportIncident,
  } = useSimulation();

  const handleStartScenario = async (
    scenario: ScenarioType,
    thresholds: CriticalityThresholds,
    metaheuristic: MetaheuristicParams,
    initialMonth: string
  ) => {
    // ponytail: navegar con el estado ya arrancado, no con el anterior
    try {
      await startSimulationAsync({
        scenario,
        startDateTime: '2026-09-01T08:30:00Z',
        initialMonth,
        thresholds,
        metaheuristic,
      });
    } finally {
      setCurrentView('DASHBOARD');
    }
  };

  const handleReset = () => {
    resetSimulation();
  };

  if (isLoading || !state) {
    return (
      <div className="h-screen w-screen bg-bg flex items-center justify-center text-texto font-mono text-sm">
        <div className="flex flex-col items-center gap-3">
          <div className="w-8 h-8 border-2 border-mint border-t-transparent rounded-full animate-spin" />
          <span>Iniciando Componente Visualizador SisRap...</span>
        </div>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-bg text-texto">
      {currentView === 'SELECTOR' && (
        <SelectorEscenario
          onStartScenario={handleStartScenario}
        />
      )}

      {currentView === 'DASHBOARD' && (
        <EscenarioDashboard
          state={state}
          onReset={handleReset}
          onBackToSelector={() => setCurrentView('SELECTOR')}
          onReportIncident={reportIncident}
          onPause={pauseSimulation}
          onResume={resumeSimulation}
        />
      )}
    </div>
  );
}

export default function App() {
  return (
    <QueryClientProvider client={queryClient}>
      <MainApp />
    </QueryClientProvider>
  );
}
