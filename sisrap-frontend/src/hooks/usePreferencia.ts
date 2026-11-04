import { useCallback, useState } from 'react';

/**
 * Estado que sobrevive al desmontar el panel: el orden y los filtros de una
 * lista se conservan aunque se colapse el panel o se recargue la página.
 */
export function usePreferencia<T>(
  clave: string,
  inicial: T,
  valido: (valor: unknown) => boolean = () => true
): [T, (nuevo: T) => void] {
  const [valor, setValor] = useState<T>(() => {
    try {
      const texto = localStorage.getItem(clave);
      if (texto == null) return inicial;
      const guardado: unknown = JSON.parse(texto);
      return valido(guardado) ? (guardado as T) : inicial;
    } catch {
      return inicial;
    }
  });

  const guardar = useCallback(
    (nuevo: T) => {
      setValor(nuevo);
      try {
        localStorage.setItem(clave, JSON.stringify(nuevo));
      } catch {
        // Sin almacenamiento, la preferencia dura lo que dure el panel.
      }
    },
    [clave]
  );

  return [valor, guardar];
}
