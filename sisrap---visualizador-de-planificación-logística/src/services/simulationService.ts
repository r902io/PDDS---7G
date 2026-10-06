import {
  ScenarioType,
  SimulationState,
  CriticalityThresholds,
  MetaheuristicParams,
} from '../types/logistics';
import { StartScenarioRequest, CreateIncidentRequest } from '../types/api';
import { apiRequest } from './apiClient';
import { simulationEngine } from './mockBackendEngine';

export const simulationService = {
  /**
   * Get current simulation state from server or internal engine
   */
  async getState(): Promise<SimulationState> {
    const res = await apiRequest<SimulationState>('/simulation/state');
    if (res.success && res.data) {
      return res.data;
    }
    return simulationEngine.getState();
  },

  /**
   * Start a scenario (DIA_A_DIA, SIMULACION_5D, COLAPSO_LOGISTICO)
   */
  async startScenario(req: StartScenarioRequest): Promise<SimulationState> {
    const res = await apiRequest<SimulationState>('/simulation/start', {
      method: 'POST',
      body: JSON.stringify(req),
    });

    if (res.success && res.data) {
      return res.data;
    }

    simulationEngine.setScenario(req.scenario);
    simulationEngine.setThresholds(req.thresholds);
    simulationEngine.setMetaheuristic(req.metaheuristic);
    simulationEngine.start();
    return simulationEngine.getState();
  },

  /**
   * Reset simulation to initial parameters
   */
  async resetSimulation(): Promise<SimulationState> {
    await apiRequest('/simulation/reset', { method: 'POST' });
    simulationEngine.reset();
    return simulationEngine.getState();
  },

  /**
   * Update criticality thresholds
   */
  async updateThresholds(thresholds: CriticalityThresholds): Promise<void> {
    await apiRequest('/simulation/thresholds', {
      method: 'PUT',
      body: JSON.stringify(thresholds),
    });
    simulationEngine.setThresholds(thresholds);
  },

  /**
   * Report an incident (breakdown or road block)
   */
  async reportIncident(req: CreateIncidentRequest): Promise<void> {
    await apiRequest('/incidents', {
      method: 'POST',
      body: JSON.stringify(req),
    });

    if (req.type === 'AVERIA_UNIDAD' && req.vehicleId) {
      simulationEngine.reportBreakdown(req.vehicleId, 'MODERADA');
    } else if (req.type === 'BLOQUEO_VIA' && req.roadSegment) {
      simulationEngine.addRoadBlock(
        { x: req.roadSegment.fromX, y: req.roadSegment.fromY },
        { x: req.roadSegment.toX, y: req.roadSegment.toY },
        req.description
      );
    }
  },

  /**
   * Subscribe to local engine updates
   */
  subscribe(callback: (state: SimulationState) => void): () => void {
    return simulationEngine.subscribe(callback);
  },
};
