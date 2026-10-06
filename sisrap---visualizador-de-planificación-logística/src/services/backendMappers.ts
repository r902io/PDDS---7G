import {
  Order,
  OrderStatus,
  RoadBlock,
  ScenarioType,
  SimulationClock,
  SimulationState,
  Vehicle,
  VehicleStatus,
  VehicleType,
  Warehouse,
} from '../types/logistics';

// ---- Backend payloads (subset real de /v3/api-docs) ----
export interface SnapshotVehiculo {
  idVehiculo: string;
  estado: string;
  x: number;
  y: number;
  pedidoObjetivo?: number | null;
  caminoActual?: Array<{ x: number; y: number }>;
}

export interface SnapshotSimulacion {
  idSimulacion: number;
  estado: 'DETENIDA' | 'EJECUTANDO' | 'PAUSADA' | 'FINALIZADA' | 'ERROR';
  escenario: 'OPERACION_DIARIA' | 'SIMULACION_CINCO_DIAS' | 'COLAPSO_LOGISTICO';
  relojSimulado: string;
  fechaHoraInicio: string;
  fechaHoraFin: string;
  pedidos: {
    total: number;
    futuros: number;
    pendientes: number;
    enRuta: number;
    reasignados: number;
    retrasados: number;
    entregados: number;
  };
  bloqueosActivos: number;
  vehiculos: SnapshotVehiculo[];
}

export interface VehiculoOperativo {
  idVehiculo: string;
  tipo: string;
  capacidadPaquetes: number;
  velocidadKmh: number;
  costoPorKm: number;
  estado: string;
  posicionX: number;
  posicionY: number;
  idConductorActual?: string | null;
}

export interface PedidoOperativo {
  idPedido: number;
  idCliente: string;
  cantidadQq: number;
  horasLimite: number;
  fechaLlegada: string;
  fechaEntregaReal?: string | null;
  estado: 'PENDIENTE' | 'EN_RUTA' | 'ENTREGADO' | 'REASIGNADO' | 'RETRASADO';
  ubicacionX: number;
  ubicacionY: number;
}

export interface BloqueoRespuesta {
  idIncidencia: number;
  inicio: string;
  fin: string;
  estado: 'PROGRAMADO' | 'ACTIVO' | 'FINALIZADO' | 'CANCELADO';
  vertices: Array<{ x: number; y: number }>;
}

export interface AlmacenOperativo {
  idAlmacen: string;
  nombre: string;
  tipo: 'CENTRAL' | 'INTERMEDIO';
  ubicacionX: number;
  ubicacionY: number;
  capacidadMaxima?: number | null;
  stockActual?: number | null;
}

export function mapScenarioToBackend(s: ScenarioType): SnapshotSimulacion['escenario'] {
  return s === 'DIA_A_DIA'
    ? 'OPERACION_DIARIA'
    : s === 'SIMULACION_5D'
      ? 'SIMULACION_CINCO_DIAS'
      : 'COLAPSO_LOGISTICO';
}

export function mapScenarioFromBackend(s: SnapshotSimulacion['escenario']): ScenarioType {
  return s === 'OPERACION_DIARIA'
    ? 'DIA_A_DIA'
    : s === 'SIMULACION_CINCO_DIAS'
      ? 'SIMULACION_5D'
      : 'COLAPSO_LOGISTICO';
}

function mapVehicleType(t: string): VehicleType {
  const u = (t ?? '').toUpperCase();
  if (u.startsWith('MOT')) return 'MOTO';
  if (u.startsWith('BIC')) return 'BICICLETA';
  return 'AUTO';
}

function mapVehicleStatus(e: string): VehicleStatus {
  switch (e) {
    case 'DISPONIBLE': return 'DISPONIBLE';
    case 'EN_RUTA': return 'EN_RUTA';
    case 'EN_AVERIA': return 'AVERIADO';
    case 'EN_MANTENIMIENTO': return 'MANTENIMIENTO';
    case 'RETORNANDO_ALMACEN': return 'EN_RUTA';
    default: return 'DISPONIBLE';
  }
}

function mapOrderStatus(e: PedidoOperativo['estado']): OrderStatus {
  switch (e) {
    case 'PENDIENTE': return 'REGISTRADO';
    case 'EN_RUTA': return 'EN_RUTA';
    case 'ENTREGADO': return 'ENTREGADO';
    case 'REASIGNADO': return 'ASIGNADO';
    case 'RETRASADO': return 'CRITICO';
  }
}

