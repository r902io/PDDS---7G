import { useMemo } from 'react';
import { simulacionService } from '../services/simulacionService';
import { rutasDeEnvio, type RutaDestacada } from '../utilitarios/envios';
import { useConsulta } from './useConsulta';

const SIN_RUTAS: RutaDestacada[] = [];
const REFRESCO_MS = 4_000;

/**
 * Rutas de todas las unidades de un envío. Se consulta aparte de la lista
 * porque un filtro solo trae las líneas de su ventana, y el envío elegido
 * puede salir de la página actual.
 */
export function useRutasEnvio(
  idPedido: number | null,
  idSimulacion: number | null,
  activa: boolean
): RutaDestacada[] {
  const consulta = useConsulta(
    (s) => (idPedido == null ? Promise.resolve(null) : simulacionService.envio(idPedido, s)),
    `ruta-envio-${idSimulacion}-${idPedido}`,
    idPedido != null && activa ? REFRESCO_MS : null
  );

  return useMemo(() => {
    const envio = consulta.datos?.envios[0];
    return envio && envio.idPedido === idPedido ? rutasDeEnvio(envio) : SIN_RUTAS;
  }, [consulta.datos, idPedido]);
}
