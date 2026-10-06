import {
  Order,
  Vehicle,
  Warehouse,
  RoadBlock,
} from '../types/logistics';
import { simulationEngine } from './mockBackendEngine';
import { apiFetch } from './apiClient';
import { ensureSession } from './sessionService';
import {
  AlmacenOperativo,
  BloqueoRespuesta,
  PedidoOperativo,
  VehiculoOperativo,
  mapOrders,
  mapRoadBlocks,
  mapVehicles,
  mapWarehouses,
} from './backendMappers';

export interface Customer {
  id: string;
  code: string;
  name: string;
  coordX: number;
  coordY: number;
  phone: string;
  activeOrdersCount: number;
}

const INITIAL_CUSTOMERS: Customer[] = [
  { id: 'cli-01', code: 'c9167', name: 'Distribuidora San Mateo', coordX: 45, coordY: 43, phone: '+51 984 112 049', activeOrdersCount: 1 },
  { id: 'cli-02', code: 'c4021', name: 'Farmacias del Sol', coordX: 18, coordY: 35, phone: '+51 912 344 890', activeOrdersCount: 1 },
  { id: 'cli-03', code: 'c7720', name: 'Bodega Central Norte', coordX: 15, coordY: 46, phone: '+51 977 441 229', activeOrdersCount: 1 },
  { id: 'cli-04', code: 'c1204', name: 'Minimarket Los Laureles', coordX: 62, coordY: 25, phone: '+51 965 210 338', activeOrdersCount: 1 },
  { id: 'cli-05', code: 'c8842', name: 'Comercial Andina', coordX: 34, coordY: 18, phone: '+51 991 762 104', activeOrdersCount: 1 },
  { id: 'cli-06', code: 'c3319', name: 'Almacenes Perú Express', coordX: 28, coordY: 16, phone: '+51 955 883 112', activeOrdersCount: 1 },
];

let localCustomers = [...INITIAL_CUSTOMERS];

export const masterDataService = {
  async getOrders(): Promise<Order[]> {
    try {
      const pag = await apiFetch<{ contenido: PedidoOperativo[] }>(
        '/api/pedidos?pagina=0&tamanio=200', {}, false
      );
      return mapOrders(pag.contenido);
    } catch {
      return simulationEngine.getState().orders;
    }
  },

  async createOrder(order: Partial<Order>): Promise<Order> {
    // ponytail: sin POST /pedidos individual; alta masiva va por cargas-historicas
    const now = new Date();
    const dlHours = order.deadlineHours || 36;
    const newOrder: Order = {
      id: `ord-${Date.now()}`,
      clientCode: order.clientCode || 'c9999',
      clientName: order.clientName || 'Cliente Nuevo',
      coord: order.coord || { x: 20, y: 20 },
      quantity: order.quantity || 1,
      registeredAt: now.toISOString(),
      deadlineHours: dlHours,
      deadlineTimestamp: new Date(now.getTime() + dlHours * 3600000).toISOString(),
      status: 'REGISTRADO',
      slackHours: dlHours,
      criticality: 'VERDE',
      deliveredQuantity: 0,
      partials: order.quantity && order.quantity > 24 ? [
        { vehicleId: 'Pendiente', quantity: 24, delivered: false },
        { vehicleId: 'Pendiente', quantity: order.quantity - 24, delivered: false },
      ] : undefined,
    };
    simulationEngine.addOrder(newOrder);
    return newOrder;
  },

  // ponytail: el .txt local se sube tal cual; el backend parsea el formato ventas
  async uploadHistoricos(files: FileList | File[]): Promise<void> {
    await ensureSession();
    const form = new FormData();
    Array.from(files).forEach((f) => form.append('archivos', f));
    await apiFetch('/api/pedidos/cargas-historicas', { method: 'POST', body: form });
  },

  async getCustomers(): Promise<Customer[]> {
    return localCustomers;
  },

  async createCustomer(cust: Omit<Customer, 'id' | 'activeOrdersCount'>): Promise<Customer> {
    const created: Customer = { ...cust, id: `cli-${Date.now()}`, activeOrdersCount: 0 };
    localCustomers.push(created);
    return created;
  },

  async getVehicles(): Promise<Vehicle[]> {
    try {
      const ops = await apiFetch<VehiculoOperativo[]>('/api/vehiculos', {}, false);
      return mapVehicles(ops, []);
    } catch {
      return simulationEngine.getState().vehicles;
    }
  },

  async getWarehouses(): Promise<Warehouse[]> {
    try {
      const list = await apiFetch<AlmacenOperativo[]>('/api/almacenes', {}, false);
      return mapWarehouses(list);
    } catch {
      return simulationEngine.getState().warehouses;
    }
  },

  async getRoadBlocks(): Promise<RoadBlock[]> {
    try {
      const list = await apiFetch<BloqueoRespuesta[]>('/api/bloqueos', {}, false);
      return mapRoadBlocks(list);
    } catch {
      return simulationEngine.getState().roadBlocks;
    }
  },

  async createRoadBlock(block: Omit<RoadBlock, 'id'>): Promise<RoadBlock> {
    await ensureSession();
    const now = new Date().toISOString().slice(0, 19);
    const fin = new Date(Date.now() + 2 * 3600000).toISOString().slice(0, 19);
    await apiFetch('/api/bloqueos', {
      method: 'POST',
      body: JSON.stringify({
        inicio: now,
        fin,
        vertices: [
          { x: block.from.x, y: block.from.y },
          { x: block.to.x, y: block.to.y },
        ],
      }),
    });
    const newBlock: RoadBlock = { ...block, id: `blk-${Date.now()}` };
    simulationEngine.addRoadBlock(block.from, block.to, block.description);
    return newBlock;
  },
};
