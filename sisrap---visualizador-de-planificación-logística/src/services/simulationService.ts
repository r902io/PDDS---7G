import {
  SimulationState,
} from '../types/logistics';
import { StartScenarioRequest, CreateIncidentRequest } from '../types/api';
import { API_BASE, ApiError, apiFetch } from './apiClient';
import { ensureSession } from './sessionService';
import {
  AlmacenOperativo,
  BloqueoRespuesta,
  PedidoOperativo,
  SnapshotSimulacion,
  VehiculoOperativo,
  mapScenarioToBackend,
  snapshotToState,
} from './backendMappers';
import { simulationEngine } from './mockBackendEngine';

let lastState: SimulationState | null = null;

async function fetchBackendState(): Promise<SimulationState> {
  const [snap, vehiculos, almacenes, bloqueos, pag] = await Promise.all([
    apiFetch<SnapshotSimulacion>('/api/simulacion/estado', {}, false),
    apiFetch<VehiculoOperativo[]>('/api/vehiculos', {}, false),
    apiFetch<AlmacenOperativo[]>('/api/almacenes', {}, false),
    apiFetch<BloqueoRespuesta[]>('/api/bloqueos', {}, false),
    apiFetch<{ contenido: PedidoOperativo[] }>('/api/pedidos?pagina=0&tamanio=200', {}, false),
  ]);
  lastState = snapshotToState(snap, vehiculos, pag.contenido, almacenes, bloqueos, lastState);
  return lastState;
}

export const simulationService = {
  async getState(): Promise<SimulationState> {
    try {
      return await fetchBackendState();
    } catch {
      return simulationEngine.getState();
    }
  },

  async startScenario(req: StartScenarioRequest): Promise<SimulationState> {
    await ensureSession();
    // ponytail: AAAAMM validado; si viene roto se usa el startDateTime en vez de mandar 400
    const fechaInicio = /^\d{6}$/.test(req.initialMonth ?? '')
      ? `${req.initialMonth.slice(0, 4)}-${req.initialMonth.slice(4, 6)}-01`
      : req.startDateTime.slice(0, 10);
    try {
      await apiFetch('/api/simulacion/iniciar', {
        method: 'POST',
        body: JSON.stringify({
          escenario: mapScenarioToBackend(req.scenario),
          fechaInicio,
          semilla: req.metaheuristic?.seed ?? 42,
        }),
      });
    } catch (e) {
      // ponytail: 409 = ya hay una corriendo, 403 = otro dueno; engancharse en vez de fallar
      if (!(e instanceof ApiError && (e.status === 409 || e.status === 403))) throw e;
    }
    try {
      return await fetchBackendState();
    } catch {
      simulationEngine.setScenario(req.scenario);
      simulationEngine.setThresholds(req.thresholds);
      simulationEngine.setMetaheuristic(req.metaheuristic);
      simulationEngine.start();
      return simulationEngine.getState();
    }
  },

  async resetSimulation(): Promise<SimulationState> {
    try {
      await ensureSession();
      await apiFetch('/api/simulacion/detener', { method: 'POST' });
    } catch {
      // sin dueno o sin backend: solo resetea local
    }
    simulationEngine.reset();
    try {
      return await fetchBackendState();
    } catch {
      return simulationEngine.getState();
    }
  },

  async pause(): Promise<void> {
    await apiFetch('/api/simulacion/pausar', { method: 'POST' });
  },

  async resume(): Promise<void> {
    await apiFetch('/api/simulacion/reanudar', { method: 'POST' });
  },

  async updateThresholds(thresholds: SimulationState['thresholds']): Promise<void> {
    // ponytail: sin endpoint backend, solo UI local
    simulationEngine.setThresholds(thresholds);
  },

  async reportIncident(req: CreateIncidentRequest): Promise<void> {
    await ensureSession();
    const now = new Date().toISOString().slice(0, 19);
    if (req.type === 'AVERIA_UNIDAD' && req.vehicleId) {
      await apiFetch(`/api/vehiculos/${req.vehicleId}/averias`, {
        method: 'POST',
        body: JSON.stringify({ tipoAveria: 'TIPO2_INTERMEDIA', fechaOcurrencia: now }),
      });
      simulationEngine.reportBreakdown(req.vehicleId, 'MODERADA');
    } else if (req.type === 'BLOQUEO_VIA' && req.roadSegment) {
      const fin = new Date(Date.now() + 2 * 3600000).toISOString().slice(0, 19);
      await apiFetch('/api/bloqueos', {
        method: 'POST',
        body: JSON.stringify({
          inicio: now,
          fin,
          vertices: [
            { x: req.roadSegment.fromX, y: req.roadSegment.fromY },
            { x: req.roadSegment.toX, y: req.roadSegment.toY },
          ],
        }),
      });
      simulationEngine.addRoadBlock(
        { x: req.roadSegment.fromX, y: req.roadSegment.fromY },
        { x: req.roadSegment.toX, y: req.roadSegment.toY },
        req.description
      );
    }
  },

  subscribe(callback: (state: SimulationState) => void): () => void {
    const unsubMock = simulationEngine.subscribe(callback);
    let es: EventSource | null = null;
    try {
      es = new EventSource(`${API_BASE}/api/simulacion/eventos`);
      es.onmessage = () => {
        fetchBackendState().then(callback).catch(() => {});
      };
      // ponytail: sin reintento infinito si el stream cae; el mock sigue vivo
      es.onerror = () => es?.close();
    } catch {
      // sin SSE: solo mock
    }
    return () => {
      unsubMock();
      es?.close();
    };
  },
};