function toClock(snap: SnapshotSimulacion): SimulationClock {
  // ponytail: UTC explicito, el backend emite en UTC y el Date local desplazaba el reloj
  const reloj = new Date(snap.relojSimulado);
  const inicio = new Date(snap.fechaHoraInicio);
  const elapsedH = Math.max(0, (reloj.getTime() - inicio.getTime()) / 3600000);
  const h = reloj.getUTCHours();
  const dd = String(reloj.getUTCDate()).padStart(2, '0');
  const mm = String(reloj.getUTCMonth() + 1).padStart(2, '0');
  return {
    simulatedDateTime: snap.relojSimulado,
    simulatedDay: Math.min(99, Math.floor(elapsedH / 24) + 1),
    formattedTime: Number.isNaN(reloj.getTime()) ? '--:--:--' : snap.relojSimulado.slice(11, 19),
    formattedDate: Number.isNaN(reloj.getTime()) ? '--/--/----' : `${dd}/${mm}/${reloj.getUTCFullYear()}`,
    elapsedRealSeconds: 0,
    elapsedSimulatedHours: elapsedH,
    currentShift: h >= 7 && h < 15 ? 'MANANA' : h >= 15 && h < 23 ? 'TARDE' : 'NOCHE',
  };
}

export function mapVehicles(
  operativos: VehiculoOperativo[],
  live: SnapshotVehiculo[]
): Vehicle[] {
  const liveById = new Map(live.map((v) => [v.idVehiculo, v]));
  return operativos.map((o) => {
    const l = liveById.get(o.idVehiculo);
    const type = mapVehicleType(o.tipo);
    return {
      id: o.idVehiculo,
      plate: o.idVehiculo,
      type,
      status: mapVehicleStatus(l?.estado ?? o.estado),
      coord: { x: l?.x ?? o.posicionX, y: l?.y ?? o.posicionY },
      currentWarehouseId: 'wh-ac',
      assignedOrderIds: l?.pedidoObjetivo != null ? [`ord-${l.pedidoObjetivo}`] : [],
      currentLoad: 0,
      maxCapacity: o.capacidadPaquetes,
      speedKmH: o.velocidadKmh,
      costPerKm: o.costoPorKm,
      currentShift: 'MANANA',
      driverName: o.idConductorActual ?? `${o.idVehiculo} (T1)`,
      isTakingBreak: false,
      breakWindowStartHour: 11,
      breakWindowEndHour: 12,
      breakTaken: false,
      distanceTraveledKm: 0,
      accumulatedCostSoles: 0,
      traveledPath: [],
      pendingPath: (l?.caminoActual ?? []).map((p) => ({ x: p.x, y: p.y })),
      currentTargetCoord: l?.caminoActual?.length
        ? { x: l.caminoActual[l.caminoActual.length - 1].x, y: l.caminoActual[l.caminoActual.length - 1].y }
        : undefined,
      destinationType: l?.pedidoObjetivo != null ? 'CLIENTE' : undefined,
    };
  });
}

export function mapWarehouses(list: AlmacenOperativo[]): Warehouse[] {
  return list.map((a) => ({
    id: a.idAlmacen,
    name: a.nombre,
    code: a.idAlmacen === 'ALM-CENTRAL' ? 'AC' : a.idAlmacen === 'ALM-NOROESTE' ? 'A1' : 'A2',
    coord: { x: a.ubicacionX, y: a.ubicacionY },
    capacityMax: a.capacidadMaxima ?? (a.tipo === 'CENTRAL' ? Infinity : 1000),
    currentStock: a.stockActual ?? (a.tipo === 'CENTRAL' ? 999999 : 1000),
    isCentral: a.tipo === 'CENTRAL',
  }));
}

export function mapOrders(list: PedidoOperativo[]): Order[] {
  return list.map((p) => {
    const dl = [4, 8, 12, 18, 36].includes(p.horasLimite)
      ? (p.horasLimite as Order['deadlineHours'])
      : 36;
    const arrival = new Date(p.fechaLlegada);
    const deadline = Number.isNaN(arrival.getTime())
      ? p.fechaLlegada
      : new Date(arrival.getTime() + dl * 3600000).toISOString();
    return {
      id: `ord-${p.idPedido}`,
      clientCode: p.idCliente,
      clientName: p.idCliente,
      coord: { x: p.ubicacionX, y: p.ubicacionY },
      quantity: p.cantidadQq,
      registeredAt: p.fechaLlegada,
      deadlineHours: dl,
      deadlineTimestamp: deadline,
      status: mapOrderStatus(p.estado),
      slackHours: dl,
      criticality: 'VERDE',
      deliveredAt: p.fechaEntregaReal ?? undefined,
      deliveredQuantity: p.estado === 'ENTREGADO' ? p.cantidadQq : 0,
    };
  });
}

