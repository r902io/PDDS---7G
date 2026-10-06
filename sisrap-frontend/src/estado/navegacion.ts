import { useEffect, useState } from 'react';

/**
 * Navegación por fragmento (#/operacion, #/mantenimiento/vehiculos, ...).
 *
 * El servidor solo ve "/" e index.html: ninguna ruta del front llega al
 * backend, así que recargar o abrir un enlace directo nunca devuelve 404,
 * sin importar quién sirva los archivos estáticos.
 */

export type PestanaMantenimiento = 'vehiculos' | 'almacenes' | 'pedidos' | 'bloqueos';

export type Vista =
  | { nombre: 'selector' }
  | { nombre: 'operacion' }
  | { nombre: 'resultado' }
  | { nombre: 'mantenimiento'; pestana: PestanaMantenimiento };

const PESTANAS: PestanaMantenimiento[] = ['vehiculos', 'almacenes', 'pedidos', 'bloqueos'];

export function leerVista(hash: string): Vista {
  const partes = hash.replace(/^#\/?/, '').split('/').filter(Boolean);
  switch (partes[0]) {
    case 'operacion':
      return { nombre: 'operacion' };
    case 'resultado':
      return { nombre: 'resultado' };
    case 'mantenimiento': {
      const p = partes[1] as PestanaMantenimiento;
      return { nombre: 'mantenimiento', pestana: PESTANAS.includes(p) ? p : 'vehiculos' };
    }
    default:
      return { nombre: 'selector' };
  }
}

export function hashDe(v: Vista): string {
  switch (v.nombre) {
    case 'selector':
      return '#/';
    case 'operacion':
      return '#/operacion';
    case 'resultado':
      return '#/resultado';
    case 'mantenimiento':
      return `#/mantenimiento/${v.pestana}`;
  }
}

export function navegar(v: Vista): void {
  const h = hashDe(v);
  if (window.location.hash !== h) window.location.hash = h;
}

export function useVista(): Vista {
  const [vista, setVista] = useState<Vista>(() => leerVista(window.location.hash));
  useEffect(() => {
    const alCambiar = () => setVista(leerVista(window.location.hash));
    window.addEventListener('hashchange', alCambiar);
    return () => window.removeEventListener('hashchange', alCambiar);
  }, []);
  return vista;
}
