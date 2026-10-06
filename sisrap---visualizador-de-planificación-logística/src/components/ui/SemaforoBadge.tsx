import React from 'react';
import { CriticalityLevel } from '../../types/logistics';

interface SemaforoBadgeProps {
  nivel: CriticalityLevel;
  slackHours: number;
  mostrarEtiqueta?: boolean;
}

export const SemaforoBadge: React.FC<SemaforoBadgeProps> = ({
  nivel,
  slackHours,
  mostrarEtiqueta = true,
}) => {
  let badgeClasses = '';
  let dotColor = '';
  let labelText = '';

  if (nivel === 'VERDE') {
    badgeClasses = 'bg-sem-verde/15 text-sem-verde border border-sem-verde/30';
    dotColor = 'bg-sem-verde';
    labelText = 'VERDE';
  } else if (nivel === 'AMBAR') {
    badgeClasses = 'bg-sem-ambar/15 text-sem-ambar border border-sem-ambar/30';
    dotColor = 'bg-sem-ambar';
    labelText = 'ÁMBAR';
  } else {
    badgeClasses = 'bg-sem-rojo/15 text-sem-rojo border border-sem-rojo/30';
    dotColor = 'bg-sem-rojo';
    labelText = 'ROJO';
  }

  const formattedHours = slackHours >= 0 ? `+${slackHours.toFixed(1)}h` : `${slackHours.toFixed(1)}h`;

  return (
    <span
      className={`inline-flex items-center gap-1.5 px-2 py-0.5 rounded text-xs font-mono font-medium ${badgeClasses}`}
      title={`Holgura temporal: ${formattedHours} - Estado ${labelText}`}
    >
      <span className={`w-1.5 h-1.5 rounded-full ${dotColor} shrink-0`} />
      <span>{formattedHours}</span>
      {mostrarEtiqueta && (
        <span className="text-[10px] font-sans opacity-80 uppercase tracking-tighter">
          [{labelText}]
        </span>
      )}
    </span>
  );
};
