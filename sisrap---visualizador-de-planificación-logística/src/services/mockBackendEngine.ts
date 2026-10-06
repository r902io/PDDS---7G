import {
  Coordinate,
  Order,
  Vehicle,
  Warehouse,
  RoadBlock,
  Incident,
  SimulationState,
  ScenarioType,
  ShiftType,
  FleetKPIs,
  DailyKPIEvolutionPoint,
  CollapseReport,
  CriticalityThresholds,
  MetaheuristicParams,
  VehicleType,
} from '../types/logistics';

// Updated warehouse coordinates as explicitly instructed:
// Central AC: (27, 14)
// Nor-Oeste A1: (12, 38)
// Este A2: (57, 27)
export const INITIAL_WAREHOUSES: Warehouse[] = [
  {
    id: 'wh-ac',
    name: 'Almacén Central',
    code: 'AC',
    coord: { x: 27, y: 14 },
    capacityMax: Infinity,
    currentStock: 999999,
    isCentral: true,
  },
  {
    id: 'wh-a1',
    name: 'Almacén Intermedio Nor-Oeste',
    code: 'A1',
    coord: { x: 12, y: 38 },
    capacityMax: 1000,
    currentStock: 1000,
    isCentral: false,
    lastRefillTime: '00:00:00',
  },
  {
    id: 'wh-a2',
    name: 'Almacén Intermedio Este',
    code: 'A2',
    coord: { x: 57, y: 27 },
    capacityMax: 1000,
    currentStock: 1000,
    isCentral: false,
    lastRefillTime: '00:00:00',
  },
];

export const INITIAL_FLEET_CONFIG = {
  AUTO: { capacity: 24, speedKmH: 40, costPerKm: 8.0, label: 'Auto' },
  MOTO: { capacity: 8, speedKmH: 25, costPerKm: 6.0, label: 'Moto' },
  BICICLETA: { capacity: 4, speedKmH: 12, costPerKm: 3.0, label: 'Bicicleta' },
};

