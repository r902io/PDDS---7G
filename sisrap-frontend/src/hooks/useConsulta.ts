import { useCallback, useEffect, useRef, useState } from 'react';
import { textoError } from '../services/apiClient';

export interface Consulta<T> {
  datos: T | null;
  error: string | null;
  cargando: boolean;
  /** Hora del navegador (ms) de la última respuesta correcta. */
  actualizadoEn: number | null;
  recargar: () => void;
}

/**
 * Consulta HTTP con estado de carga y error explícitos.
 *
 * Si una recarga falla se conserva el último dato correcto, pero `error`
 * queda informado para que la vista lo muestre: nunca se reemplaza por
 * datos inventados.
 *
 * @param clave cambia → se vuelve a consultar.
 * @param intervaloMs si se indica, se repite la consulta periódicamente.
 */
export function useConsulta<T>(
  consultar: (senal: AbortSignal) => Promise<T>,
  clave: string,
  intervaloMs?: number | null
): Consulta<T> {
  const [datos, setDatos] = useState<T | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [cargando, setCargando] = useState(true);
  const [actualizadoEn, setActualizadoEn] = useState<number | null>(null);
  const [intento, setIntento] = useState(0);
  const fn = useRef(consultar);
  fn.current = consultar;

  useEffect(() => {
    const control = new AbortController();
    let vigente = true;
    const ejecutar = () => {
      setCargando(true);
      fn.current(control.signal)
        .then((d) => {
          if (!vigente) return;
          setDatos(d);
          setError(null);
          setActualizadoEn(Date.now());
        })
        .catch((e) => {
          if (!vigente || (e as Error)?.name === 'AbortError') return;
          setError(textoError(e));
        })
        .finally(() => vigente && setCargando(false));
    };
    ejecutar();
    const id = intervaloMs ? setInterval(ejecutar, intervaloMs) : null;
    return () => {
      vigente = false;
      control.abort();
      if (id) clearInterval(id);
    };
  }, [clave, intervaloMs, intento]);

  const recargar = useCallback(() => setIntento((n) => n + 1), []);
  return { datos, error, cargando, actualizadoEn, recargar };
}
