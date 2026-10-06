import type {
  AlmacenOperativo,
  AveriaVehiculo,
  BloqueoRespuesta,
  EstadoPedido,
  MantenimientoVehiculo,
  MapaCiudad,
  PaginaPedidos,
  VehiculoOperativo,
} from '../types/api';
import { apiFetch } from './apiClient';

/**
 * Consultas de solo lectura a los controladores de datos maestros.
 * En esta entrega ninguna vista crea, edita ni elimina registros.
 */
export const catalogoService = {
  mapa(senal?: AbortSignal): Promise<MapaCiudad> {
    return apiFetch<MapaCiudad>('/api/mapa', { autenticar: false, senal });
  },

  almacenes(senal?: AbortSignal): Promise<AlmacenOperativo[]> {
    return apiFetch<AlmacenOperativo[]>('/api/almacenes', { autenticar: false, senal });
  },

  vehiculos(senal?: AbortSignal): Promise<VehiculoOperativo[]> {
    return apiFetch<VehiculoOperativo[]>('/api/vehiculos', { autenticar: false, senal });
  },

  averias(idVehiculo: string, senal?: AbortSignal): Promise<AveriaVehiculo[]> {
    return apiFetch<AveriaVehiculo[]>(`/api/vehiculos/${encodeURIComponent(idVehiculo)}/averias`, {
      autenticar: false,
      senal,
    });
  },

  mantenimientos(idVehiculo: string, senal?: AbortSignal): Promise<MantenimientoVehiculo[]> {
    return apiFetch<MantenimientoVehiculo[]>(
      `/api/vehiculos/${encodeURIComponent(idVehiculo)}/mantenimientos`,
      { autenticar: false, senal }
    );
  },

  pedidos(
    filtros: { anio?: number; mes?: number; estado?: EstadoPedido; pagina: number; tamanio: number },
    senal?: AbortSignal
  ): Promise<PaginaPedidos> {
    const q = new URLSearchParams();
    if (filtros.anio != null) q.set('anio', String(filtros.anio));
    if (filtros.mes != null) q.set('mes', String(filtros.mes));
    if (filtros.estado) q.set('estado', filtros.estado);
    q.set('pagina', String(filtros.pagina));
    q.set('tamanio', String(filtros.tamanio));
    return apiFetch<PaginaPedidos>(`/api/pedidos?${q.toString()}`, { autenticar: false, senal });
  },

  /**
   * Bloqueos con su estado evaluado en `instante` (LocalDateTime sin zona).
   * Sin instante, el backend usa la hora de su servidor.
   */
  bloqueos(instante?: string | null, senal?: AbortSignal): Promise<BloqueoRespuesta[]> {
    const q = instante ? `?instante=${encodeURIComponent(instante)}` : '';
    return apiFetch<BloqueoRespuesta[]>(`/api/bloqueos${q}`, { autenticar: false, senal });
  },
};
