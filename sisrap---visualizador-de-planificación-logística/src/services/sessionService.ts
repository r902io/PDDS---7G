import { apiFetch, getSessionToken, setSessionToken } from './apiClient';

interface SesionCreada {
  sesionId: string;
  token: string;
  creadaEn: string;
  expiraEn: string;
}

// ponytail: una sesion por 12h, se valida contra /actual antes de crear otra
export async function ensureSession(): Promise<string> {
  const cached = getSessionToken();
  if (cached) {
    try {
      await apiFetch('/api/sesiones/actual');
      return cached;
    } catch {
      // expirada o revocada: crear una nueva
    }
  }
  const s = await apiFetch<SesionCreada>('/api/sesiones', { method: 'POST' }, false);
  setSessionToken(s.token, s.expiraEn);
  return s.token;
}
