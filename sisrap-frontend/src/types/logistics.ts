export type ScenarioType = 'DIA_A_DIA' | 'SIMULACION_5D' | 'COLAPSO_LOGISTICO';

export type ShiftType = 'MANANA' | 'TARDE' | 'NOCHE';

export type VehicleType = 'AUTO' | 'MOTO' | 'BICICLETA';

export type VehicleStatus = 'DISPONIBLE' | 'EN_RUTA' | 'AVERIADO' | 'MANTENIMIENTO' | 'REFRIGERIO';

export type OrderStatus = 'REGISTRADO' | 'ASIGNADO' | 'EN_RUTA' | 'ENTREGADO' | 'CRITICO' | 'COLAPSADO';

export type CriticalityLevel = 'VERDE' | 'AMBAR' | 'ROJO';

export interface Coordinate {
  x: number;
  y: number;
}

export interface RoadSegment {
  from: Coordinate;
  to: Coordinate;
}

export interface Warehouse {
  id: string;
  name: string;
  code: 'AC' | 'A1' | 'A2';
  coord: Coordinate;
  capacityMax: number; // Infinite (represented by Infinity or 999999) for AC, 1000 for A1, A2
  currentStock: number;
  isCentral: boolean;
  lastRefillTime?: string;
}

export interface VehicleConfig {
  type: VehicleType;
  label: string;
  capacity: number; // pkgs (Auto: 24, Moto: 8, Bici: 4)
  speedKmH: number; // km/h (Auto: 40, Moto: 25, Bici: 12)
  costPerKm: number; // S/ per km (Auto: 8, Moto: 6, Bici: 3)
  colorToken: string; // azul, violeta, mint
}

export interface Vehicle {
  id: string;
  plate: string;
  type: VehicleType;
  status: VehicleStatus;
  coord: Coordinate;
  currentWarehouseId: string;
  assignedOrderIds: string[];
  currentLoad: number;
  maxCapacity: number;
  speedKmH: number;
  costPerKm: number;
  currentShift: ShiftType;
  driverName: string;
  isTakingBreak: boolean; // 1-hour lunch break
  breakWindowStartHour: number;
  breakWindowEndHour: number;
  breakTaken: boolean;
  distanceTraveledKm: number;
  accumulatedCostSoles: number;
  // Route history and pending nodes
  traveledPath: Coordinate[];
  pendingPath: Coordinate[];
  currentTargetCoord?: Coordinate;
  destinationType?: 'CLIENTE' | 'ALMACEN';
  hasAlternativeRoute?: boolean; // Highlighted amber halo when detour around block
  breakdownDetails?: {
    type: 'LEVE' | 'MODERADA' | 'GRAVE';
    reportedAt: string;
    estimatedReturnTime: string;
    reassignedOrders: string[];
  };
}

export interface OrderItemPartial {
  vehicleId: string;
  quantity: number;
  delivered: boolean;
  deliveredAt?: string;
}

export interface Order {
  id: string;
  clientCode: string;
  clientName: string;
  coord: Coordinate;
  quantity: number;
  registeredAt: string;
  deadlineHours: 4 | 8 | 12 | 18 | 36;
  deadlineTimestamp: string;
  status: OrderStatus;
  slackHours: number; // Remaining time until deadline
  criticality: CriticalityLevel;
  deliveredAt?: string;
  dispatchedAt?: string;
  assignedVehicleId?: string;
  assignedWarehouseId?: string;
  // Partial deliveries support (LE-028)
  partials?: OrderItemPartial[];
  deliveredQuantity: number;
  isCollapsingOrder?: boolean;
}

export interface RoadBlock {
  id: string;
  from: Coordinate;
  to: Coordinate;
  startDateTime: string; // e.g. "01d06h00m"
  endDateTime: string;   // e.g. "01d15h00m"
  active: boolean;
  description: string;
}

export interface Incident {
  id: string;
  type: 'BLOQUEO_VIA' | 'AVERIA_UNIDAD';
  title: string;
  timestamp: string;
  location: Coordinate;
  affectedVehicleId?: string;
  roadSegment?: RoadSegment;
  impactedOrders: string[];
  alternativeSolution: string;
  costDifferenceKm: number;
  costDifferenceSoles: number;
  active: boolean;
}

export interface CriticalityThresholds {
  greenMinHours: number; // > 6h
  amberMinHours: number; // 2h - 6h
  redMinHours: number;   // < 2h
}

export interface SimulationClock {
  simulatedDateTime: string; // ISO or formatted
  simulatedDay: number;      // 1..5, etc.
  formattedTime: string;    // HH:MM:SS
  formattedDate: string;    // DD/MM/YYYY
  elapsedRealSeconds: number;
  elapsedSimulatedHours: number;
  currentShift: ShiftType;
}

export interface FleetUtilization {
  type: VehicleType;
  total: number;
  active: number;
  percentage: number;
}

export interface FleetKPIs {
  totalDeliveredOnTime: number;
  totalFailedDeadlines: number;
  minSlackHours: number;
  minSlackOrderId: string;
  minSlackClientCode: string;
  totalDistanceKm: number;
  totalCostSoles: number;
  distanceByType: Record<VehicleType, number>;
  costByType: Record<VehicleType, number>;
  utilizationByType: Record<VehicleType, FleetUtilization>;
  overallUtilizationPercentage: number;
  activeIncidentsCount: number;
}

export interface DailyKPIEvolutionPoint {
  day: number;
  deliveredOrdersAccum: number;
  costAccumSoles: number;
  minSlackHours: number;
  distanceAccumKm: number;
}

export interface CollapseReport {
  isCollapsed: boolean;
  isDataMissing: boolean; // Distinction between logistical collapse and missing input file
  timestamp: string;
  simulatedDay: number;
  collapsingOrderId?: string;
  collapsingOrderClientCode?: string;
  orderDeadline?: string;
  estimatedArrival?: string;
  cause: 'SATURACION_DEMANDA' | 'INCIDENCIA_AVERIA' | 'BLOQUEO_INSOLUBLE' | 'FALTA_ARCHIVO_MES';
  details: string;
  totalDeliveriesCompleted: number;
  totalDaysReached: number;
}

export interface MonthlyFileRecord {
  filename: string;
  type: 'PEDIDOS' | 'BLOQUEOS';
  monthYear: string; // e.g. "202608", "202609"
  status: 'DISPONIBLE' | 'FALTANTE' | 'INVALIDO';
  recordCount: number;
  uploadedAt?: string;
}

export interface MetaheuristicParams {
  algorithm: 'SIMULATED_ANNEALING' | 'GENETIC_ALGORITHM' | 'TABU_SEARCH';
  iterations: number;
  populationSize: number;
  seed: number;
  maxComputeTimeSec: number;
}

export interface SimulationState {
  scenario: ScenarioType;
  isRunning: boolean;
  isPaused: boolean; // Prohibited from UI playback, but represents engine completion
  isFinished: boolean;
  hasCollapsed: boolean;
  clock: SimulationClock;
  warehouses: Warehouse[];
  vehicles: Vehicle[];
  orders: Order[];
  roadBlocks: RoadBlock[];
  incidents: Incident[];
  kpis: FleetKPIs;
  thresholds: CriticalityThresholds;
  collapseReport?: CollapseReport;
  dailyEvolution: DailyKPIEvolutionPoint[];
  monthlyFiles: MonthlyFileRecord[];
  metaheuristic: MetaheuristicParams;
}
