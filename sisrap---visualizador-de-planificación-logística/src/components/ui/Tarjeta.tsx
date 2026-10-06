import React from 'react';

interface TarjetaProps {
  titulo: string;
  valor: string | number;
  contexto?: string;
  porcentaje?: number; // 0..100
  alerta?: 'normal' | 'advertencia' | 'critica' | 'conforme';
  icono?: React.ReactNode;
  className?: string;
}

export const Tarjeta: React.FC<TarjetaProps> = ({
  titulo,
  valor,
  contexto,
  porcentaje,
  alerta = 'normal',
  icono,
  className = '',
}) => {
  let valorColor = 'text-texto';
  let barraColor = 'bg-mint';

  if (alerta === 'advertencia') {
    valorColor = 'text-ambar';
    barraColor = 'bg-ambar';
  } else if (alerta === 'critica') {
    valorColor = 'text-rojo';
    barraColor = 'bg-rojo';
  } else if (alerta === 'conforme') {
    valorColor = 'text-mint';
    barraColor = 'bg-mint';
  }

  return (
    <div className={`bg-panel border border-borde rounded-md p-3 flex flex-col justify-between ${className}`}>
      <div className="flex items-start justify-between">
        <span className="text-xs uppercase font-medium tracking-wider text-texto2">{titulo}</span>
        {icono && <span className="text-texto2/70">{icono}</span>}
      </div>

      <div className="my-1.5">
        <div className={`text-2xl font-mono font-semibold ${valorColor}`}>{valor}</div>
        {porcentaje !== undefined && (
          <div className="w-full bg-panel2 h-1.5 rounded-full overflow-hidden mt-1.5 border border-borde/40">
            <div
              className={`h-full ${barraColor} transition-all duration-300`}
              style={{ width: `${Math.min(100, Math.max(0, porcentaje))}%` }}
            />
          </div>
        )}
      </div>

      {contexto && <p className="text-[11px] text-texto2 font-normal leading-tight mt-0.5">{contexto}</p>}
    </div>
  );
};
