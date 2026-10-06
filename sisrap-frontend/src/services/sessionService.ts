import type { SesionCreadaRespuesta, SesionRespuesta } from '../types/api';
import { ApiError, apiFetch, guardarSesion, olvidarSesion, sesionGuardada } from './apiClient';

export interface SesionActiva {
  sesionId: string;
  expiraEn: string;
}

/**
 * Sesión anónima, sin inicio de sesión visible.
 *
 * 1. Si hay un token guardado, se valida con GET /api/sesiones/actual
 *    (el backend además la renueva).
 * 2. Si no hay token o el backend lo rechaza con 401, se crea una nueva
 *    con POST /api/sesiones.
 * Cualquier otro error (backend apagado, 5xx) se propaga: no se inventa
 * una sesión local.
 */
export async function asegurarSesion(): Promise<SesionActiva> {
  const guardada = sesionGuardada();
  if (guardada) {
    try {
      const actual = await apiFetch<SesionRespuesta>('/api/sesiones/actual');
      guardarSesion(guardada.token, actual.sesionId, actual.expiraEn);
      return { sesionId: actual.sesionId, expiraEn: actual.expiraEn };
    } catch (e) {
      if (!(e instanceof ApiError && e.status === 401)) throw e;
      olvidarSesion();
    }
  }

  const creada = await apiFetch<SesionCreadaRespuesta>('/api/sesiones', {
    metodo: 'POST',
    autenticar: false,
  });
  guardarSesion(creada.token, creada.sesionId, creada.expiraEn);
  return { sesionId: creada.sesionId, expiraEn: creada.expiraEn };
}
