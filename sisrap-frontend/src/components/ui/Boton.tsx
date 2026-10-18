import React, { useId } from 'react';

interface BotonProps extends React.ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: 'primario' | 'secundario' | 'peligro';
  /** Obligatorio cuando el botón está deshabilitado: explica qué lo bloquea. */
  disabledReason?: string;
  icono?: React.ReactNode;
  /** Muestra el motivo de bloqueo como texto visible debajo (útil en táctil). */
  motivoVisible?: boolean;
}

export const Boton: React.FC<BotonProps> = ({
  children,
  variant = 'primario',
  disabled,
  disabledReason,
  icono,
  motivoVisible = false,
  className = '',
  ...props
}) => {
  const idMotivo = useId();
  const base =
    'inline-flex items-center justify-center gap-2 px-4 py-2 rounded-md text-sm font-medium transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-mint select-none';

  let variante = '';
  if (disabled) {
    // Colores explícitos (no opacidad) para conservar el contraste de 4.5:1.
    variante = 'bg-panel2 border border-borde text-texto2 cursor-not-allowed';
  } else if (variant === 'primario') {
    variante = 'bg-mint text-bg hover:brightness-110 active:brightness-95 cursor-pointer';
  } else if (variant === 'secundario') {
    variante = 'bg-transparent border border-borde text-texto hover:bg-panel hover:border-texto2 cursor-pointer';
  } else {
    variante = 'bg-rojo/15 border border-rojo text-rojo hover:bg-rojo/25 cursor-pointer';
  }

  const conMotivo = disabled && disabledReason;

  return (
    <span className="relative inline-flex flex-col group">
      <button
        disabled={disabled}
        aria-describedby={conMotivo ? idMotivo : undefined}
        title={conMotivo ? disabledReason : props.title}
        className={`${base} ${variante} ${className}`}
        {...props}
      >
        {icono && <span className="w-4 h-4 shrink-0 flex items-center justify-center">{icono}</span>}
        {children}
      </button>

      {conMotivo &&
        (motivoVisible ? (
          <span id={idMotivo} className="mt-1 text-xs text-texto2 max-w-xs">
            {disabledReason}
          </span>
        ) : (
          <span
            id={idMotivo}
            role="tooltip"
            className="absolute top-full left-1/2 -translate-x-1/2 mt-1.5 hidden group-hover:block group-focus-within:block z-50 w-max max-w-64 bg-panel border border-borde text-texto text-xs px-2.5 py-1.5 rounded shadow-lg pointer-events-none"
          >
            {disabledReason}
          </span>
        ))}
    </span>
  );
};
