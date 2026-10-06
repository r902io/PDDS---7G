import { useState, useEffect, useCallback } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import {
  SimulationState,
  ScenarioType,
  CriticalityThresholds,
  MetaheuristicParams,
} from '../types/logistics';
import { simulationService } from '../services/simulationService';

export function useSimulation(initialScenario: ScenarioType = 'DIA_A_DIA') {
  const queryClient = useQueryClient();
  const [localState, setLocalState] = useState<SimulationState | null>(null);

  // TanStack Query to fetch state
  const { data: serverState, isLoading, error } = useQuery<SimulationState>({
    queryKey: ['simulationState'],
    queryFn: () => simulationService.getState(),
    refetchOnWindowFocus: false,
  });

  // Subscribe to real-time simulation updates
  useEffect(() => {
    if (serverState && !localState) {
      setLocalState(serverState);
    }

    const unsubscribe = simulationService.subscribe((updatedState) => {
      setLocalState(updatedState);
      queryClient.setQueryData(['simulationState'], updatedState);
    });

    return () => {
      unsubscribe();
    };
  }, [serverState, queryClient]);

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
    resetSimulation: resetMutation.mutate,
    updateThresholds: updateThresholdsMutation.mutate,
    reportIncident: reportIncidentMutation.mutate,
  };
}
