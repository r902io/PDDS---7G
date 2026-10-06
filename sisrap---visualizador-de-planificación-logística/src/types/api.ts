import {
  ScenarioType,
  SimulationState,
  Order,
  Vehicle,
  Warehouse,
  RoadBlock,
  Incident,
  CollapseReport,
  CriticalityThresholds,
  MetaheuristicParams,
} from './logistics';

export interface ApiResponse<T> {
  success: boolean;
  data: T;
  message?: string;
  error?: string;
  timestamp: string;
}

export interface StartScenarioRequest {
  scenario: ScenarioType;
  startDateTime: string;
  initialMonth: string;
  thresholds: CriticalityThresholds;
  metaheuristic: MetaheuristicParams;
}

export interface SimulationTickResponse {
  state: SimulationState;
  events?: Array<{
    type: 'DISPATCH' | 'DELIVERY' | 'INCIDENT' | 'SHIFT_CHANGE' | 'RELOAD_WAREHOUSE' | 'COLLAPSE';
    description: string;
    timestamp: string;
  }>;
}

export interface FileUploadResponse {
  filename: string;
  type: 'PEDIDOS' | 'BLOQUEOS';
  monthYear: string;
  parsedRecords: number;
  isValid: boolean;
  validationErrors?: string[];
}

export interface CreateOrderRequest {
  clientCode: string;
  clientName: string;
  coordX: number;
  coordY: number;
  quantity: number;
  deadlineHours: 4 | 8 | 12 | 18 | 36;
}

export interface CreateIncidentRequest {
  type: 'BLOQUEO_VIA' | 'AVERIA_UNIDAD';
  vehicleId?: string;
  roadSegment?: {
    fromX: number;
    fromY: number;
    toX: number;
    toY: number;
  };
  durationHours?: number;
  description: string;
}
