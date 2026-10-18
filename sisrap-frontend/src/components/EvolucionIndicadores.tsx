import { useEffect, useState } from 'react';
import type { SnapshotSimulacion } from '../types/api';

interface Punto { reloj: string; pedidos: number; entregados: number; retrasados: number; }

/** Serie de estados recibidos durante ESTA visita; no inventa datos anteriores. */
export function EvolucionIndicadores({ snapshot }: { snapshot: SnapshotSimulacion }) {
  const [registro, setRegistro] = useState<{ id: number | null; puntos: Punto[] }>({ id: null, puntos: [] });
  useEffect(() => {
    if (!snapshot.relojSimulado || snapshot.idSimulacion == null) return;
    const nuevo: Punto = {
      reloj: snapshot.relojSimulado,
      pedidos: Math.max(0, snapshot.pedidos.total - snapshot.pedidos.futuros),
      entregados: snapshot.pedidos.entregados,
      retrasados: snapshot.pedidos.retrasados,
    };
    setRegistro(prev => {
      if (prev.id !== snapshot.idSimulacion) return { id: snapshot.idSimulacion, puntos: [nuevo] };
      const ultimo = prev.puntos.at(-1);
      if (ultimo?.reloj === nuevo.reloj) return prev;
      // Tomar una muestra cada 15 minutos simulados o ante una variación importante.
      const tiempo = Date.parse(nuevo.reloj) - Date.parse(ultimo?.reloj ?? nuevo.reloj);
      if (ultimo && tiempo < 15 * 60_000 && ultimo.entregados === nuevo.entregados && ultimo.retrasados === nuevo.retrasados) return prev;
      return { id: prev.id, puntos: [...prev.puntos.slice(-119), nuevo] };
    });
  }, [snapshot.idSimulacion, snapshot.relojSimulado, snapshot.pedidos.total, snapshot.pedidos.futuros, snapshot.pedidos.entregados, snapshot.pedidos.retrasados]);
  const puntos = registro.id === snapshot.idSimulacion ? registro.puntos : [];
  if (puntos.length < 2) return <p className="text-xs text-texto2">La evolución aparecerá conforme avancen los pedidos. Se muestra únicamente lo observado en esta sesión de visualización.</p>;
  const W = 290, H = 124, min = 12, max = Math.max(1, ...puntos.map(p => p.pedidos));
  const polilinea = (k: 'pedidos' | 'entregados' | 'retrasados') => puntos.map((p, i) => `${min + i / (puntos.length - 1) * (W - min * 2)},${H - min - p[k] / max * (H - min * 2)}`).join(' ');
  return <div>
    <svg viewBox={`0 0 ${W} ${H}`} className="w-full rounded-md border border-borde bg-bg" role="img" aria-label="Evolución de pedidos registrados, entregados y retrasados">
      {[0.25, 0.5, 0.75].map(y => <line key={y} x1={0} x2={W} y1={H * y} y2={H * y} stroke="#334155" strokeDasharray="3 4" />)}
      <polyline points={polilinea('pedidos')} fill="none" stroke="#60a5fa" strokeWidth="2" />
      <polyline points={polilinea('entregados')} fill="none" stroke="#34d399" strokeWidth="2.5" />
      <polyline points={polilinea('retrasados')} fill="none" stroke="#f87171" strokeWidth="2" />
    </svg>
    <div className="flex flex-wrap justify-between gap-2 text-[11px] mt-1.5">
      <span className="text-azul">● Ingresados</span><span className="text-mint">● Entregados</span><span className="text-rojo">● Retrasados</span>
    </div>
    <p className="text-[11px] text-texto2 mt-1">{puntos.length} muestras · la serie se reinicia al comenzar una corrida nueva.</p>
  </div>;
}
