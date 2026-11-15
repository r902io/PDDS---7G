import { createContext, useContext, type ReactNode } from 'react';
import type { EscenarioSimulacion, VehiculoSnapshot, SemaforoFiltro } from '../types/api';
import { useConsulta } from '../hooks/useConsulta';
import { apiFetch } from '../services/apiClient';
import { Cargando, ErrorCarga } from '../components/ui/Avisos';
import { filtrarUnidades, type FiltrosListaUT } from '../utilitarios/vehiculos';

export interface RangosSemaforo { amarilloDesdePct: number; rojoDesdePct: number }
export const RANGOS_INICIALES: RangosSemaforo = { amarilloDesdePct: 50, rojoDesdePct: 80 };
export function rangosValidos(r: RangosSemaforo): boolean {
  return Number.isInteger(r.amarilloDesdePct) && Number.isInteger(r.rojoDesdePct)
    && r.amarilloDesdePct >= 1 && r.amarilloDesdePct < r.rojoDesdePct && r.rojoDesdePct <= 100;
}
export function clasificarCarga(carga: number, capacidad: number, r: RangosSemaforo): SemaforoFiltro {
  if (carga <= 0) return 'VACIO';
  if (capacidad <= 0) return 'VERDE';
  const porcentaje = carga / capacidad * 100;
  return porcentaje >= r.rojoDesdePct ? 'ROJO' : porcentaje >= r.amarilloDesdePct ? 'AMARILLO' : 'VERDE';
}
export function clasificarVehiculo(v: VehiculoSnapshot, r: RangosSemaforo) {
  return clasificarCarga(v.cargaActualPaquetes ?? 0, v.capacidadPaquetes, r);
}
export const COLOR_SEMAFORO: Record<SemaforoFiltro, string> = {
  VACIO: '#9ca3af', VERDE: '#22c55e', AMARILLO: '#f59e0b', ROJO: '#ef4444',
};
export function filtrarPorRangos(v: VehiculoSnapshot[], filtros: FiltrosListaUT, r: RangosSemaforo) {
  return filtrarUnidades(v, { ...filtros, semaforos: ['VACIO', 'VERDE', 'AMARILLO', 'ROJO'] })
    .filter(unidad => filtros.semaforos.includes(clasificarVehiculo(unidad, r)));
}
const Contexto = createContext<RangosSemaforo>(RANGOS_INICIALES);
export const useRangosSemaforo = () => useContext(Contexto);
export function SemaforoProvider({ escenario, children }: { escenario: EscenarioSimulacion | null; children: ReactNode }) {
  const consulta = useConsulta(async signal => {
    if (!escenario) return RANGOS_INICIALES;
    const plantilla = await apiFetch<{ semaforoVehiculos?: RangosSemaforo }>(
      `/api/configuracion-operativa/escenarios/${escenario}`, { autenticar: false, senal: signal });
    const rangos = plantilla.semaforoVehiculos ?? RANGOS_INICIALES;
    if (!rangosValidos(rangos)) throw new Error('Los rangos del semáforo del escenario no son válidos.');
    return rangos;
  }, `semaforo-${escenario}`);
  if (consulta.error) return <div className="p-4"><ErrorCarga titulo="No se pudo cargar el semáforo" error={consulta.error} reintentar={consulta.recargar} /></div>;
  if (!consulta.datos || consulta.cargando) return <div className="p-4"><Cargando texto="Cargando parámetros del escenario…" /></div>;
  return <Contexto.Provider value={consulta.datos}>{children}</Contexto.Provider>;
}
