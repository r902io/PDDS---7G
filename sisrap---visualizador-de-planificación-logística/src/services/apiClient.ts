export const API_BASE = (
  import.meta.env.VITE_API_BASE_URL ?? ''
).replace(/\/$/, '');
// ponytail: '' = mismo origen via proxy Vite (evita CORS); en prod se fija VITE_API_BASE_URL al compilar

const TOKEN_KEY = 'sisrap_token';
const EXP_KEY = 'sisrap_token_exp';

export function getSessionToken(): string {
  return localStorage.getItem(TOKEN_KEY) ?? '';
}

export function setSessionToken(token: string, expiraEn: string): void {
  localStorage.setItem(TOKEN_KEY, token);
  localStorage.setItem(EXP_KEY, expiraEn);
}

export class ApiError extends Error {
  status: number;
  body: string;
  constructor(message: string, status: number, body: string) {
    super(message);
    this.status = status;
    this.body = body;
  }
}

// ponytail: el backend devuelve JSON directo, sin wrapper {success,data}
export async function apiFetch<T>(
  path: string,
  options: RequestInit = {},
  auth = true
): Promise<T> {
  const headers: Record<string, string> = { ...(options.headers as any) };
  const isForm = options.body instanceof FormData;
  if (!isForm && options.body && !headers['Content-Type']) {
    headers['Content-Type'] = 'application/json';
  }
  if (!headers['Accept']) headers['Accept'] = 'application/json';
  const token = getSessionToken();
  if (auth && token) headers['Authorization'] = `Bearer ${token}`;

  const res = await fetch(`${API_BASE}${path}`, { ...options, headers });
  if (!res.ok) {
    throw new ApiError(`HTTP ${res.status} ${path}`, res.status, await res.text());
  }
  if (res.status === 204) return undefined as T;
  const ct = res.headers.get('content-type') ?? '';
  if (!ct.includes('json')) return undefined as T;
  return (await res.json()) as T;
}
