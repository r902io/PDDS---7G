import { useEffect, useState } from 'react';
import { leerVista, navegar as navegarOriginal, type Vista as VistaOriginal } from './navegacion';

export type Vista = VistaOriginal | { nombre: 'datos' };
export function navegar(vista: Vista) {
  if (vista.nombre === 'datos') window.location.hash = '#/datos';
  else navegarOriginal(vista);
}
export function useVista(): Vista {
  const leer = (): Vista => /^#\/datos\/?$/.test(window.location.hash)
    ? { nombre: 'datos' } : leerVista(window.location.hash);
  const [vista, setVista] = useState<Vista>(leer);
  useEffect(() => {
    const actualizar = () => setVista(leer());
    window.addEventListener('hashchange', actualizar);
    return () => window.removeEventListener('hashchange', actualizar);
  }, []);
  return vista;
}
