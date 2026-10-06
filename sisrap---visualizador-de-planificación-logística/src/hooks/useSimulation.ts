import { useState, useEffect, useCallback } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import {
  SimulationState,
  ScenarioType,
  CriticalityThresholds,
  MetaheuristicParams,
} from '../types/logistics';
import { simulationService } from '../services/simulationService';
import { ApiError } from '../services/apiClient';

// ponytail: 403 = sim ajena; mensaje accionable en vez de "Forbidden" crudo
function controlErrorMsg(e: unknown): string | null {
  if (!e) return null;
  if (e instanceof ApiError && e.status === 403) {
    return 'Solo el dueño de la corrida puede pausarla/reanudarla';
  }
  return (e as Error)?.message ?? 'Error de red';
}

export function useSimulation(initialScenario: ScenarioType = 'DIA_A_DIA') {
  const queryClient = useQueryClient();
  const [localState, setLocalState] = useState<SimulationState | null>(null);

  // TanStack Query to fetch state
  const { data: serverState, isLoading, error } = useQuery<SimulationState>({
    queryKey: ['simulationState'],
    queryFn: () => simulationService.getState(),
    refetchOnWindowFocus: false,
  });

  // Subscribe once to real-time simulation updates (no re-suscribir por update)
  useEffect(() => {
    const unsubscribe = simulationService.subscribe((updatedState) => {
      setLocalState(updatedState);
      queryClient.setQueryData(['simulationState'], updatedState);
    });
    return () => {
      unsubscribe();
    };
  }, [queryClient]);

  // Hidratacion inicial desde el servidor solo si aun no hay estado local
  useEffect(() => {
    if (serverState && !localState) {
      setLocalState(serverState);
    }
  }, [serverState]); // eslint-disable-line react-hooks/exhaustive-deps

  const currentState = localState || serverState;

  // Mutations
  const startMutation = useMutation({
    mutationFn: (params: {
      scenario: ScenarioType;
      startDateTime: string;
      initialMonth: string;
      thresholds: CriticalityThresholds;
      metaheuristic: MetaheuristicParams;
    }) => simulationService.startScenario(params),
    onSuccess: (data) => {
      setLocalState(data);
      queryClient.setQueryData(['simulationState'], data);
    },
  });

  const resetMutation = useMutation({
    mutationFn: () => simulationService.resetSimulation(),
    onSuccess: (data) => {
      setLocalState(data);
      queryClient.setQueryData(['simulationState'], data);
    },
  });

  const pauseMutation = useMutation({
    mutationFn: () => simulationService.pause(),
    onSuccess: async () => {
      const fresh = await simulationService.getState();
      setLocalState(fresh);
      queryClient.setQueryData(['simulationState'], fresh);
    },
  });

  const resumeMutation = useMutation({
    mutationFn: () => simulationService.resume(),
    onSuccess: async () => {
      const fresh = await simulationService.getState();
      setLocalState(fresh);
      queryClient.setQueryData(['simulationState'], fresh);
    },
  });

  const updateThresholdsMutation = useMutation({
    mutationFn: (thresholds: CriticalityThresholds) =>
      simulationService.updateThresholds(thresholds),
  });

  const reportIncidentMutation = useMutation({
    mutationFn: (incidentReq: Parameters<typeof simulationService.reportIncident>[0]) =>
      simulationService.reportIncident(incidentReq),
  });

  return {
    state: currentState,
    isLoading: isLoading && !currentState,
    error,
    startSimulation: startMutation.mutate,
    startSimulationAsync: startMutation.mutateAsync,
    resetSimulation: resetMutation.mutate,
    pauseSimulation: pauseMutation.mutate,
    resumeSimulation: resumeMutation.mutate,
    isPausing: pauseMutation.isPending,
    isResuming: resumeMutation.isPending,
    pauseErrorMsg: controlErrorMsg(pauseMutation.error),
    resumeErrorMsg: controlErrorMsg(resumeMutation.error),
    updateThresholds: updateThresholdsMutation.mutate,
    reportIncident: reportIncidentMutation.mutate,
  };
}
