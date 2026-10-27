import { IconoMapaSvg } from '../iconos';

export function LeyendaMapa() {
    return (
        <div className="flex flex-col gap-1.5 p-2 bg-slate-900/90 border border-slate-800 rounded text-xs text-slate-300">
            <div className="font-semibold text-slate-200 border-b border-slate-800 pb-1 mb-1">
                Leyenda de Incidencias
            </div>

            {/* Averías Diferenciadas */}
            <div className="flex items-center gap-2">
                <IconoMapaSvg forma="averiaTipo1" tamano={16} />
                <span>Avería T1 (Leve - 2h in situ)</span>
            </div>
            <div className="flex items-center gap-2">
                <IconoMapaSvg forma="averiaTipo2" tamano={16} />
                <span>Avería T2 (Moderada - Fin sig. turno)</span>
            </div>
            <div className="flex items-center gap-2">
                <IconoMapaSvg forma="averiaTipo3" tamano={16} />
                <span>Avería T3 (Grave - 2 días taller)</span>
            </div>

            <hr className="border-slate-800 my-1" />

            {/* Otras Incidencias independientes */}
            <div className="flex items-center gap-2">
                <IconoMapaSvg forma="bloqueo" tamano={16} />
                <span>Bloqueo vial (Cierre de tramo)</span>
            </div>
            <div className="flex items-center gap-2">
                <IconoMapaSvg forma="mantenimiento" tamano={16} />
                <span>Mantenimiento preventivo</span>
            </div>
        </div>
    );
}