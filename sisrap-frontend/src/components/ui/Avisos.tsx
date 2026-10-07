import type { ReactNode } from 'react';
import { IconoAlerta, IconoInfo, IconoRecargar } from '../iconos';

/** Mensaje de error de una operación, con opción de reintentar. */
export function ErrorCarga({
  titulo,
  error,
  reintentar,
  className = '',
}: {
  titulo: string;
  error: string;
  reintentar?: () => void;
  className?: string;
}) {
  return (
    <div role="alert" className={`flex items-start gap-2 border border-rojo/60 bg-rojo/10 rounded-md px-3 py-2 text-xs ${className}`}>
      <span className="text-rojo mt-0.5 shrink-0">
        <IconoAlerta tamano={16} />
      </span>
      <div className="flex-1 min-w-0">
        <p className="font-semibold text-rojo">{titulo}</p>
        <p className="text-texto mt-0.5 break-words">{error}</p>
      </div>
      {reintentar && (
        <button
          onClick={reintentar}
          className="shrink-0 flex items-center gap-1 border border-borde rounded px-2 py-1 text-texto hover:border-texto2"
        >
          <IconoRecargar tamano={13} /> Reintentar
        </button>
      )}
    </div>
  );
}

/** Nota informativa (no es un error). */
export function Nota({ children, tono = 'info', className = '' }: { children: ReactNode; tono?: 'info' | 'aviso'; className?: string }) {
  const clase = tono === 'aviso' ? 'border-ambar text-ambar' : 'border-azul text-texto2';
  return (
    <div className={`flex items-start gap-2 border-l-2 pl-2.5 py-0.5 text-xs leading-relaxed ${clase} ${className}`}>
      <span className="mt-0.5 shrink-0">{tono === 'aviso' ? <IconoAlerta tamano={14} /> : <IconoInfo tamano={14} />}</span>
      <div>{children}</div>
    </div>
  );
}

export function Cargando({ texto = 'Cargando…' }: { texto?: string }) {
  return (
    <div className="flex items-center gap-2 text-xs text-texto2" role="status">
      <span className="w-3.5 h-3.5 border-2 border-mint border-t-transparent rounded-full animate-spin" />
      {texto}
    </div>
  );
}