export const INITIAL_VEHICLES: Vehicle[] = [
  // Autos
  {
    id: 'v-auto-01',
    plate: 'AUT-101',
    type: 'AUTO',
    status: 'DISPONIBLE',
    coord: { x: 27, y: 14 },
    currentWarehouseId: 'wh-ac',
    assignedOrderIds: [],
    currentLoad: 0,
    maxCapacity: 24,
    speedKmH: 40,
    costPerKm: 8.0,
    currentShift: 'MANANA',
    driverName: 'Carlos Mendoza (T1)',
    isTakingBreak: false,
    breakWindowStartHour: 11,
    breakWindowEndHour: 12,
    breakTaken: false,
    distanceTraveledKm: 0,
    accumulatedCostSoles: 0,
    traveledPath: [],
    pendingPath: [],
  },
  {
    id: 'v-auto-02',
    plate: 'AUT-102',
    type: 'AUTO',
    status: 'DISPONIBLE',
    coord: { x: 27, y: 14 },
    currentWarehouseId: 'wh-ac',
    assignedOrderIds: [],
    currentLoad: 0,
    maxCapacity: 24,
    speedKmH: 40,
    costPerKm: 8.0,
    currentShift: 'MANANA',
    driverName: 'Lucía Fernández (T1)',
    isTakingBreak: false,
    breakWindowStartHour: 12,
    breakWindowEndHour: 13,
    breakTaken: false,
    distanceTraveledKm: 0,
    accumulatedCostSoles: 0,
    traveledPath: [],
    pendingPath: [],
  },
  {
    id: 'v-auto-03',
    plate: 'AUT-103',
    type: 'AUTO',
    status: 'DISPONIBLE',
    coord: { x: 12, y: 38 },
    currentWarehouseId: 'wh-a1',
    assignedOrderIds: [],
    currentLoad: 0,
    maxCapacity: 24,
    speedKmH: 40,
    costPerKm: 8.0,
    currentShift: 'MANANA',
    driverName: 'Jorge Quispe (T1)',
    isTakingBreak: false,
    breakWindowStartHour: 10,
    breakWindowEndHour: 11,
    breakTaken: false,
    distanceTraveledKm: 0,
    accumulatedCostSoles: 0,
    traveledPath: [],
    pendingPath: [],
  },
  // Motos
  {
    id: 'v-moto-01',
    plate: 'MOT-201',
    type: 'MOTO',
    status: 'DISPONIBLE',
    coord: { x: 27, y: 14 },
    currentWarehouseId: 'wh-ac',
    assignedOrderIds: [],
    currentLoad: 0,
    maxCapacity: 8,
    speedKmH: 25,
    costPerKm: 6.0,
    currentShift: 'MANANA',
    driverName: 'Raúl Sánchez (T1)',
    isTakingBreak: false,
    breakWindowStartHour: 11,
    breakWindowEndHour: 12,
    breakTaken: false,
    distanceTraveledKm: 0,
    accumulatedCostSoles: 0,
    traveledPath: [],
    pendingPath: [],
  },
  {
    id: 'v-moto-02',
    plate: 'MOT-202',
    type: 'MOTO',
    status: 'DISPONIBLE',
    coord: { x: 57, y: 27 },
    currentWarehouseId: 'wh-a2',
    assignedOrderIds: [],
    currentLoad: 0,
    maxCapacity: 8,
    speedKmH: 25,
    costPerKm: 6.0,
    currentShift: 'MANANA',
    driverName: 'Sofía Benítez (T1)',
    isTakingBreak: false,
    breakWindowStartHour: 13,
    breakWindowEndHour: 14,
    breakTaken: false,
    distanceTraveledKm: 0,
    accumulatedCostSoles: 0,
    traveledPath: [],
    pendingPath: [],
  },
  {
    id: 'v-moto-03',
    plate: 'MOT-203',
    type: 'MOTO',
    status: 'DISPONIBLE',
    coord: { x: 12, y: 38 },
    currentWarehouseId: 'wh-a1',
    assignedOrderIds: [],
    currentLoad: 0,
    maxCapacity: 8,
    speedKmH: 25,
    costPerKm: 6.0,
    currentShift: 'MANANA',
    driverName: 'Marco Polo (T1)',
    isTakingBreak: false,
    breakWindowStartHour: 12,
    breakWindowEndHour: 13,
    breakTaken: false,
    distanceTraveledKm: 0,
    accumulatedCostSoles: 0,
    traveledPath: [],
    pendingPath: [],
  },
  // Bicicletas
  {
    id: 'v-bici-01',
    plate: 'BIC-301',
    type: 'BICICLETA',
    status: 'DISPONIBLE',
    coord: { x: 27, y: 14 },
    currentWarehouseId: 'wh-ac',
    assignedOrderIds: [],
    currentLoad: 0,
    maxCapacity: 4,
    speedKmH: 12,
    costPerKm: 3.0,
    currentShift: 'MANANA',
    driverName: 'David Silva (T1)',
    isTakingBreak: false,
    breakWindowStartHour: 12,
    breakWindowEndHour: 13,
    breakTaken: false,
    distanceTraveledKm: 0,
    accumulatedCostSoles: 0,
    traveledPath: [],
    pendingPath: [],
  },
  {
    id: 'v-bici-02',
    plate: 'BIC-302',
    type: 'BICICLETA',
    status: 'DISPONIBLE',
    coord: { x: 57, y: 27 },
    currentWarehouseId: 'wh-a2',
    assignedOrderIds: [],
    currentLoad: 0,
    maxCapacity: 4,
    speedKmH: 12,
    costPerKm: 3.0,
    currentShift: 'MANANA',
    driverName: 'Elena Torres (T1)',
    isTakingBreak: false,
    breakWindowStartHour: 11,
    breakWindowEndHour: 12,
    breakTaken: false,
    distanceTraveledKm: 0,
    accumulatedCostSoles: 0,
    traveledPath: [],
    pendingPath: [],
  },
];

