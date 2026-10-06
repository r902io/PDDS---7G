import {
  Order,
  Vehicle,
  Warehouse,
  RoadBlock,
} from '../types/logistics';
import { simulationEngine } from './mockBackendEngine';
import { apiRequest } from './apiClient';

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
  // Pedidos
  async getOrders(): Promise<Order[]> {
    const res = await apiRequest<Order[]>('/orders');
    if (res.success && res.data) return res.data;
    return simulationEngine.getState().orders;
  },

  async createOrder(order: Partial<Order>): Promise<Order> {
    const newOrder: Order = {
      id: `ord-${Date.now()}`,
      clientCode: order.clientCode || 'c9999',
      clientName: order.clientName || 'Cliente Nuevo',
      coord: order.coord || { x: 20, y: 20 },
      quantity: order.quantity || 1,
      registeredAt: new Date().toLocaleString(),
      deadlineHours: order.deadlineHours || 36,
      deadlineTimestamp: new Date(Date.now() + (order.deadlineHours || 36) * 3600000).toLocaleString(),
      status: 'REGISTRADO',
      slackHours: order.deadlineHours || 36,
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

  // Clientes
  async getCustomers(): Promise<Customer[]> {
    const res = await apiRequest<Customer[]>('/customers');
    if (res.success && res.data) return res.data;
    return localCustomers;
  },

  async createCustomer(cust: Omit<Customer, 'id' | 'activeOrdersCount'>): Promise<Customer> {
    const created: Customer = {
      ...cust,
      id: `cli-${Date.now()}`,
      activeOrdersCount: 0,
    };
    localCustomers.push(created);
    return created;
  },

  // Vehículos
  async getVehicles(): Promise<Vehicle[]> {
    const res = await apiRequest<Vehicle[]>('/vehicles');
    if (res.success && res.data) return res.data;
    return simulationEngine.getState().vehicles;
  },

  // Almacenes
  async getWarehouses(): Promise<Warehouse[]> {
    const res = await apiRequest<Warehouse[]>('/warehouses');
    if (res.success && res.data) return res.data;
    return simulationEngine.getState().warehouses;
  },

  // Tramos bloqueados
  async getRoadBlocks(): Promise<RoadBlock[]> {
    const res = await apiRequest<RoadBlock[]>('/roadblocks');
    if (res.success && res.data) return res.data;
    return simulationEngine.getState().roadBlocks;
  },

  async createRoadBlock(block: Omit<RoadBlock, 'id'>): Promise<RoadBlock> {
    const newBlock: RoadBlock = {
      ...block,
      id: `blk-${Date.now()}`,
    };
    simulationEngine.addRoadBlock(block.from, block.to, block.description);
    return newBlock;
  },
};
