import type { ConfiguracionOperativa } from '../types/api';
import { apiFetch } from './apiClient';

export const configuracionService = {
  consultar(senal?: AbortSignal): Promise<ConfiguracionOperativa> {
    return apiFetch('/api/configuracion-operativa', { autenticar: false, senal });
  },
  guardar(configuracion: ConfiguracionOperativa): Promise<ConfiguracionOperativa> {
    return apiFetch('/api/configuracion-operativa', { metodo: 'PUT', cuerpo: configuracion });
  },
};