export const INITIAL_ORDERS: Order[] = [
  {
    id: 'ord-101',
    clientCode: 'c9167',
    clientName: 'Distribuidora San Mateo',
    coord: { x: 45, y: 43 },
    quantity: 12,
    registeredAt: '01/09/2026 07:15:00',
    deadlineHours: 36,
    deadlineTimestamp: '02/09/2026 19:15:00',
    status: 'ASIGNADO',
    slackHours: 32.5,
    criticality: 'VERDE',
    assignedVehicleId: 'v-auto-01',
    assignedWarehouseId: 'wh-ac',
    deliveredQuantity: 0,
    partials: [{ vehicleId: 'v-auto-01', quantity: 12, delivered: false }],
  },
  {
    id: 'ord-102',
    clientCode: 'c4021',
    clientName: 'Farmacias del Sol',
    coord: { x: 18, y: 35 },
    quantity: 6,
    registeredAt: '01/09/2026 07:30:00',
    deadlineHours: 8,
    deadlineTimestamp: '01/09/2026 15:30:00',
    status: 'EN_RUTA',
    slackHours: 6.8,
    criticality: 'VERDE',
    assignedVehicleId: 'v-moto-03',
    assignedWarehouseId: 'wh-a1',
    deliveredQuantity: 0,
  },
  {
    id: 'ord-103',
    clientCode: 'c7720',
    clientName: 'Bodega Central Norte',
    coord: { x: 15, y: 46 },
    quantity: 4,
    registeredAt: '01/09/2026 07:45:00',
    deadlineHours: 4,
    deadlineTimestamp: '01/09/2026 11:45:00',
    status: 'EN_RUTA',
    slackHours: 2.9,
    criticality: 'AMBAR',
    assignedVehicleId: 'v-auto-03',
    assignedWarehouseId: 'wh-a1',
    deliveredQuantity: 0,
  },
  {
    id: 'ord-104',
    clientCode: 'c1204',
    clientName: 'Minimarket Los Laureles',
    coord: { x: 62, y: 25 },
    quantity: 3,
    registeredAt: '01/09/2026 08:00:00',
    deadlineHours: 4,
    deadlineTimestamp: '01/09/2026 12:00:00',
    status: 'EN_RUTA',
    slackHours: 1.8,
    criticality: 'ROJO',
    assignedVehicleId: 'v-moto-02',
    assignedWarehouseId: 'wh-a2',
    deliveredQuantity: 0,
  },
  {
    id: 'ord-105',
    clientCode: 'c8842',
    clientName: 'Comercial Andina',
    coord: { x: 34, y: 18 },
    quantity: 18,
    registeredAt: '01/09/2026 08:10:00',
    deadlineHours: 18,
    deadlineTimestamp: '02/09/2026 02:10:00',
    status: 'ASIGNADO',
    slackHours: 16.5,
    criticality: 'VERDE',
    assignedVehicleId: 'v-auto-02',
    assignedWarehouseId: 'wh-ac',
    deliveredQuantity: 0,
  },
  {
    id: 'ord-106',
    clientCode: 'c3319',
    clientName: 'Almacenes Perú Express',
    coord: { x: 28, y: 16 },
    quantity: 32, // Exceeds 24 -> partial split (Auto 24 + Moto 8)
    registeredAt: '01/09/2026 08:20:00',
    deadlineHours: 12,
    deadlineTimestamp: '01/09/2026 20:20:00',
    status: 'ASIGNADO',
    slackHours: 10.4,
    criticality: 'VERDE',
    assignedVehicleId: 'v-auto-01',
    assignedWarehouseId: 'wh-ac',
    deliveredQuantity: 0,
    partials: [
      { vehicleId: 'v-auto-01', quantity: 24, delivered: false },
      { vehicleId: 'v-moto-01', quantity: 8, delivered: false },
    ],
  },
];

export const INITIAL_ROAD_BLOCKS: RoadBlock[] = [
  {
    id: 'blk-01',
    from: { x: 31, y: 21 },
    to: { x: 34, y: 21 },
    startDateTime: '01d06h00m',
    endDateTime: '01d15h00m',
    active: true,
    description: 'Cierre por obras viales municipales en Av. Industrial',
  },
  {
    id: 'blk-02',
    from: { x: 14, y: 36 },
    to: { x: 18, y: 36 },
    startDateTime: '02d08h00m',
    endDateTime: '02d18h00m',
    active: false,
    description: 'Mantenimiento de tuberías troncales Nor-Oeste',
  },
];

// Calculate Manhattan distance (only horizontal + vertical allowed)
export function getManhattanDistance(p1: Coordinate, p2: Coordinate): number {
  return Math.abs(p1.x - p2.x) + Math.abs(p1.y - p2.y);
}

// Generate orthogonal waypoint path between two coordinates
export function getManhattanPath(from: Coordinate, to: Coordinate): Coordinate[] {
  const path: Coordinate[] = [];
  let currX = from.x;
  let currY = from.y;

  // Move horizontally first
  const stepX = to.x >= currX ? 1 : -1;
  while (currX !== to.x) {
    currX += stepX;
    path.push({ x: currX, y: currY });
  }

  // Move vertically
  const stepY = to.y >= currY ? 1 : -1;
  while (currY !== to.y) {
    currY += stepY;
    path.push({ x: currX, y: currY });
  }

  return path;
}

