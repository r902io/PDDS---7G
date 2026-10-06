/**
 * Iconos SVG en línea. El estándar de GUI excluye las librerías de iconos,
 * así que cada icono es un componente con sus propios trazos.
 */
import React from 'react';
import { FORMAS, type FormaIcono, type NombreForma } from './formas';

interface PropsIcono extends React.SVGProps<SVGSVGElement> {
  tamano?: number;
  titulo?: string;
}

function Base({ tamano = 16, titulo, children, ...resto }: PropsIcono & { children: React.ReactNode }) {
  return (
    <svg
      width={tamano}
      height={tamano}
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth={2}
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden={titulo ? undefined : true}
      role={titulo ? 'img' : undefined}
      {...resto}
    >
      {titulo && <title>{titulo}</title>}
      {children}
    </svg>
  );
}

export const IconoChevronIzq = (p: PropsIcono) => (
  <Base {...p}>
    <path d="M15 5 L8 12 L15 19" />
  </Base>
);

export const IconoChevronDer = (p: PropsIcono) => (
  <Base {...p}>
    <path d="M9 5 L16 12 L9 19" />
  </Base>
);

export const IconoCerrar = (p: PropsIcono) => (
  <Base {...p}>
    <path d="M6 6 L18 18 M18 6 L6 18" />
  </Base>
);

export const IconoIniciar = (p: PropsIcono) => (
  <Base {...p}>
    <path d="M7 4.5 L19 12 L7 19.5 Z" fill="currentColor" />
  </Base>
);

export const IconoDetener = (p: PropsIcono) => (
  <Base {...p}>
    <rect x="5.5" y="5.5" width="13" height="13" rx="1.5" fill="currentColor" />
  </Base>
);

export const IconoReloj = (p: PropsIcono) => (
  <Base {...p}>
    <circle cx="12" cy="12" r="9" />
    <path d="M12 7 V12 L15.5 14" />
  </Base>
);

export const IconoCalendario = (p: PropsIcono) => (
  <Base {...p}>
    <rect x="3.5" y="5" width="17" height="15" rx="2" />
    <path d="M3.5 10 H20.5 M8 3 V7 M16 3 V7" />
  </Base>
);

export const IconoColapso = (p: PropsIcono) => (
  <Base {...p}>
    <path d="M8.2 3 H15.8 L21 8.2 V15.8 L15.8 21 H8.2 L3 15.8 V8.2 Z" />
    <path d="M12 7.5 V12.5 M12 16 V16.3" />
  </Base>
);

export const IconoAlerta = (p: PropsIcono) => (
  <Base {...p}>
    <path d="M12 3.5 L21.5 20 H2.5 Z" />
    <path d="M12 9.5 V14 M12 17 V17.3" />
  </Base>
);

export const IconoCorrecto = (p: PropsIcono) => (
  <Base {...p}>
    <circle cx="12" cy="12" r="9" />
    <path d="M7.5 12.5 L10.5 15.5 L16.5 9" />
  </Base>
);

export const IconoInfo = (p: PropsIcono) => (
  <Base {...p}>
    <circle cx="12" cy="12" r="9" />
    <path d="M12 11 V16.5 M12 7.6 V7.8" />
  </Base>
);

export const IconoConexion = (p: PropsIcono) => (
  <Base {...p}>
    <path d="M2.5 9 A14 14 0 0 1 21.5 9 M5.5 12.5 A9.5 9.5 0 0 1 18.5 12.5 M8.7 16 A5 5 0 0 1 15.3 16" />
    <circle cx="12" cy="19.3" r="0.9" fill="currentColor" />
  </Base>
);

export const IconoSinConexion = (p: PropsIcono) => (
  <Base {...p}>
    <path d="M2.5 9 A14 14 0 0 1 9 5.6 M14.5 5.4 A14 14 0 0 1 21.5 9 M8.7 16 A5 5 0 0 1 15.3 16" />
    <path d="M3 3 L21 21" />
  </Base>
);

export const IconoMapa = (p: PropsIcono) => (
  <Base {...p}>
    <path d="M3 6 L9 3.5 L15 6 L21 3.5 V18 L15 20.5 L9 18 L3 20.5 Z M9 3.5 V18 M15 6 V20.5" />
  </Base>
);

export const IconoLista = (p: PropsIcono) => (
  <Base {...p}>
    <path d="M9 6 H20 M9 12 H20 M9 18 H20" />
    <path d="M4.5 6 H5 M4.5 12 H5 M4.5 18 H5" />
  </Base>
);

export const IconoCapas = (p: PropsIcono) => (
  <Base {...p}>
    <path d="M12 3 L21 8 L12 13 L3 8 Z" />
    <path d="M3 12.5 L12 17.5 L21 12.5 M3 16.5 L12 21.5 L21 16.5" />
  </Base>
);

export const IconoRecargar = (p: PropsIcono) => (
  <Base {...p}>
    <path d="M20 11 A8 8 0 1 0 17.6 17.3" />
    <path d="M20 4.5 V11 H13.5" />
  </Base>
);

export const IconoEncuadrar = (p: PropsIcono) => (
  <Base {...p}>
    <path d="M4 9 V4 H9 M15 4 H20 V9 M20 15 V20 H15 M9 20 H4 V15" />
  </Base>
);

export const IconoOjo = (p: PropsIcono) => (
  <Base {...p}>
    <path d="M2.5 12 C5 7 8.5 5 12 5 C15.5 5 19 7 21.5 12 C19 17 15.5 19 12 19 C8.5 19 5 17 2.5 12 Z" />
    <circle cx="12" cy="12" r="3" />
  </Base>
);

export const IconoVolante = (p: PropsIcono) => (
  <Base {...p}>
    <circle cx="12" cy="12" r="9" />
    <circle cx="12" cy="12" r="2.2" />
    <path d="M3.5 11 L9.8 12 M14.2 12 L20.5 11 M12 14.2 V21" />
  </Base>
);

// ---------------------------------------------------------------------------
// Iconos del mapa en SVG (misma forma que en el Canvas)
// ---------------------------------------------------------------------------

interface PropsIconoMapa {
  forma: NombreForma;
  color?: string;
  tamano?: number;
  titulo?: string;
}

export function IconoMapaSvg({ forma, color = '#e8eef7', tamano = 24, titulo }: PropsIconoMapa) {
  return (
    <svg
      width={tamano}
      height={tamano}
      viewBox="-12 -12 24 24"
      aria-hidden={titulo ? undefined : true}
      role={titulo ? 'img' : undefined}
      strokeLinecap="round"
      strokeLinejoin="round"
    >
      {titulo && <title>{titulo}</title>}
      {(FORMAS[forma] as FormaIcono).map((c, i) => (
        <path
          key={i}
          d={c.d}
          fill={c.relleno ? (c.relleno === 'color' ? color : c.relleno) : 'none'}
          stroke={c.trazo ? (c.trazo === 'color' ? color : c.trazo) : 'none'}
          strokeWidth={c.grosor ?? 1}
        />
      ))}
    </svg>
  );
}
