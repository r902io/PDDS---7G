import { ApiResponse } from '../types/api';

const API_BASE_URL = '/api/v1';

export async function apiRequest<T>(
  endpoint: string,
  options: RequestInit = {}
): Promise<ApiResponse<T>> {
  const headers = {
    'Content-Type': 'application/json',
    Accept: 'application/json',
    ...options.headers,
  };

  try {
    const response = await fetch(`${API_BASE_URL}${endpoint}`, {
      ...options,
      headers,
    });

    if (!response.ok) {
      throw new Error(`HTTP error ${response.status}: ${response.statusText}`);
    }

    return await response.json();
  } catch (error: any) {
    // Return structured response for offline/mock fallback handling
    return {
      success: false,
      data: null as any,
      error: error.message || 'Error de conexión con el backend SisRap en Java',
      timestamp: new Date().toISOString(),
    };
  }
}