export class SimulationEngine {
  private state: SimulationState;
  private intervalId: any = null;
  private subscribers: Array<(state: SimulationState) => void> = [];

  constructor(scenario: ScenarioType = 'DIA_A_DIA') {
    this.state = this.createInitialState(scenario);
  }

  public createInitialState(scenario: ScenarioType): SimulationState {
    const thresholds: CriticalityThresholds = {
      greenMinHours: 6.0,
      amberMinHours: 2.0,
      redMinHours: 0.0,
    };

    const metaheuristic: MetaheuristicParams = {
      algorithm: 'SIMULATED_ANNEALING',
      iterations: 2500,
      populationSize: 50,
      seed: 42,
      maxComputeTimeSec: 45,
    };

    // Pre-initialize initial paths for vehicles with orders
    const vehicles = JSON.parse(JSON.stringify(INITIAL_VEHICLES)) as Vehicle[];
    const orders = JSON.parse(JSON.stringify(INITIAL_ORDERS)) as Order[];
    const warehouses = JSON.parse(JSON.stringify(INITIAL_WAREHOUSES)) as Warehouse[];

    // Assign sample routes
    vehicles[0].assignedOrderIds = ['ord-101'];
    vehicles[0].status = 'EN_RUTA';
    vehicles[0].currentTargetCoord = { x: 45, y: 43 };
    vehicles[0].destinationType = 'CLIENTE';
    vehicles[0].pendingPath = getManhattanPath(vehicles[0].coord, { x: 45, y: 43 });
    vehicles[0].traveledPath = [{ x: 27, y: 14 }];

    vehicles[3].assignedOrderIds = ['ord-102'];
    vehicles[3].status = 'EN_RUTA';
    vehicles[3].currentTargetCoord = { x: 18, y: 35 };
    vehicles[3].destinationType = 'CLIENTE';
    vehicles[3].pendingPath = getManhattanPath(vehicles[3].coord, { x: 18, y: 35 });

    vehicles[4].assignedOrderIds = ['ord-104'];
    vehicles[4].status = 'EN_RUTA';
    vehicles[4].currentTargetCoord = { x: 62, y: 25 };
    vehicles[4].destinationType = 'CLIENTE';
    vehicles[4].pendingPath = getManhattanPath(vehicles[4].coord, { x: 62, y: 25 });

    const kpis: FleetKPIs = {
      totalDeliveredOnTime: 14,
      totalFailedDeadlines: 0,
      minSlackHours: 1.8,
      minSlackOrderId: 'ord-104',
      minSlackClientCode: 'c1204',
      totalDistanceKm: 142.5,
      totalCostSoles: 984.0,
      distanceByType: { AUTO: 84.5, MOTO: 42.0, BICICLETA: 16.0 },
      costByType: { AUTO: 676.0, MOTO: 252.0, BICICLETA: 48.0 },
      utilizationByType: {
        AUTO: { type: 'AUTO', total: 3, active: 2, percentage: 66.7 },
        MOTO: { type: 'MOTO', total: 3, active: 2, percentage: 66.7 },
        BICICLETA: { type: 'BICICLETA', total: 2, active: 0, percentage: 0.0 },
      },
      overallUtilizationPercentage: 50.0,
      activeIncidentsCount: 1,
    };

    const dailyEvolution: DailyKPIEvolutionPoint[] = [
      { day: 1, deliveredOrdersAccum: 14, costAccumSoles: 984, minSlackHours: 1.8, distanceAccumKm: 142.5 },
    ];

    const monthlyFiles = [
      { filename: 'ventas202609.txt', type: 'PEDIDOS' as const, monthYear: '202609', status: 'DISPONIBLE' as const, recordCount: 842, uploadedAt: '01/09/2026 06:00' },
      { filename: '202609.bloqueadas', type: 'BLOQUEOS' as const, monthYear: '202609', status: 'DISPONIBLE' as const, recordCount: 12, uploadedAt: '01/09/2026 06:00' },
    ];

    return {
      scenario,
      isRunning: false,
      isPaused: false,
      isFinished: false,
      hasCollapsed: false,
      clock: {
        simulatedDateTime: '2026-09-01T08:30:00Z',
        simulatedDay: 1,
        formattedTime: '08:30:00',
        formattedDate: '01/09/2026',
        elapsedRealSeconds: 0,
        elapsedSimulatedHours: 1.5,
        currentShift: 'MANANA',
      },
      warehouses,
      vehicles,
      orders,
      roadBlocks: INITIAL_ROAD_BLOCKS,
      incidents: [
        {
          id: 'inc-blk-01',
          type: 'BLOQUEO_VIA',
          title: 'Bloqueo en tramo (31,21) - (34,21)',
          timestamp: '08:00:00',
          location: { x: 31, y: 21 },
          roadSegment: { from: { x: 31, y: 21 }, to: { x: 34, y: 21 } },
          impactedOrders: ['ord-101'],
          alternativeSolution: 'Desvío por calle Y=23 (+2 km recorrido alternativo)',
          costDifferenceKm: 2.0,
          costDifferenceSoles: 16.0,
          active: true,
        },
      ],
      kpis,
      thresholds,
      dailyEvolution,
      monthlyFiles,
      metaheuristic,
    };
  }

