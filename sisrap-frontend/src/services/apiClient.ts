import type { ErrorSpring } from '../types/api';

/**
 * Cliente HTTP único hacia sisrap-backend.
 *
 * Siempre llama a /api en el mismo origen: en desarrollo lo atiende el
 * proxy de Vite y en despliegue el propio backend sirve el front. No hay
 * reintentos silenciosos ni datos de respaldo: si una llamada falla, se
 * lanza ApiError y la interfaz lo muestra.
 */

const CLAVE_TOKEN = 'sisrap.sesion.token';
const CLAVE_SESION = 'sisrap.sesion.id';
const CLAVE_EXPIRA = 'sisrap.sesion.expiraEn';

function leer(clave: string): string | null {
  try {
    return localStorage.getItem(clave);
  } catch {
    return null;
  }
}

function escribir(clave: string, valor: string | null): void {
  try {
    if (valor == null) localStorage.removeItem(clave);
    else localStorage.setItem(clave, valor);
  } catch {
    // Almacenamiento bloqueado: la sesión dura lo que dure la pestaña.
  }
}

let tokenMemoria: string | null = leer(CLAVE_TOKEN);

export interface SesionGuardada {
  token: string;
  sesionId: string | null;
  expiraEn: string | null;
}

export function sesionGuardada(): SesionGuardada | null {
  if (!tokenMemoria) return null;
  return { token: tokenMemoria, sesionId: leer(CLAVE_SESION), expiraEn: leer(CLAVE_EXPIRA) };
}

export function guardarSesion(token: string, sesionId: string, expiraEn: string): void {
  tokenMemoria = token;
  escribir(CLAVE_TOKEN, token);
  escribir(CLAVE_SESION, sesionId);
  escribir(CLAVE_EXPIRA, expiraEn);
}

export function olvidarSesion(): void {
  tokenMemoria = null;
  escribir(CLAVE_TOKEN, null);
  escribir(CLAVE_SESION, null);
  escribir(CLAVE_EXPIRA, null);
}

export class ApiError extends Error {
  readonly status: number;
  readonly ruta: string;
  readonly detalle: string | null;

  constructor(mensaje: string, status: number, ruta: string, detalle: string | null) {
    super(mensaje);
    this.name = 'ApiError';
    this.status = status;
    this.ruta = ruta;
    this.detalle = detalle;
  }

  /** status 0 = no hubo respuesta HTTP (backend apagado o red caída). */
  get sinConexion(): boolean {
    return this.status === 0;
  }
}

function mensajePorEstado(status: number): string {
  switch (status) {
    case 400:
      return 'El backend rechazó la solicitud por datos inválidos.';
    case 401:
      return 'La sesión no es válida o expiró.';
    case 403:
      return 'Esta sesión no controla la corrida en curso.';
    case 404:
      return 'El recurso solicitado no existe en el backend.';
    case 409:
      return 'La operación entra en conflicto con el estado actual de la simulación.';
    case 500:
      return 'El backend reportó un error interno.';
    case 502:
    case 503:
    case 504:
      return 'El backend no está disponible.';
    default:
      return `El backend respondió con el estado HTTP ${status}.`;
  }
}

async function leerDetalle(res: Response): Promise<string | null> {
  try {
    const texto = await res.text();
    if (!texto) return null;
    try {
      const cuerpo = JSON.parse(texto) as ErrorSpring & { error?: string };
      const msg = typeof cuerpo.message === 'string' ? cuerpo.message.trim() : '';
      const error = typeof cuerpo.error === 'string' ? cuerpo.error.trim() : '';
      if (msg) return msg;
      if (error) return error;
      return null;
    } catch {
      return null;
    }
  } catch {
    return null;
  }
}

export interface OpcionesApi {
  metodo?: 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE';
  cuerpo?: unknown;
  /** Enviar Authorization: Bearer <token>. Por defecto, si hay token. */
  autenticar?: boolean;
  senal?: AbortSignal;
}

export async function apiFetch<T>(ruta: string, opciones: OpcionesApi = {}): Promise<T> {
  const { metodo = 'GET', cuerpo, autenticar = true, senal } = opciones;
  const cabeceras: Record<string, string> = { Accept: 'application/json' };
  if (cuerpo !== undefined) cabeceras['Content-Type'] = 'application/json';
  if (autenticar && tokenMemoria) cabeceras['Authorization'] = `Bearer ${tokenMemoria}`;

  let res: Response;
  try {
    res = await fetch(ruta, {
      method: metodo,
      headers: cabeceras,
      body: cuerpo === undefined ? undefined : JSON.stringify(cuerpo),
      signal: senal,
      cache: 'no-store',
    });
  } catch (e) {
    if ((e as Error)?.name === 'AbortError') throw e;
    throw new ApiError('No se pudo conectar con el backend de SisRap.', 0, ruta, null);
  }

  if (!res.ok) {
    const detalle = await leerDetalle(res);
    throw new ApiError(mensajePorEstado(res.status), res.status, ruta, detalle);
  }

  if (res.status === 204) return undefined as T;
  const tipo = res.headers.get('content-type') ?? '';
  if (!tipo.includes('json')) {
    // Típico cuando el proxy devuelve index.html porque el backend no responde.
    throw new ApiError('El backend devolvió una respuesta que no es JSON.', res.status, ruta, null);
  }
  return (await res.json()) as T;
}

/** Texto para mostrar al usuario a partir de cualquier error. */
export function textoError(e: unknown): string {
  if (e instanceof ApiError) {
    return e.detalle ? `${e.message} Detalle: ${e.detalle}` : e.message;
  }
  if (e instanceof Error) return e.message;
  return 'Error desconocido.';
}