export function mapRoadBlocks(list: BloqueoRespuesta[]): RoadBlock[] {
  return list.map((b) => ({
    id: `blk-${b.idIncidencia}`,
    from: b.vertices[0] ? { x: b.vertices[0].x, y: b.vertices[0].y } : { x: 0, y: 0 },
    to: b.vertices[1] ? { x: b.vertices[1].x, y: b.vertices[1].y } : { x: 0, y: 0 },
    startDateTime: b.inicio,
    endDateTime: b.fin,
    active: b.estado === 'ACTIVO' || b.estado === 'PROGRAMADO',
    description: `Bloqueo #${b.idIncidencia}`,
  }));
}

// ponytail: KPIs que el backend no da quedan en 0; se completan cuando el API los exponga
export function snapshotToState(
  snap: SnapshotSimulacion,
  operativos: VehiculoOperativo[],
  pedidos: PedidoOperativo[],
  almacenes: AlmacenOperativo[],
  bloqueos: BloqueoRespuesta[],
  prev: SimulationState | null
): SimulationState {
  const vehicles = mapVehicles(operativos, snap.vehiculos);
  const activeOf = (t: Vehicle['type']) =>
    vehicles.filter((v) => v.type === t && v.status === 'EN_RUTA').length;
  const totalOf = (t: Vehicle['type']) => vehicles.filter((v) => v.type === t).length;
  const pct = (t: Vehicle['type']) => (totalOf(t) ? Math.round((activeOf(t) / totalOf(t)) * 100) : 0);
  const active = activeOf('AUTO') + activeOf('MOTO') + activeOf('BICICLETA');
  return {
    scenario: mapScenarioFromBackend(snap.escenario),
    isRunning: snap.estado === 'EJECUTANDO',
    isPaused: snap.estado === 'PAUSADA',
    isFinished: snap.estado === 'FINALIZADA',
    hasCollapsed: snap.estado === 'ERROR',
    clock: toClock(snap),
    warehouses: almacenes.length ? mapWarehouses(almacenes) : (prev?.warehouses ?? []),
    vehicles,
    orders: mapOrders(pedidos),
    roadBlocks: mapRoadBlocks(bloqueos),
    incidents: prev?.incidents ?? [],
    kpis: {
      totalDeliveredOnTime: snap.pedidos.entregados,
      totalFailedDeadlines: snap.pedidos.retrasados,
      minSlackHours: 0,
      minSlackOrderId: '',
      minSlackClientCode: '',
      totalDistanceKm: 0,
      totalCostSoles: 0,
      distanceByType: { AUTO: 0, MOTO: 0, BICICLETA: 0 },
      costByType: { AUTO: 0, MOTO: 0, BICICLETA: 0 },
      utilizationByType: {
        AUTO: { type: 'AUTO', total: totalOf('AUTO'), active: activeOf('AUTO'), percentage: pct('AUTO') },
        MOTO: { type: 'MOTO', total: totalOf('MOTO'), active: activeOf('MOTO'), percentage: pct('MOTO') },
        BICICLETA: { type: 'BICICLETA', total: totalOf('BICICLETA'), active: activeOf('BICICLETA'), percentage: pct('BICICLETA') },
      },
      overallUtilizationPercentage: vehicles.length ? Math.round((active / vehicles.length) * 100) : 0,
      activeIncidentsCount: snap.bloqueosActivos,
    },
    thresholds: prev?.thresholds ?? { greenMinHours: 6, amberMinHours: 2, redMinHours: 0 },
    collapseReport: snap.estado === 'ERROR' ? prev?.collapseReport : undefined,
    dailyEvolution: prev?.dailyEvolution ?? [],
    monthlyFiles: prev?.monthlyFiles ?? [],
    metaheuristic: prev?.metaheuristic ?? {
      algorithm: 'SIMULATED_ANNEALING',
      iterations: 2500,
      populationSize: 50,
      seed: 42,
      maxComputeTimeSec: 45,
    },
  };
}
