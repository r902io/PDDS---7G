import React, { useEffect, useRef } from 'react';
import { SimulationState, DailyKPIEvolutionPoint } from '../types/logistics';
import { Modal } from '../components/ui/Modal';
import { Boton } from '../components/ui/Boton';
import { Award, CheckCircle2, TrendingUp } from 'lucide-react';

interface CierreSimulacionModalProps {
  isOpen: boolean;
  onClose: () => void;
  state: SimulationState;
  onReset: () => void;
}

export const CierreSimulacionModal: React.FC<CierreSimulacionModalProps> = ({
  isOpen,
  onClose,
  state,
  onReset,
}) => {
  const canvasRef = useRef<HTMLCanvasElement | null>(null);

  // Render evolution chart in native Canvas 2D
  useEffect(() => {
    if (!isOpen) return;
    const canvas = canvasRef.current;
    if (!canvas) return;
    const ctx = canvas.getContext('2d');
    if (!ctx) return;

    const dpr = window.devicePixelRatio || 1;
    const w = canvas.clientWidth;
    const h = canvas.clientHeight;
    canvas.width = w * dpr;
    canvas.height = h * dpr;
    ctx.scale(dpr, dpr);

    // Background
    ctx.fillStyle = '#101b2d'; // --color-panel
    ctx.fillRect(0, 0, w, h);

    const data = state.dailyEvolution;
    if (data.length === 0) return;

    const padLeft = 40;
    const padRight = 30;
    const padTop = 30;
    const padBottom = 35;
    const chartW = w - padLeft - padRight;
    const chartH = h - padTop - padBottom;

    // Draw grid
    ctx.strokeStyle = '#24364f';
    ctx.lineWidth = 1;

    // Days 1..5 on X axis
    const days = [1, 2, 3, 4, 5];
    days.forEach((day, idx) => {
      const x = padLeft + (idx / (days.length - 1)) * chartW;
      ctx.beginPath();
      ctx.moveTo(x, padTop);
      ctx.lineTo(x, padTop + chartH);
      ctx.stroke();

      ctx.fillStyle = '#a7b6c9';
      ctx.font = '10px ui-monospace, monospace';
      ctx.textAlign = 'center';
      ctx.fillText(`Día ${day}`, x, padTop + chartH + 18);
    });

    // Draw deliveries line (mint)
    const maxOrders = Math.max(50, ...data.map((d) => d.deliveredOrdersAccum));
    ctx.strokeStyle = '#34d399'; // Mint
    ctx.lineWidth = 2;
    ctx.beginPath();
    data.forEach((pt, idx) => {
      const x = padLeft + ((pt.day - 1) / (days.length - 1)) * chartW;
      const y = padTop + chartH - (pt.deliveredOrdersAccum / maxOrders) * chartH;
      if (idx === 0) ctx.moveTo(x, y);
      else ctx.lineTo(x, y);
    });
    ctx.stroke();

    // Draw min slack line (amber)
    const maxSlack = 10;
    ctx.strokeStyle = '#fbbf24'; // Ámbar
    ctx.lineWidth = 2;
    ctx.setLineDash([4, 3]);
    ctx.beginPath();
    data.forEach((pt, idx) => {
      const x = padLeft + ((pt.day - 1) / (days.length - 1)) * chartW;
      const y = padTop + chartH - (Math.min(maxSlack, pt.minSlackHours) / maxSlack) * chartH;
      if (idx === 0) ctx.moveTo(x, y);
      else ctx.lineTo(x, y);
    });
    ctx.stroke();
    ctx.setLineDash([]);

    // Legend inside chart
    ctx.fillStyle = '#34d399';
    ctx.fillRect(padLeft + 10, padTop - 18, 10, 4);
    ctx.fillStyle = '#e8eef7';
    ctx.font = '10px system-ui, sans-serif';
    ctx.textAlign = 'left';
    ctx.fillText('Entregas Acumuladas', padLeft + 25, padTop - 14);

    ctx.strokeStyle = '#fbbf24';
    ctx.strokeRect(padLeft + 160, padTop - 16, 10, 2);
    ctx.fillStyle = '#e8eef7';
    ctx.fillText('Holgura Mínima (horas)', padLeft + 175, padTop - 14);
  }, [isOpen, state.dailyEvolution]);

  if (!isOpen) return null;

  return (
    <Modal
      isOpen={isOpen}
      onClose={onClose}
      titulo="Cierre de Simulación de Cinco Días (LE-052, LE-071)"
      subtitulo="120 horas simuladas concluidas • Flota retornada a almacenes"
      tamano="ancho"
    >
      <div className="space-y-4 text-sm">
        {/* Banner */}
        <div className="p-4 rounded-lg bg-mint/10 border border-mint/30 text-mint flex items-start gap-3">
          <Award className="w-6 h-6 shrink-0 mt-0.5" />
          <div>
            <h4 className="font-bold text-base">Periodo Completado con Éxito</h4>
            <p className="text-xs text-texto2 mt-0.5 leading-relaxed">
              Se completaron las 120 horas programadas de la simulación de cinco días. Todas las unidades
              han retornado a sus almacenes base y el planificador metaheurístico no registró ningún
              incumplimiento de plazos.
            </p>
          </div>
        </div>

        {/* Consolidated KPIs */}
        <div className="grid grid-cols-2 md:grid-cols-4 gap-3 text-xs">
          <div className="bg-panel border border-borde p-3 rounded-md">
            <span className="text-texto2 block text-[11px] uppercase tracking-wider">Entregas a Tiempo</span>
            <span className="text-2xl font-mono font-bold text-mint mt-1 block">
              {state.kpis.totalDeliveredOnTime}
            </span>
            <span className="text-[10px] text-texto2">100% de cumplimiento</span>
          </div>

          <div className="bg-panel border border-borde p-3 rounded-md">
            <span className="text-texto2 block text-[11px] uppercase tracking-wider">Costo Operativo Total</span>
            <span className="text-2xl font-mono font-bold text-texto mt-1 block">
              S/ {state.kpis.totalCostSoles.toFixed(2)}
            </span>
            <span className="text-[10px] text-texto2">Autos, Motos y Bicicletas</span>
          </div>

          <div className="bg-panel border border-borde p-3 rounded-md">
            <span className="text-texto2 block text-[11px] uppercase tracking-wider">Distancia Recorrida</span>
            <span className="text-2xl font-mono font-bold text-azul mt-1 block">
              {state.kpis.totalDistanceKm.toFixed(1)} km
            </span>
            <span className="text-[10px] text-texto2">Trazados ortogonales</span>
          </div>

          <div className="bg-panel border border-borde p-3 rounded-md">
            <span className="text-texto2 block text-[11px] uppercase tracking-wider">Holgura Mínima Final</span>
            <span className="text-2xl font-mono font-bold text-sem-verde mt-1 block">
              +{state.kpis.minSlackHours.toFixed(1)}h
            </span>
            <span className="text-[10px] text-texto2">Margen operativo seguro</span>
          </div>
        </div>

        {/* Canvas Chart */}
        <div className="bg-panel border border-borde rounded-md p-3 space-y-2">
          <div className="flex items-center justify-between">
            <span className="text-xs font-semibold uppercase tracking-wider text-texto flex items-center gap-2">
              <TrendingUp className="w-4 h-4 text-mint" />
              <span>Evolución Diaria de Desempeño y Holgura Temporal</span>
            </span>
            <span className="text-[11px] text-texto2 font-mono">Días 1 a 5</span>
          </div>
          <div className="h-48 w-full rounded border border-borde/70 overflow-hidden">
            <canvas ref={canvasRef} className="w-full h-full block" />
          </div>
        </div>

        {/* Actions */}
        <div className="flex items-center justify-between pt-2 border-t border-borde/60">
          <Boton variant="secundario" onClick={onClose}>
            Continuar Inspección en Mapa
          </Boton>
          <Boton variant="primario" onClick={onReset}>
            Iniciar Nueva Corrida
          </Boton>
        </div>
      </div>
    </Modal>
  );
};
