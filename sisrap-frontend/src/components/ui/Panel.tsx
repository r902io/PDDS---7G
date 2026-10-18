import React from 'react';

interface PanelProps {
  titulo?: string;
  subtitulo?: string;
  accion?: React.ReactNode;
  children: React.ReactNode;
  className?: string;
}

export const Panel: React.FC<PanelProps> = ({
  titulo,
  subtitulo,
  accion,
  children,
  className = '',
}) => {
  return (
    <div className={`bg-panel2 border border-borde rounded-lg p-4 ${className}`}>
      {(titulo || accion) && (
        <div className="flex items-center justify-between border-b border-borde/70 pb-2.5 mb-3.5">
          <div>
            {titulo && <h3 className="text-sm font-semibold text-texto uppercase tracking-wide">{titulo}</h3>}
            {subtitulo && <p className="text-xs text-texto2 mt-0.5">{subtitulo}</p>}
          </div>
          {accion && <div>{accion}</div>}
        </div>
      )}
      {children}
    </div>
  );
};