  public getState(): SimulationState {
    return this.state;
  }

  public subscribe(fn: (state: SimulationState) => void): () => void {
    this.subscribers.push(fn);
    return () => {
      this.subscribers = this.subscribers.filter(s => s !== fn);
    };
  }

  private notify() {
    this.subscribers.forEach(cb => cb({ ...this.state }));
  }

  public setScenario(scenario: ScenarioType) {
    if (this.state.isRunning) return; // Cannot change scenario during execution
    this.state = this.createInitialState(scenario);
    this.notify();
  }

  public setThresholds(thresholds: CriticalityThresholds) {
    this.state.thresholds = thresholds;
    this.updateOrderCriticality();
    this.notify();
  }

  public setMetaheuristic(params: MetaheuristicParams) {
    this.state.metaheuristic = params;
    this.notify();
  }

  public start() {
    if (this.state.isRunning || this.state.isFinished || this.state.hasCollapsed) return;
    this.state.isRunning = true;
    this.notify();

    // Internal automatic clock progression (Prohibited to expose speed change buttons!)
    const TICK_INTERVAL_MS = 1000;
    this.intervalId = setInterval(() => {
      this.tick();
    }, TICK_INTERVAL_MS);
  }

  public stop() {
    if (this.intervalId) {
      clearInterval(this.intervalId);
      this.intervalId = null;
    }
    this.state.isRunning = false;
    this.notify();
  }

  public reset() {
    this.stop();
    this.state = this.createInitialState(this.state.scenario);
    this.notify();
  }

  // Add an order directly from UI / maintenance
  public addOrder(order: Order) {
    this.state.orders.push(order);
    this.updateOrderCriticality();
    this.notify();
  }

  // Register vehicle breakdown incident (LE-079, LE-080, LE-081, LE-082)
  public reportBreakdown(vehicleId: string, type: 'LEVE' | 'MODERADA' | 'GRAVE') {
    const vehicle = this.state.vehicles.find(v => v.id === vehicleId);
    if (!vehicle || vehicle.status === 'AVERIADO') return;

    vehicle.status = 'AVERIADO';
    const hoursUnavailable = type === 'LEVE' ? 2 : type === 'MODERADA' ? 6 : 24;
    const estReturn = `${this.state.clock.simulatedDay}d +${hoursUnavailable}h`;

    const affectedOrderIds = [...vehicle.assignedOrderIds];
    vehicle.breakdownDetails = {
      type,
      reportedAt: this.state.clock.formattedTime,
      estimatedReturnTime: estReturn,
      reassignedOrders: affectedOrderIds,
    };

    // Reassign orders to available vehicle with highest criticality priority
    const availableVehicle = this.state.vehicles.find(
      v => v.id !== vehicleId && v.status === 'DISPONIBLE'
    );

    if (availableVehicle && affectedOrderIds.length > 0) {
      availableVehicle.assignedOrderIds.push(...affectedOrderIds);
      availableVehicle.status = 'EN_RUTA';
      availableVehicle.currentTargetCoord = vehicle.coord;
      availableVehicle.pendingPath = getManhattanPath(availableVehicle.coord, vehicle.coord);
    }

    const incident: Incident = {
      id: `inc-brk-${Date.now()}`,
      type: 'AVERIA_UNIDAD',
      title: `Avería de unidad ${vehicle.plate} (${type})`,
      timestamp: this.state.clock.formattedTime,
      location: { ...vehicle.coord },
      affectedVehicleId: vehicle.id,
      impactedOrders: affectedOrderIds,
      alternativeSolution: availableVehicle
        ? `Carga reasignada de urgencia a ${availableVehicle.plate}`
        : 'Pendiente de asignación por saturación de flota',
      costDifferenceKm: 4.0,
      costDifferenceSoles: 32.0,
      active: true,
    };

    this.state.incidents.unshift(incident);
    this.state.kpis.activeIncidentsCount = this.state.incidents.filter(i => i.active).length;
    this.notify();
  }

