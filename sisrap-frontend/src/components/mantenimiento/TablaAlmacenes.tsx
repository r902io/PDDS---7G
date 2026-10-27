import { useMemo, useState } from 'react';
import type { AlmacenOperativo, PedidoOperativo } from '../../types/api';
// Se remueve "import { dibujarForma } from '../iconos/formas';"
import { obtenerColorSemaforoAlmacen, obtenerColorHexAlmacen } from '../../utilitarios/almacenes';
interface Props {
    almacenes: AlmacenOperativo[];
    pedidos?: PedidoOperativo[];
    relojSimulado?: string | null;
    alSeleccionar?: (idAlmacen: string) => void;
}

export function TablaAlmacenes({ almacenes, pedidos = [], relojSimulado, alSeleccionar }: Props) {
    const [horasVentana, setHorasVentana] = useState<number>(12);
    const [almacenExpandido, setAlmacenExpandido] = useState<string | null>(null);

    const ahoraMs = useMemo(() => {
        if (!relojSimulado) return Date.now();
        return new Date(relojSimulado.replace(/Z$/, '') + 'Z').getTime();
    }, [relojSimulado]);

    return (
        <div className="flex flex-col gap-4 w-full">
            {/* Barra de control: Cantidad de almacenes y selector de horizonte X horas */}
            <div className="flex items-center justify-between bg-slate-900/80 p-3 rounded-lg border border-slate-800 text-xs text-slate-300">
                <span className="font-semibold text-slate-200">{almacenes.length} almacenes</span>

                <div className="flex items-center gap-2">
                    <span className="text-slate-400">Ventana de consulta ($X$ horas):</span>
                    <select
                        value={horasVentana}
                        onChange={(e) => setHorasVentana(Number(e.target.value))}
                        className="px-2.5 py-1 bg-slate-800 border border-slate-700 rounded text-white font-bold"
                    >
                        <option value={4}>4 horas</option>
                        <option value={8}>8 horas</option>
                        <option value={12}>12 horas</option>
                        <option value={24}>24 horas</option>
                    </select>
                </div>
            </div>

            {/* Tabla de Almacenes */}
            <div className="overflow-x-auto rounded-lg border border-slate-800 bg-slate-950">
                <table className="w-full text-left text-xs text-slate-300">
                    <thead className="bg-slate-900/90 text-slate-400 uppercase font-mono border-b border-slate-800">
                    <tr>
                        <th className="p-3">Código</th>
                        <th className="p-3">Nombre</th>
                        <th className="p-3">Tipo</th>
                        <th className="p-3">Ubicación</th>
                        <th className="p-3">Capacidad</th>
                        <th className="p-3">Stock Actual</th>
                        <th className="p-3 text-center">Estado (Semáforo)</th>
                        <th className="p-3 text-center">Por Salir (+{horasVentana}h)</th>
                        <th className="p-3 text-center">Entregados (-{horasVentana}h)</th>
                        <th className="p-3 text-center">En Tránsito</th>
                    </tr>
                    </thead>
                    <tbody className="divide-y divide-slate-800/60">
                    {almacenes.map((a) => {
                        // C21/E19: Semáforo con color dinámico (Rojo para 1,000/1,000)
                        const semaforo = obtenerColorSemaforoAlmacen(a);
                        const colorSem = obtenerColorHexAlmacen(semaforo);
                        const estaExpandido = almacenExpandido === a.idAlmacen;

                        const ventanaMs = horasVentana * 3_600_000;

                        // Filtrar pedidos por almacén
                        const pedidosAlmacen = pedidos.filter((p) =>
                            a.tipo === 'CENTRAL' || (p.ubicacionX === a.ubicacionX && p.ubicacionY === a.ubicacionY)
                        );

                        // 1. Envíos y paquetes por salir en las siguientes X horas
                        const porSalir = pedidosAlmacen.filter((p) => {
                            if (p.estado !== 'PENDIENTE') return false;
                            const tLlegada = new Date(p.fechaLlegada).getTime();
                            return tLlegada >= ahoraMs && tLlegada <= ahoraMs + ventanaMs;
                        });
                        const qqPorSalir = porSalir.reduce((acc, p) => acc + (p.cantidadQq ?? 0), 0);

                        // 2. Envíos y paquetes entregados en las últimas X horas
                        const entregados = pedidosAlmacen.filter((p) => {
                            if (p.estado !== 'ENTREGADO' || !p.fechaEntregaReal) return false;
                            const tEntrega = new Date(p.fechaEntregaReal).getTime();
                            return tEntrega <= ahoraMs && tEntrega >= ahoraMs - ventanaMs;
                        });
                        const qqEntregados = entregados.reduce((acc, p) => acc + (p.cantidadQq ?? 0), 0);

                        // 3. Envíos y paquetes en tránsito
                        const enTransito = pedidosAlmacen.filter((p) => p.estado === 'EN_RUTA');
                        const qqEnTransito = enTransito.reduce((acc, p) => acc + (p.cantidadQq ?? 0), 0);

                        return (
                            <>
                                <tr
                                    key={a.idAlmacen}
                                    onClick={() => {
                                        alSeleccionar?.(a.idAlmacen);
                                        setAlmacenExpandido(estaExpandido ? null : a.idAlmacen);
                                    }}
                                    className="hover:bg-slate-900/60 cursor-pointer transition-colors"
                                >
                                    <td className="p-3 font-mono text-slate-400">{a.idAlmacen}</td>
                                    <td className="p-3 font-semibold text-white flex items-center gap-2">
                                        {/* Ícono dinámico pintado con el semáforo */}
                                        <span
                                            className="w-3 h-3 rounded-full inline-block"
                                            style={{ backgroundColor: colorSem }}
                                        />
                                        {a.nombre}
                                    </td>
                                    <td className="p-3">{a.tipo === 'CENTRAL' ? 'Central' : 'Intermedio'}</td>
                                    <td className="p-3 font-mono">({a.ubicacionX}, {a.ubicacionY})</td>
                                    <td className="p-3 font-mono">
                                        {a.capacidadMaxima !== null ? a.capacidadMaxima.toLocaleString() : 'Ilimitada'}
                                    </td>
                                    <td className="p-3 font-mono">
                                        {a.stockActual !== null ? a.stockActual.toLocaleString() : 'Ilimitado'}
                                    </td>
                                    <td className="p-3 text-center">
                      <span
                          className="px-2 py-0.5 text-[10px] font-bold rounded-full text-slate-950 uppercase"
                          style={{ backgroundColor: colorSem }}
                      >
                        {a.tipo === 'CENTRAL' ? 'CENTRAL' : semaforo}
                      </span>
                                    </td>
                                    {/* Acceso directo a Paquetes Por Salir (+Xh) */}
                                    <td className="p-3 text-center">
                                        <span className="font-bold text-amber-400 block">{porSalir.length} envíos</span>
                                        <span className="text-[10px] text-slate-400">{qqPorSalir} qq</span>
                                    </td>
                                    {/* Acceso directo a Paquetes Entregados (-Xh) */}
                                    <td className="p-3 text-center">
                                        <span className="font-bold text-emerald-400 block">{entregados.length} envíos</span>
                                        <span className="text-[10px] text-slate-400">{qqEntregados} qq</span>
                                    </td>
                                    {/* Acceso directo a Paquetes En Tránsito */}
                                    <td className="p-3 text-center">
                                        <span className="font-bold text-sky-400 block">{enTransito.length} envíos</span>
                                        <span className="text-[10px] text-slate-400">{qqEnTransito} qq</span>
                                    </td>
                                </tr>

                                {/* Panel expandible de detalle de paquetes por salir y entregados */}
                                {estaExpandido && (
                                    <tr key={`${a.idAlmacen}-detalle`} className="bg-slate-900/90">
                                        <td colSpan={10} className="p-4 border-t border-slate-800">
                                            <div className="grid grid-cols-2 gap-4">
                                                <div className="p-3 bg-slate-950 rounded border border-slate-800">
                                                    <div className="font-semibold text-amber-400 mb-2">
                                                        Envíos por salir en las siguientes {horasVentana} horas ({porSalir.length} envíos / {qqPorSalir} qq):
                                                    </div>
                                                    {porSalir.length === 0 ? (
                                                        <span className="text-slate-500 italic">No hay envíos programados.</span>
                                                    ) : (
                                                        <div className="flex flex-col gap-1 max-h-32 overflow-y-auto">
                                                            {porSalir.map((p) => (
                                                                <div key={p.idPedido} className="flex justify-between border-b border-slate-800/50 pb-1">
                                                                    <span>Pedido #{p.idPedido} ({p.idCliente})</span>
                                                                    <span className="font-mono text-amber-400">{p.cantidadQq} qq</span>
                                                                </div>
                                                            ))}
                                                        </div>
                                                    )}
                                                </div>

                                                <div className="p-3 bg-slate-950 rounded border border-slate-800">
                                                    <div className="font-semibold text-emerald-400 mb-2">
                                                        Envíos entregados en las últimas {horasVentana} horas ({entregados.length} envíos / {qqEntregados} qq):
                                                    </div>
                                                    {entregados.length === 0 ? (
                                                        <span className="text-slate-500 italic">No hay entregas registradas en la ventana.</span>
                                                    ) : (
                                                        <div className="flex flex-col gap-1 max-h-32 overflow-y-auto">
                                                            {entregados.map((p) => (
                                                                <div key={p.idPedido} className="flex justify-between border-b border-slate-800/50 pb-1">
                                                                    <span>Pedido #{p.idPedido} ({p.idCliente})</span>
                                                                    <span className="font-mono text-emerald-400">{p.cantidadQq} qq</span>
                                                                </div>
                                                            ))}
                                                        </div>
                                                    )}
                                                </div>
                                            </div>
                                        </td>
                                    </tr>
                                )}
                            </>
                        );
                    })}
                    </tbody>
                </table>
            </div>
        </div>
    );
}