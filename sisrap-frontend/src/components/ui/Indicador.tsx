import React from 'react';
import { VehicleType } from '../../types/logistics';

interface BreakdownRow {
  label: string;
  type?: VehicleType;
  value: string;
  subvalue?: string;
  colorClass?: string;
}

interface IndicadorDesglosadoProps {
  titulo: string;
  valorConsolidado: string;
  unidad?: string;
  filas: BreakdownRow[];
  contexto?: string;
}

export const IndicadorDesglosado: React.FC<IndicadorDesglosadoProps> = ({
  titulo,
  valorConsolidado,
  unidad,
  filas,
  contexto,
}) => {
  return (
    <div className="bg-panel border border-borde rounded-md p-3 flex flex-col space-y-2">
      <div className="flex items-center justify-between">
        <span className="text-xs uppercase font-medium tracking-wider text-texto2">{titulo}</span>
        <div className="text-right">
          <span className="text-base font-mono font-bold text-texto">{valorConsolidado}</span>
          {unidad && <span className="text-xs font-mono text-texto2 ml-1">{unidad}</span>}
        </div>
      </div>

      <div className="space-y-1 pt-1 border-t border-borde/40 text-xs">
        {filas.map((f, i) => (
          <div key={i} className="flex items-center justify-between">
            <span className={`text-[11px] ${f.colorClass || 'text-texto2'}`}>{f.label}</span>
            <div className="font-mono text-xs text-texto">
              {f.value}
              {f.subvalue && <span className="text-[10px] text-texto2 ml-1">({f.subvalue})</span>}
            </div>
          </div>
        ))}
      </div>

      {contexto && <p className="text-[10px] text-texto2/80 italic pt-0.5">{contexto}</p>}
    </div>
  );
};
