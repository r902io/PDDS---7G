import { useMemo, useState } from 'react';
import type { AlmacenOperativo, PedidoOperativo } from '../../types/api';
import { obtenerColorSemaforoAlmacen, obtenerColorHexAlmacen } from '../../utilitarios/almacenes';
import type { Seleccion } from '../../hooks/useMapCanvas';

export type SemaforoFiltro = 'VERDE' | 'AMARILLO' | 'ROJO' | 'VACIO';

export function esAlmacenVisible(almacen: AlmacenOperativo, semaforos: SemaforoFiltro[]): boolean {
    const color = obtenerColorSemaforoAlmacen(almacen);
    return semaforos.includes(color === 'AMBAR' ? 'AMARILLO' : color);
}

interface Props {
    almacenes: AlmacenOperativo[];
    pedidos?: PedidoOperativo[];
    relojSimulado?: string | null;
    seleccion: Seleccion;
    alSeleccionar: (s: Seleccion) => void;
    // D07: Props opcionales para integración global de filtros
    semaforoAlmacenes?: SemaforoFiltro[];
    onToggleSemaforoAlmacen?: (estado: SemaforoFiltro) => void;
}

export function ListaAlmacenes({
                                   almacenes,
                                   pedidos = [],
                                   relojSimulado,
                                   seleccion,
                                   alSeleccionar,
                                   semaforoAlmacenes: semaforoProp,
                                   onToggleSemaforoAlmacen,
                               }: Props) {
    const [busqueda, setBusqueda] = useState('');
    const [filtroTipo, setFiltroTipo] = useState<'TODOS' | 'CENTRAL' | 'INTERMEDIO'>('TODOS');
    const [horasVentana, setHorasVentana] = useState<number>(12);
    const [almacenExpandido, setAlmacenExpandido] = useState<string | null>(null);
    const [categoriaActiva, setCategoriaActiva] = useState<'POR_SALIR' | 'ENTREGADOS' | 'TRANSITO'>('POR_SALIR');

    // D07: Estado local fallback si no viene desde un context/hook superior
    const [semaforoLocal, setSemaforoLocal] = useState<SemaforoFiltro[]>(['VERDE', 'AMARILLO', 'ROJO', 'VACIO']);

    const semaforosActivos = semaforoProp ?? semaforoLocal;
    const toggleSemaforo = (estado: SemaforoFiltro) => {
        if (onToggleSemaforoAlmacen) {
            onToggleSemaforoAlmacen(estado);
        } else {
            setSemaforoLocal(prev =>
                prev.includes(estado) ? prev.filter(s => s !== estado) : [...prev, estado]
            );
        }
    };

    // Convierte fecha ISO/Local a Timestamp
    const ahoraMs = useMemo(() => {
        if (!relojSimulado) return Date.now();
        const iso = relojSimulado.includes('Z') ? relojSimulado : `${relojSimulado}Z`;
        return new Date(iso).getTime();
    }, [relojSimulado]);

    const almacenesFiltrados = useMemo(() => {
        return almacenes.filter((a) => {
            const coincideNombre = a.nombre.toLowerCase().includes(busqueda.toLowerCase());
            const coincideTipo = filtroTipo === 'TODOS' || a.tipo === filtroTipo;
            // D07: Validación del semáforo de capacidad del almacén
            const coincideSemaforo = esAlmacenVisible(a, semaforosActivos);
            return coincideNombre && coincideTipo && coincideSemaforo;
        });
    }, [almacenes, busqueda, filtroTipo, semaforosActivos]);

    return (
        <div className="flex flex-col gap-3 p-3">
            {/* Controles: Búsqueda y Selector de Horizonte X Horas */}
            <div className="flex flex-col gap-2">
                <div className="flex gap-2">
                    <input
                        type="text"
                        placeholder="Buscar almacén..."
                        value={busqueda}
                        onChange={(e) => setBusqueda(e.target.value)}
                        className="px-3 py-1.5 text-sm bg-slate-900 border border-slate-700 rounded text-white w-full"
                    />
                    <select
                        value={filtroTipo}
                        onChange={(e) => setFiltroTipo(e.target.value as any)}
                        className="px-2 py-1.5 text-sm bg-slate-900 border border-slate-700 rounded text-white"
                    >
                        <option value="TODOS">Todos</option>
                        <option value="CENTRAL">Central</option>
                        <option value="INTERMEDIO">Intermedio</option>
                    </select>
                </div>

                {/* D07: Botonera interactiva de filtro por Semáforo de Almacén */}
                <div className="flex flex-col gap-1.5 text-xs bg-slate-900/80 p-2 rounded border border-slate-800 text-slate-300">
                    <span className="text-[11px] font-semibold text-slate-400">Estado Capacidad:</span>
                    <div className="flex justify-between gap-1">
                        {[
                            { id: 'VERDE', label: 'Verde', color: 'bg-emerald-500' },
                            { id: 'AMARILLO', label: 'Amarillo', color: 'bg-amber-500' },
                            { id: 'ROJO', label: 'Rojo', color: 'bg-rose-500' },
                            { id: 'VACIO', label: 'Vacío', color: 'bg-slate-400' },
                        ].map((item) => {
                            const activo = semaforosActivos.includes(item.id as SemaforoFiltro);
                            return (
                                <button
                                    key={item.id}
                                    type="button"
                                    onClick={() => toggleSemaforo(item.id as SemaforoFiltro)}
                                    aria-pressed={activo}
                                    className={`flex-1 flex items-center justify-center gap-1 px-2 py-1 rounded text-[10px] font-semibold border transition-colors ${
                                        activo
                                            ? 'bg-slate-800 border-slate-600 text-slate-100 shadow-sm'
                                            : 'bg-slate-950 border-slate-800 text-slate-500 opacity-50 hover:opacity-80'
                                    }`}
                                >
                                    <span className={`w-1.5 h-1.5 rounded-full ${item.color}`} />
                                    {item.label}
                                </button>
                            );
                        })}
                    </div>
                </div>

                {/* Requerimiento 1 y 2: Selector de Ventana de X Horas */}
                <div className="flex items-center justify-between text-xs bg-slate-900/80 p-2 rounded border border-slate-800 text-slate-300">
                    <span>Horizonte de análisis (hrs):</span>
                    <select
                        value={horasVentana}
                        onChange={(e) => setHorasVentana(Number(e.target.value))}
                        className="px-2 py-1 bg-slate-800 border border-slate-700 rounded text-white font-bold"
                    >
                        <option value={4}>4 horas</option>
                        <option value={8}>8 horas</option>
                        <option value={12}>12 horas</option>
                        <option value={24}>24 horas</option>
                    </select>
                </div>
            </div>

            {/* Lista de Almacenes con métricas y acceso desplegable */}
            <div className="flex flex-col gap-2 max-h-[520px] overflow-y-auto pr-1">
                {almacenesFiltrados.map((almacen) => {
                    const semaforo = obtenerColorSemaforoAlmacen(almacen);
                    const colorHex = obtenerColorHexAlmacen(semaforo);
                    const esSeleccionado = seleccion?.tipo === 'almacen' && seleccion.id === almacen.idAlmacen;
                    const estaExpandido = almacenExpandido === almacen.idAlmacen;

                    const ventanaMs = horasVentana * 3_600_000;

                    // Asignación de pedidos al almacén
                    const pedidosDelAlmacen = pedidos.filter(p =>
                        almacen.tipo === 'CENTRAL' ||
                        (p.ubicacionX === almacen.ubicacionX && p.ubicacionY === almacen.ubicacionY)
                    );

                    // 1. REQUERIMIENTO 1: Envíos y paquetes por salir en las siguientes X horas
                    const listaPorSalir = pedidosDelAlmacen.filter(p => {
                        if (p.estado !== 'PENDIENTE') return false;
                        const tLlegada = new Date(p.fechaLlegada.includes('Z') ? p.fechaLlegada : `${p.fechaLlegada}Z`).getTime();
                        return tLlegada >= ahoraMs && tLlegada <= ahoraMs + ventanaMs;
                    });
                    const qqPorSalir = listaPorSalir.reduce((acc, p) => acc + (p.cantidadQq ?? 0), 0);

                    // 2. REQUERIMIENTO 2: Envíos y paquetes entregados en las últimas X horas
                    const listaEntregados = pedidosDelAlmacen.filter(p => {
                        if (p.estado !== 'ENTREGADO' || !p.fechaEntregaReal) return false;
                        const tEntrega = new Date(p.fechaEntregaReal.includes('Z') ? p.fechaEntregaReal : `${p.fechaEntregaReal}Z`).getTime();
                        return tEntrega <= ahoraMs && tEntrega >= ahoraMs - ventanaMs;
                    });
                    const qqEntregados = listaEntregados.reduce((acc, p) => acc + (p.cantidadQq ?? 0), 0);

                    // 3. Envíos y paquetes en tránsito activo
                    const listaEnTransito = pedidosDelAlmacen.filter(p => p.estado === 'EN_RUTA');
                    const qqEnTransito = listaEnTransito.reduce((acc, p) => acc + (p.cantidadQq ?? 0), 0);

                    const listaDetalle = categoriaActiva === 'POR_SALIR'
                        ? listaPorSalir
                        : categoriaActiva === 'ENTREGADOS'
                            ? listaEntregados
                            : listaEnTransito;

                    return (
                        <div
                            key={almacen.idAlmacen}
                            className={`flex flex-col gap-2 p-3 rounded-lg border cursor-pointer transition-all ${
                                esSeleccionado ? 'bg-slate-800 border-indigo-500 shadow-lg' : 'bg-slate-800/60 border-slate-700/60 hover:bg-slate-800'
                            }`}
                            onClick={() => alSeleccionar({ tipo: 'almacen', id: almacen.idAlmacen })}
                        >
                            {/* Encabezado e indicador del Semáforo */}
                            <div className="flex items-center justify-between">
                                <div>
                                    <div className="font-semibold text-sm text-white">{almacen.nombre}</div>
                                    <div className="text-xs text-slate-400">
                                        Stock: <span className="text-slate-200 font-medium">{almacen.stockActual ?? 'Ilimitado'}</span>
                                        {almacen.capacidadMaxima ? ` / ${almacen.capacidadMaxima}` : ''}
                                    </div>
                                </div>

                                <span
                                    className="px-2.5 py-0.5 text-[10px] font-bold rounded-full text-slate-950 uppercase"
                                    style={{ backgroundColor: colorHex }}
                                >
                  {almacen.tipo === 'CENTRAL' ? 'CENTRAL' : semaforo}
                </span>
                            </div>

                            {/* Botones de acceso rápido a las métricas */}
                            <div className="grid grid-cols-3 gap-1.5 pt-2 border-t border-slate-700/60 text-center text-[11px]">
                                <button
                                    type="button"
                                    onClick={(e) => {
                                        e.stopPropagation();
                                        setAlmacenExpandido(estaExpandido && categoriaActiva === 'POR_SALIR' ? null : almacen.idAlmacen);
                                        setCategoriaActiva('POR_SALIR');
                                    }}
                                    className={`p-1.5 rounded border transition-colors ${
                                        estaExpandido && categoriaActiva === 'POR_SALIR'
                                            ? 'bg-amber-950/80 border-amber-500 text-amber-300'
                                            : 'bg-slate-900/60 border-slate-800 hover:bg-slate-900 text-slate-300'
                                    }`}
                                >
                                    <span className="block text-[10px] text-slate-400">Por salir (+{horasVentana}h)</span>
                                    <span className="font-bold text-amber-400">{listaPorSalir.length} envíos</span>
                                    <span className="block text-[10px] text-slate-400">{qqPorSalir} qq</span>
                                </button>

                                <button
                                    type="button"
                                    onClick={(e) => {
                                        e.stopPropagation();
                                        setAlmacenExpandido(estaExpandido && categoriaActiva === 'ENTREGADOS' ? null : almacen.idAlmacen);
                                        setCategoriaActiva('ENTREGADOS');
                                    }}
                                    className={`p-1.5 rounded border transition-colors ${
                                        estaExpandido && categoriaActiva === 'ENTREGADOS'
                                            ? 'bg-emerald-950/80 border-emerald-500 text-emerald-300'
                                            : 'bg-slate-900/60 border-slate-800 hover:bg-slate-900 text-slate-300'
                                    }`}
                                >
                                    <span className="block text-[10px] text-slate-400">Entregados (-{horasVentana}h)</span>
                                    <span className="font-bold text-emerald-400">{listaEntregados.length} envíos</span>
                                    <span className="block text-[10px] text-slate-400">{qqEntregados} qq</span>
                                </button>

                                <button
                                    type="button"
                                    onClick={(e) => {
                                        e.stopPropagation();
                                        setAlmacenExpandido(estaExpandido && categoriaActiva === 'TRANSITO' ? null : almacen.idAlmacen);
                                        setCategoriaActiva('TRANSITO');
                                    }}
                                    className={`p-1.5 rounded border transition-colors ${
                                        estaExpandido && categoriaActiva === 'TRANSITO'
                                            ? 'bg-sky-950/80 border-sky-500 text-sky-300'
                                            : 'bg-slate-900/60 border-slate-800 hover:bg-slate-900 text-slate-300'
                                    }`}
                                >
                                    <span className="block text-[10px] text-slate-400">En Tránsito</span>
                                    <span className="font-bold text-sky-400">{listaEnTransito.length} envíos</span>
                                    <span className="block text-[10px] text-slate-400">{qqEnTransito} qq</span>
                                </button>
                            </div>

                            {/* PANEL DE ACCESO A LISTADO DE ENVÍOS Y PAQUETES (DESPLEGABLE) */}
                            {estaExpandido && (
                                <div
                                    className="flex flex-col gap-2 mt-1 pt-2 border-t border-slate-700 bg-slate-950 p-2.5 rounded-md text-xs"
                                    onClick={(e) => e.stopPropagation()}
                                >
                                    <div className="flex items-center justify-between border-b border-slate-800 pb-1">
                    <span className="font-semibold text-slate-200">
                      {categoriaActiva === 'POR_SALIR' && `Envíos por Salir (Próximas ${horasVentana}h)`}
                        {categoriaActiva === 'ENTREGADOS' && `Envíos Entregados (Últimas ${horasVentana}h)`}
                        {categoriaActiva === 'TRANSITO' && 'Envíos en Tránsito Activo'}
                    </span>
                                        <span className="text-[11px] text-slate-400 font-mono">
                      {listaDetalle.length} envíos | {listaDetalle.reduce((a, b) => a + (b.cantidadQq ?? 0), 0)} qq
                    </span>
                                    </div>

                                    {listaDetalle.length === 0 ? (
                                        <div className="text-slate-500 italic text-center py-2 text-[11px]">
                                            No hay paquetes ni envíos registrados en este rango de tiempo.
                                        </div>
                                    ) : (
                                        <div className="flex flex-col gap-1 max-h-36 overflow-y-auto pr-1">
                                            {listaDetalle.map((p) => (
                                                <div
                                                    key={p.idPedido}
                                                    onClick={() => alSeleccionar({ tipo: 'pedido', id: p.idPedido })}
                                                    className="flex items-center justify-between bg-slate-900 hover:bg-slate-850 p-1.5 rounded border border-slate-800 text-[11px] transition-colors"
                                                >
                                                    <div>
                                                        <span className="font-semibold text-slate-200">Pedido #{p.idPedido}</span>
                                                        <span className="text-slate-400 ml-2">({p.idCliente})</span>
                                                    </div>
                                                    <div className="text-right">
                                                        <span className="font-bold text-indigo-400">{p.cantidadQq} qq</span>
                                                        <span className="text-slate-500 text-[10px] block">{p.estado}</span>
                                                    </div>
                                                </div>
                                            ))}
                                        </div>
                                    )}
                                </div>
                            )}
                        </div>
                    );
                })}
            </div>
        </div>
    );
}