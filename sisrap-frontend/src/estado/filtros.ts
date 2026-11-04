import { useState, useCallback } from 'react';
import { TipoFlota, SemaforoFiltro, FiltrosOperacion } from '../types/api';

export function useFiltrosOperacion() {
  const [filtros, setFiltros] = useState<FiltrosOperacion>({
    tiposVehiculo: ['AUTO', 'MOTO', 'BICICLETA'],
    semaforoVehiculos: ['VERDE', 'AMARILLO', 'ROJO', 'VACIO'],
    semaforoAlmacenes: ['VERDE', 'AMARILLO', 'ROJO', 'VACIO'],
  });

  // D05: Toggle para Tipos de Vehículo
  const toggleTipoVehiculo = useCallback((tipo: TipoFlota) => {
    setFiltros((prev: FiltrosOperacion) => ({
      ...prev,
      tiposVehiculo: prev.tiposVehiculo.includes(tipo)
          ? prev.tiposVehiculo.filter((t: TipoFlota) => t !== tipo)
          : [...prev.tiposVehiculo, tipo],
    }));
  }, []);

  // D06: Toggle para Semáforo de Vehículos
  const toggleSemaforoVehiculo = useCallback((semaforo: SemaforoFiltro) => {
    setFiltros((prev: FiltrosOperacion) => ({
      ...prev,
      semaforoVehiculos: prev.semaforoVehiculos.includes(semaforo)
          ? prev.semaforoVehiculos.filter((s: SemaforoFiltro) => s !== semaforo)
          : [...prev.semaforoVehiculos, semaforo],
    }));
  }, []);

  // D07: Toggle para Semáforo de Almacenes
  const toggleSemaforoAlmacen = useCallback((semaforo: SemaforoFiltro) => {
    setFiltros((prev: FiltrosOperacion) => ({
      ...prev,
      semaforoAlmacenes: prev.semaforoAlmacenes.includes(semaforo)
          ? prev.semaforoAlmacenes.filter((s: SemaforoFiltro) => s !== semaforo)
          : [...prev.semaforoAlmacenes, semaforo],
    }));
  }, []);

  return {
    filtros,
    toggleTipoVehiculo,
    toggleSemaforoVehiculo,
    toggleSemaforoAlmacen,
  };
}