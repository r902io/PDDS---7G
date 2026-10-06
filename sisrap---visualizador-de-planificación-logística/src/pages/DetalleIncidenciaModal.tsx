import React from 'react';
import { Incident, Vehicle, Order } from '../types/logistics';
import { Modal } from '../components/ui/Modal';
import { Boton } from '../components/ui/Boton';
import { AlertTriangle, ShieldAlert, Truck, ArrowRight, CornerDownRight } from 'lucide-react';

interface DetalleIncidenciaModalProps {
  incident: Incident | null;
  onClose: () => void;
  vehicles: Vehicle[];
  orders: Order[];
}

export const DetalleIncidenciaModal: React.FC<DetalleIncidenciaModalProps> = ({
  incident,
  onClose,
  vehicles,
  orders,
}) => {
  if (!incident) return null;

  const affectedVehicle = incident.affectedVehicleId
    ? vehicles.find((v) => v.id === incident.affectedVehicleId)
    : undefined;

  const impactedOrdersList = orders.filter((o) => incident.impactedOrders.includes(o.id));

  return (
    <Modal
      isOpen={!!incident}
      onClose={onClose}
      titulo={incident.type === 'BLOQUEO_VIA' ? 'Detalle de Bloqueo Programado' : 'Detalle de Avería de Unidad'}
      subtitulo={`Identificador: ${incident.id} • Registrado a las ${incident.timestamp}`}
      tamano="ancho"
    >
      <div className="space-y-4 text-sm">
        {/* Banner */}
        <div
          className={`p-3.5 rounded-lg border flex items-start gap-3 ${
            incident.type === 'BLOQUEO_VIA'
              ? 'bg-ambar/10 border-ambar/30 text-ambar'
              : 'bg-rojo/10 border-rojo/30 text-rojo'
          }`}
        >
          {incident.type === 'BLOQUEO_VIA' ? (
            <ShieldAlert className="w-5 h-5 shrink-0 mt-0.5" />
          ) : (
            <AlertTriangle className="w-5 h-5 shrink-0 mt-0.5" />
          )}
          <div>
            <h4 className="font-semibold">{incident.title}</h4>
            <p className="text-xs text-texto2 mt-0.5">
              {incident.type === 'BLOQUEO_VIA'
                ? 'El bloqueo afecta a la red vial y no a las unidades particulares; el planificador excluye el tramo y busca rutas alternativas.'
                : 'La avería afecta exclusivamente a la unidad y no a la vía. Se inmoviliza y se dispara reasignación de carga priorizando al cliente más crítico.'}
            </p>
          </div>
        </div>

        {/* Breakdown Specific Info */}
        {incident.type === 'AVERIA_UNIDAD' && affectedVehicle && (
          <div className="bg-panel border border-borde rounded-md p-3 space-y-2">
            <h5 className="text-xs font-semibold uppercase tracking-wider text-texto flex items-center gap-2">
              <Truck className="w-4 h-4 text-azul" />
              <span>Unidad Afectada y Estado de Contingencia</span>
            </h5>
            <div className="grid grid-cols-2 md:grid-cols-4 gap-2 text-xs">
              <div>
                <span className="text-texto2 block text-[11px]">Vehículo / Placa</span>
                <span className="font-mono text-texto font-medium">{affectedVehicle.plate} ({affectedVehicle.type})</span>
              </div>
              <div>
                <span className="text-texto2 block text-[11px]">Tipo de Avería</span>
                <span className="font-mono text-rojo font-medium">
                  {affectedVehicle.breakdownDetails?.type || 'MODERADA'}
                </span>
              </div>
              <div>
                <span className="text-texto2 block text-[11px]">Ubicación del Percance</span>
                <span className="font-mono text-texto font-medium">
                  ({affectedVehicle.coord.x}, {affectedVehicle.coord.y})
                </span>
              </div>
              <div>
                <span className="text-texto2 block text-[11px]">Retorno Estimado</span>
                <span className="font-mono text-texto font-medium">
                  {affectedVehicle.breakdownDetails?.estimatedReturnTime || '+6h'}
                </span>
              </div>
            </div>
          </div>
        )}

        {/* Road Block Specific Info */}
        {incident.type === 'BLOQUEO_VIA' && incident.roadSegment && (
          <div className="bg-panel border border-borde rounded-md p-3 space-y-2">
            <h5 className="text-xs font-semibold uppercase tracking-wider text-texto">
              Tramo Vial Afectado
            </h5>
            <div className="grid grid-cols-2 gap-3 text-xs">
              <div>
                <span className="text-texto2 block text-[11px]">Nodo Extremo Origen</span>
                <span className="font-mono text-texto font-medium">
                  ({incident.roadSegment.from.x}, {incident.roadSegment.from.y})
                </span>
              </div>
              <div>
                <span className="text-texto2 block text-[11px]">Nodo Extremo Destino</span>
                <span className="font-mono text-texto font-medium">
                  ({incident.roadSegment.to.x}, {incident.roadSegment.to.y})
                </span>
              </div>
            </div>
          </div>
        )}

        {/* Impacted Orders */}
        <div className="bg-panel border border-borde rounded-md p-3 space-y-2">
          <h5 className="text-xs font-semibold uppercase tracking-wider text-texto">
            Pedidos Impactados y Criterio de Prioridad (LE-081, LE-082)
          </h5>

          {impactedOrdersList.length === 0 ? (
            <p className="text-xs text-texto2 italic">No hay pedidos directamente a bordo al momento de la incidencia.</p>
          ) : (
            <div className="space-y-2">
              {impactedOrdersList.map((order) => (
                <div
                  key={order.id}
                  className="p-2.5 rounded bg-panel2 border border-borde flex items-center justify-between text-xs"
                >
                  <div>
                    <span className="font-mono font-bold text-texto">{order.id}</span>
                    <span className="text-texto2 ml-2">Cliente: {order.clientCode} ({order.clientName})</span>
                    <span className="text-texto2 ml-2 font-mono">[{order.quantity} paquetes]</span>
                  </div>
                  <div className="flex items-center gap-2">
                    <span className="text-texto2 text-[11px]">Plazo:</span>
                    <span className="font-mono text-texto font-medium">{order.deadlineHours}h</span>
                    <span className="font-mono text-xs px-2 py-0.5 rounded bg-sem-rojo/20 text-sem-rojo font-bold">
                      {order.slackHours.toFixed(1)}h
                    </span>
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>

        {/* Alternative Solution & Cost Differential */}
        <div className="bg-panel border border-borde rounded-md p-3 space-y-2">
          <h5 className="text-xs font-semibold uppercase tracking-wider text-texto flex items-center gap-2">
            <CornerDownRight className="w-4 h-4 text-mint" />
            <span>Alternativa Adoptada por el Planificador Metaheurístico</span>
          </h5>
          <p className="text-xs text-texto font-mono bg-panel2 p-2 rounded border border-borde">
            {incident.alternativeSolution}
          </p>

          <div className="grid grid-cols-2 gap-3 text-xs pt-1">
            <div className="flex justify-between p-2 rounded bg-panel2">
              <span className="text-texto2">Diferencial de Distancia:</span>
              <span className="font-mono text-azul font-semibold">+{incident.costDifferenceKm.toFixed(1)} km</span>
            </div>
            <div className="flex justify-between p-2 rounded bg-panel2">
              <span className="text-texto2">Diferencial de Costo:</span>
              <span className="font-mono text-ambar font-semibold">+S/ {incident.costDifferenceSoles.toFixed(2)}</span>
            </div>
          </div>
        </div>

        <div className="flex justify-end pt-2">
          <Boton variant="secundario" onClick={onClose}>
            Cerrar Detalle
          </Boton>
        </div>
      </div>
    </Modal>
  );
};