  // Register manual or scheduled road block (LE-076)
  public addRoadBlock(from: Coordinate, to: Coordinate, desc: string) {
    const block: RoadBlock = {
      id: `blk-${Date.now()}`,
      from,
      to,
      startDateTime: `${this.state.clock.simulatedDay}d${this.state.clock.formattedTime}`,
      endDateTime: `${this.state.clock.simulatedDay}d23h59m`,
      active: true,
      description: desc,
    };

    this.state.roadBlocks.push(block);

    const incident: Incident = {
      id: `inc-${block.id}`,
      type: 'BLOQUEO_VIA',
      title: `Bloqueo vial entre (${from.x},${from.y}) y (${to.x},${to.y})`,
      timestamp: this.state.clock.formattedTime,
      location: from,
      roadSegment: { from, to },
      impactedOrders: [],
      alternativeSolution: 'Arco excluido del grafo; replanificación inmediata con desvío ortogonal',
      costDifferenceKm: 3.5,
      costDifferenceSoles: 28.0,
      active: true,
    };

    this.state.incidents.unshift(incident);
    this.state.kpis.activeIncidentsCount = this.state.incidents.filter(i => i.active).length;
    this.notify();
  }

  // Simulation tick logic
  private tick() {
    if (!this.state.isRunning || this.state.hasCollapsed || this.state.isFinished) return;

    // Advance clock by 10 simulated minutes per tick
    this.advanceClock(10);

    // Update fleet positions along Manhattan paths
    this.advanceFleet();

    // Check lunch breaks (1 hour in intermediate window)
    this.checkBreaksAndShifts();

    // Check warehouse instant refill daily at 23:59:59 (LE-012)
    this.checkWarehouseRefill();

    // Update order criticality & slack
    this.updateOrderCriticality();

    // Check termination conditions for current scenario
    this.checkTerminationConditions();

    // Update KPIs
    this.updateKPIs();

    this.notify();
  }

  private advanceClock(minutes: number) {
    const { clock } = this.state;
    clock.elapsedRealSeconds += 1;
    clock.elapsedSimulatedHours += minutes / 60;

    const [hoursStr, minsStr, secsStr] = clock.formattedTime.split(':');
    let totalMinutes = parseInt(hoursStr, 10) * 60 + parseInt(minsStr, 10) + minutes;

    let day = clock.simulatedDay;
    if (totalMinutes >= 24 * 60) {
      day += Math.floor(totalMinutes / (24 * 60));
      totalMinutes %= 24 * 60;
    }

    const currentHour = Math.floor(totalMinutes / 60);
    const currentMin = totalMinutes % 60;
    clock.simulatedDay = day;
    clock.formattedTime = `${String(currentHour).padStart(2, '0')}:${String(currentMin).padStart(2, '0')}:00`;

    // Determine current shift:
    // Mañana: 07:00 - 15:00
    // Tarde: 15:00 - 23:00
    // Noche: 23:00 - 07:00
    if (currentHour >= 7 && currentHour < 15) {
      clock.currentShift = 'MANANA';
    } else if (currentHour >= 15 && currentHour < 23) {
      clock.currentShift = 'TARDE';
    } else {
      clock.currentShift = 'NOCHE';
    }
  }

