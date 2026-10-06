import React from 'react';

interface BotonProps extends React.ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: 'primario' | 'secundario' | 'peligro';
  disabledReason?: string;
  icono?: React.ReactNode;
}

export const Boton: React.FC<BotonProps> = ({
  children,
  variant = 'primario',
  disabled,
  disabledReason,
  icono,
  className = '',
  ...props
}) => {
  const baseClasses =
    'inline-flex items-center justify-center gap-2 px-4 py-2 rounded-md text-sm font-medium transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-mint select-none';

  let variantClasses = '';
  if (variant === 'primario') {
    variantClasses = 'bg-mint text-bg hover:brightness-105 active:brightness-95';
  } else if (variant === 'secundario') {
    variantClasses = 'bg-transparent border border-borde text-texto hover:bg-panel hover:border-texto2 active:bg-panel2';
  } else if (variant === 'peligro') {
    variantClasses = 'bg-rojo/20 border border-rojo text-rojo hover:bg-rojo/30 active:bg-rojo/40';
  }

  const disabledClasses = disabled
    ? 'opacity-50 cursor-not-allowed hover:brightness-100 hover:bg-inherit pointer-events-none'
    : 'cursor-pointer';

  return (
    <div className="relative inline-block group">
      <button
        disabled={disabled}
        className={`${baseClasses} ${variantClasses} ${disabledClasses} ${className}`}
        {...props}
      >
        {icono && <span className="w-4 h-4 shrink-0 flex items-center justify-center">{icono}</span>}
        {children}
      </button>

      {disabled && disabledReason && (
        <div className="absolute bottom-full left-1/2 -translate-x-1/2 mb-2 hidden group-hover:block z-50 whitespace-nowrap bg-panel border border-borde text-texto2 text-xs px-2.5 py-1 rounded shadow-lg pointer-events-none">
          {disabledReason}
        </div>
      )}
    </div>
  );
};
