import React from 'react';

interface CampoProps extends React.InputHTMLAttributes<HTMLInputElement> {
  label: string;
  unidad?: string;
  nota?: string;
  error?: string;
}

export const Campo: React.FC<CampoProps> = ({
  label,
  unidad,
  nota,
  error,
  id,
  className = '',
  disabled,
  ...props
}) => {
  const inputId = id || `campo-${label.toLowerCase().replace(/\s+/g, '-')}`;

  return (
    <div className="flex flex-col space-y-1">
      <div className="flex items-center justify-between">
        <label htmlFor={inputId} className="text-xs font-normal text-texto2">
          {label}
        </label>
        {nota && <span className="text-[11px] text-texto2">{nota}</span>}
      </div>

      <div className="relative flex items-center">
        <input
          id={inputId}
          disabled={disabled}
          className={`w-full bg-bg border border-borde rounded-md font-mono text-sm px-3 py-1.5 text-texto placeholder:text-texto2 focus:outline-none focus:border-mint focus:ring-1 focus:ring-mint transition-colors disabled:bg-panel2 disabled:text-texto2 disabled:cursor-not-allowed ${
            unidad ? 'pr-12' : ''
          } ${error ? 'border-rojo focus:border-rojo focus:ring-rojo' : ''} ${className}`}
          {...props}
        />
        {unidad && (
          <span className="absolute right-3 text-xs font-mono text-texto2 pointer-events-none select-none">
            {unidad}
          </span>
        )}
      </div>

      {error && <span className="text-xs text-rojo mt-0.5">{error}</span>}
    </div>
  );
};