  private advanceFleet() {
    for (const vehicle of this.state.vehicles) {
      if (vehicle.status === 'AVERIADO' || vehicle.isTakingBreak) {
        continue; // Vehicle immobilized during breakdown or 1h lunch
      }

      if (vehicle.pendingPath.length > 0) {
        const nextNode = vehicle.pendingPath.shift()!;
        vehicle.traveledPath.push({ ...vehicle.coord });
        vehicle.coord = nextNode;

        const kmStep = 1; // 1 km grid unit
        vehicle.distanceTraveledKm += kmStep;
        vehicle.accumulatedCostSoles += kmStep * vehicle.costPerKm;

        // Check if vehicle arrived at current target
        if (vehicle.pendingPath.length === 0) {
          if (vehicle.destinationType === 'CLIENTE') {
            // Deliver assigned orders at this node
            const deliveredOrders = this.state.orders.filter(
              o => o.coord.x === vehicle.coord.x && o.coord.y === vehicle.coord.y && o.status === 'EN_RUTA'
            );
            deliveredOrders.forEach(o => {
              o.status = 'ENTREGADO';
              o.deliveredAt = `${this.state.clock.formattedDate} ${this.state.clock.formattedTime}`;
              o.deliveredQuantity = o.quantity;
            });

            // Return to nearest warehouse or next delivery
            const nextWarehouse = this.getNearestWarehouse(vehicle.coord);
            vehicle.destinationType = 'ALMACEN';
            vehicle.currentTargetCoord = nextWarehouse.coord;
            vehicle.pendingPath = getManhattanPath(vehicle.coord, nextWarehouse.coord);
          } else if (vehicle.destinationType === 'ALMACEN') {
            vehicle.status = 'DISPONIBLE';
            vehicle.assignedOrderIds = [];
            vehicle.currentLoad = 0;
            vehicle.currentTargetCoord = undefined;
          }
        }
      }
    }
  }

  private checkBreaksAndShifts() {
    const [h] = this.state.clock.formattedTime.split(':').map(Number);
    for (const vehicle of this.state.vehicles) {
      // Relevo de conductor (driver relay on shift transition without interrupting movement)
      if (vehicle.currentShift !== this.state.clock.currentShift) {
        vehicle.currentShift = this.state.clock.currentShift;
        const shiftNum = vehicle.currentShift === 'MANANA' ? 'T1' : vehicle.currentShift === 'TARDE' ? 'T2' : 'T3';
        vehicle.driverName = `${vehicle.driverName.split(' ')[0]} Relevo (${shiftNum})`;
        vehicle.breakTaken = false;
      }

      // Mandatory 1-hour break in intermediate window (not 1st or last hour of shift)
      if (h >= vehicle.breakWindowStartHour && h < vehicle.breakWindowEndHour && !vehicle.breakTaken) {
        vehicle.isTakingBreak = true;
      } else if (vehicle.isTakingBreak && h >= vehicle.breakWindowEndHour) {
        vehicle.isTakingBreak = false;
        vehicle.breakTaken = true;
      }
    }
  }

  // Daily instant reload of intermediate warehouses at 23:59:59 (LE-012)
  private checkWarehouseRefill() {
    const time = this.state.clock.formattedTime;
    if (time.startsWith('23:5')) {
      for (const wh of this.state.warehouses) {
        if (!wh.isCentral) {
          wh.currentStock = 1000;
          wh.lastRefillTime = '23:59:59';
        }
      }
    }
  }

  private updateOrderCriticality() {
    let minSlack = Infinity;
    let minOrderId = '';
    let minClient = '';

    for (const order of this.state.orders) {
      if (order.status === 'ENTREGADO') continue;

      // Slack decreases with simulated hours
      order.slackHours = Math.max(0, Number((order.slackHours - 0.16).toFixed(2)));

      if (order.slackHours >= this.state.thresholds.greenMinHours) {
        order.criticality = 'VERDE';
      } else if (order.slackHours >= this.state.thresholds.amberMinHours) {
        order.criticality = 'AMBAR';
      } else {
        order.criticality = 'ROJO';
      }

      if (order.slackHours < minSlack) {
        minSlack = order.slackHours;
        minOrderId = order.id;
        minClient = order.clientCode;
      }
    }

    this.state.kpis.minSlackHours = minSlack === Infinity ? 0 : minSlack;
    this.state.kpis.minSlackOrderId = minOrderId;
    this.state.kpis.minSlackClientCode = minClient;
  }

