import React from 'react';
import { CollapseReport } from '../types/logistics';
import { Modal } from '../components/ui/Modal';
import { Boton } from '../components/ui/Boton';
import { AlertOctagon, FileWarning, CheckCircle, Clock, TrendingDown } from 'lucide-react';

interface ColapsoModalProps {
  report: CollapseReport | null;
  onClose: () => void;
  onReset: () => void;
}

export const ColapsoModal: React.FC<ColapsoModalProps> = ({
  report,
  onClose,
  onReset,
}) => {
  if (!report) return null;

  const isDataMissing = report.isDataMissing;

  return (
    <Modal
      isOpen={!!report}
      onClose={onClose}
      titulo={isDataMissing ? 'Detención por Ausencia de Datos de Entrada' : 'Colapso Logístico Declarado (LE-053, LE-072)'}
      subtitulo={`Instante de Simulación: ${report.timestamp} • Día ${report.simulatedDay}`}
      tamano="ancho"
    >
      <div className="space-y-4 text-sm">
        {/* Banner with distinct causes */}
        <div
          className={`p-4 rounded-lg border flex items-start gap-3 ${
            isDataMissing
              ? 'bg-ambar/10 border-ambar/40 text-ambar'
              : 'bg-rojo/15 border-rojo/50 text-rojo'
          }`}
        >
          {isDataMissing ? (
            <FileWarning className="w-6 h-6 shrink-0 mt-0.5" />
          ) : (
            <AlertOctagon className="w-6 h-6 shrink-0 mt-0.5" />
          )}

          <div>
            <h4 className="text-base font-bold">
              {isDataMissing
                ? 'Corrida Detenida: Carencia de Archivos Mensuales'
                : 'Límite de Capacidad Alcanzado: Primer Incumplimiento de Plazo'}
            </h4>
            <p className="text-xs text-texto2 mt-1 leading-relaxed">
              {isDataMissing
                ? 'La simulación avanzó hasta un periodo para el cual no existe archivo cargado en la biblioteca. Esta detención se distingue explícitamente del colapso logístico, ya que responde a falta de insumos de prueba.'
                : 'La política de PaqRap exige cero demoras (todos los pedidos deben arribar dentro de su plazo comprometido). Ante el primer deadline vencido, el reloj se detiene de forma instantánea.'}
            </p>
          </div>
        </div>

        {/* Collapsing order details if logistical collapse */}
        {!isDataMissing && report.collapsingOrderId && (
          <div className="bg-panel border border-borde rounded-md p-3.5 space-y-2">
            <h5 className="text-xs font-semibold uppercase tracking-wider text-texto flex items-center gap-2">
              <TrendingDown className="w-4 h-4 text-rojo" />
              <span>Detalle del Pedido Causal del Colapso</span>
            </h5>

            <div className="grid grid-cols-2 md:grid-cols-4 gap-3 text-xs pt-1">
              <div className="bg-panel2 p-2 rounded border border-borde">
                <span className="text-texto2 block text-[11px]">Código de Pedido</span>
                <span className="font-mono text-rojo font-bold">{report.collapsingOrderId}</span>
              </div>
              <div className="bg-panel2 p-2 rounded border border-borde">
                <span className="text-texto2 block text-[11px]">Cliente Afectado</span>
                <span className="font-mono text-texto font-medium">{report.collapsingOrderClientCode}</span>
              </div>
              <div className="bg-panel2 p-2 rounded border border-borde">
                <span className="text-texto2 block text-[11px]">Plazo Comprometido</span>
                <span className="font-mono text-texto font-medium">{report.orderDeadline}</span>
              </div>
              <div className="bg-panel2 p-2 rounded border border-borde">
                <span className="text-texto2 block text-[11px]">Holgura Registrada</span>
                <span className="font-mono text-sem-rojo font-bold">0.0h (Excedido)</span>
              </div>
            </div>

            <p className="text-xs text-texto2 bg-panel2 p-2 rounded border border-borde/70 mt-2">
              {report.details}
            </p>
          </div>
        )}

        {/* Summary metrics achieved before collapse */}
        <div className="grid grid-cols-2 md:grid-cols-3 gap-3">
          <div className="bg-panel border border-borde rounded-md p-3">
            <span className="text-[11px] uppercase tracking-wider text-texto2 block">Días de Operación Alcanzados</span>
            <div className="text-xl font-mono font-bold text-texto mt-1">{report.totalDaysReached} días</div>
            <span className="text-[10px] text-texto2">Capacidad máxima sostenida</span>
          </div>

          <div className="bg-panel border border-borde rounded-md p-3">
            <span className="text-[11px] uppercase tracking-wider text-texto2 block">Entregas a Tiempo Logradas</span>
            <div className="text-xl font-mono font-bold text-mint mt-1">{report.totalDeliveriesCompleted} pedidos</div>
            <span className="text-[10px] text-texto2">100% de cumplimiento previo</span>
          </div>

          <div className="bg-panel border border-borde rounded-md p-3">
            <span className="text-[11px] uppercase tracking-wider text-texto2 block">Causa Determinada</span>
            <div className="text-sm font-mono font-semibold text-ambar mt-1 truncate">
              {report.cause === 'SATURACION_DEMANDA' ? 'Saturación de Demanda' : report.cause}
            </div>
            <span className="text-[10px] text-texto2">Diagnóstico de simulación</span>
          </div>
        </div>

        {/* Footer actions */}
        <div className="flex items-center justify-between pt-3 border-t border-borde/60">
          <Boton variant="secundario" onClick={onClose}>
            Examinar Estado Final en el Mapa
          </Boton>
          <Boton variant="primario" onClick={onReset}>
            Reiniciar Escenario
          </Boton>
        </div>
      </div>
    </Modal>
  );
};