  private checkTerminationConditions() {
    const { scenario, clock, orders } = this.state;

    // 1. Simulación 5D: Finishes at 120 simulated hours
    if (scenario === 'SIMULACION_5D') {
      if (clock.elapsedSimulatedHours >= 120) {
        this.state.isFinished = true;
        this.stop();
        return;
      }
    }

    // 2. Colapso Logístico: Terminates on the FIRST missed deadline (slack <= 0)
    if (scenario === 'COLAPSO_LOGISTICO') {
      const failingOrder = orders.find(o => o.status !== 'ENTREGADO' && o.slackHours <= 0);
      if (failingOrder) {
        failingOrder.status = 'COLAPSADO';
        failingOrder.isCollapsingOrder = true;
        this.state.hasCollapsed = true;
        this.state.kpis.totalFailedDeadlines = 1;

        const report: CollapseReport = {
          isCollapsed: true,
          isDataMissing: false,
          timestamp: `${clock.formattedDate} ${clock.formattedTime}`,
          simulatedDay: clock.simulatedDay,
          collapsingOrderId: failingOrder.id,
          collapsingOrderClientCode: failingOrder.clientCode,
          orderDeadline: failingOrder.deadlineTimestamp,
          estimatedArrival: `${clock.formattedDate} ${clock.formattedTime}`,
          cause: 'SATURACION_DEMANDA',
          details: `El pedido ${failingOrder.id} del cliente ${failingOrder.clientCode} excedió su plazo comprometido de ${failingOrder.deadlineHours}h. Se detiene la simulación según la política estricta de cero demoras.`,
          totalDeliveriesCompleted: this.state.kpis.totalDeliveredOnTime,
          totalDaysReached: clock.simulatedDay,
        };

        this.state.collapseReport = report;
        this.stop();
        return;
      }
    }
  }

  private updateKPIs() {
    let totalDist = 0;
    let totalCost = 0;
    const distByType: Record<VehicleType, number> = { AUTO: 0, MOTO: 0, BICICLETA: 0 };
    const costByType: Record<VehicleType, number> = { AUTO: 0, MOTO: 0, BICICLETA: 0 };
    const activeByType: Record<VehicleType, { total: number; active: number }> = {
      AUTO: { total: 0, active: 0 },
      MOTO: { total: 0, active: 0 },
      BICICLETA: { total: 0, active: 0 },
    };

    for (const v of this.state.vehicles) {
      totalDist += v.distanceTraveledKm;
      totalCost += v.accumulatedCostSoles;
      distByType[v.type] += v.distanceTraveledKm;
      costByType[v.type] += v.accumulatedCostSoles;

      activeByType[v.type].total += 1;
      if (v.status === 'EN_RUTA') {
        activeByType[v.type].active += 1;
      }
    }

    this.state.kpis.totalDistanceKm = Number(totalDist.toFixed(1));
    this.state.kpis.totalCostSoles = Number(totalCost.toFixed(2));
    this.state.kpis.distanceByType = distByType;
    this.state.kpis.costByType = costByType;

    const totalVehicles = this.state.vehicles.length;
    const totalActive = this.state.vehicles.filter(v => v.status === 'EN_RUTA').length;
    this.state.kpis.overallUtilizationPercentage = Number(((totalActive / totalVehicles) * 100).toFixed(1));

    (['AUTO', 'MOTO', 'BICICLETA'] as VehicleType[]).forEach(vt => {
      const { total, active } = activeByType[vt];
      this.state.kpis.utilizationByType[vt] = {
        type: vt,
        total,
        active,
        percentage: total > 0 ? Number(((active / total) * 100).toFixed(1)) : 0,
      };
    });

    this.state.kpis.totalDeliveredOnTime = this.state.orders.filter(o => o.status === 'ENTREGADO').length;

    // Daily evolution accumulation on day transition
    const currDay = this.state.clock.simulatedDay;
    const exists = this.state.dailyEvolution.find(d => d.day === currDay);
    if (!exists) {
      this.state.dailyEvolution.push({
        day: currDay,
        deliveredOrdersAccum: this.state.kpis.totalDeliveredOnTime,
        costAccumSoles: this.state.kpis.totalCostSoles,
        minSlackHours: this.state.kpis.minSlackHours,
        distanceAccumKm: this.state.kpis.totalDistanceKm,
      });
    } else {
      exists.deliveredOrdersAccum = this.state.kpis.totalDeliveredOnTime;
      exists.costAccumSoles = this.state.kpis.totalCostSoles;
      exists.minSlackHours = this.state.kpis.minSlackHours;
      exists.distanceAccumKm = this.state.kpis.totalDistanceKm;
    }
  }

  private getNearestWarehouse(coord: Coordinate): Warehouse {
    let nearest = this.state.warehouses[0];
    let minD = Infinity;
    for (const wh of this.state.warehouses) {
      const d = getManhattanDistance(coord, wh.coord);
      if (d < minD) {
        minD = d;
        nearest = wh;
      }
    }
    return nearest;
  }
}

// Singleton simulation engine instance
export const simulationEngine = new SimulationEngine('DIA_A_DIA');
