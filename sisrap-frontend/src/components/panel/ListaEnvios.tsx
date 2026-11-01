import { memo, useEffect, useState, type ReactNode } from 'react';
import type { AsignacionEnvio, EnvioSimulacion } from '../../types/api';
import { simulacionService } from '../../services/simulacionService';
import { useConsulta } from '../../hooks/useConsulta';
import { Cargando, ErrorCarga } from '../ui/Avisos';
import { formatoCoordenada, formatoEntero } from '../../utilitarios/formato';
import {
  ETIQUETA_ASIGNACION,
  esVigente,
  formatoProductos,
  formatoRuta,
  horaEnContexto,
  rutaReal,
} from '../../utilitarios/envios';
import { colorDeVehiculo } from '../../utilitarios/vehiculos';

interface Props {
  idSimulacion: number | null;
  /** Cambia cuando la corrida cambia de estado, para recargar al terminar. */
  estadoCorrida: string;
  activa: boolean;
  /** Fecha del reloj simulado, para mostrar la fecha solo cuando es otra. */
  fechaReloj: string | null;
  pedidoResaltado: number | null;
  alResaltar: (idPedido: number | null) => void;
}

const HORAS_SIN_VENTANA = 4;
const TAMANIO_PAGINA = 50;
const REFRESCO_MS = 4_000;

/** E31: una línea por asignación, con sus rutas, productos y horarios. */
export const ListaEnvios = memo(function ListaEnvios({
  idSimulacion,
  estadoCorrida,
  activa,
  fechaReloj,
  pedidoResaltado,
  alResaltar,
}: Props) {
  const [textoBusqueda, setTextoBusqueda] = useState('');
  const [busqueda, setBusqueda] = useState('');
  const [pagina, setPagina] = useState(0);

  // La búsqueda se aplica al dejar de escribir.
  useEffect(() => {
    const id = setTimeout(() => {
      setBusqueda(textoBusqueda.trim());
      setPagina(0);
    }, 300);
    return () => clearTimeout(id);
  }, [textoBusqueda]);

  const consulta = useConsulta(
    (s) => simulacionService.envios({ ventana: 'TODOS', horas: HORAS_SIN_VENTANA, buscar: busqueda, pagina, tamanio: TAMANIO_PAGINA }, s),
    `envios-${idSimulacion}-${estadoCorrida}-${busqueda}-${pagina}`,
    activa ? REFRESCO_MS : null
  );

  const datos = consulta.datos;
  const totalPaginas = datos ? Math.max(1, Math.ceil(datos.totalPedidos / TAMANIO_PAGINA)) : 1;

  // Una página puede quedar vacía si la búsqueda deja menos resultados.
  useEffect(() => {
    if (datos && pagina > 0 && pagina >= totalPaginas) setPagina(totalPaginas - 1);
  }, [datos, pagina, totalPaginas]);

  return (
    <div className="space-y-2">
      <input
        type="search"
        value={textoBusqueda}
        onChange={(e) => setTextoBusqueda(e.target.value)}
        placeholder="Buscar pedido, cliente o unidad"
        aria-label="Buscar envíos por pedido, cliente o unidad"
        className="w-full rounded-md border border-borde bg-bg px-2 py-1 text-xs text-texto placeholder:text-texto2 focus:outline-none focus:border-mint"
      />

      {consulta.error && (
        <ErrorCarga titulo="No se pudieron actualizar los envíos" error={consulta.error} reintentar={consulta.recargar} />
      )}

      {!datos ? (
        !consulta.error && <Cargando texto="Cargando envíos…" />
      ) : (
        <>
          <p className="flex justify-between gap-2 text-texto2" role="status">
            <span>
              <span className="font-mono text-texto">{formatoEntero(datos.totalPedidos)}</span>{' '}
              {datos.totalPedidos === 1 ? 'pedido' : 'pedidos'}
            </span>
            <span>
              <span className="font-mono text-texto">{formatoEntero(datos.totalProductos)}</span>{' '}
              {datos.totalProductos === 1 ? 'producto' : 'productos'}
            </span>
          </p>
          {datos.envios.length === 0 ? (
            <p className="text-texto2">No hay envíos que cumplan el filtro.</p>
          ) : (
            <ul className="max-h-96 overflow-y-auto space-y-1.5 pr-0.5">
              {datos.envios.map((e) => (
                <TarjetaEnvio
                  key={e.idPedido}
                  envio={e}
                  fechaReloj={fechaReloj}
                  resaltado={pedidoResaltado === e.idPedido}
                  alResaltar={alResaltar}
                />
              ))}
            </ul>
          )}
          {totalPaginas > 1 && (
            <div className="flex items-center justify-between gap-2 text-[11px] text-texto2">
              <BotonPagina etiqueta="Anterior" deshabilitado={pagina === 0} alPulsar={() => setPagina(pagina - 1)} />
              <span>
                Página <span className="font-mono text-texto">{pagina + 1}</span> de{' '}
                <span className="font-mono text-texto">{totalPaginas}</span>
              </span>
              <BotonPagina etiqueta="Siguiente" deshabilitado={pagina + 1 >= totalPaginas} alPulsar={() => setPagina(pagina + 1)} />
            </div>
          )}
        </>
      )}
    </div>
  );
});

function BotonPagina({ etiqueta, deshabilitado, alPulsar }: { etiqueta: string; deshabilitado: boolean; alPulsar: () => void }) {
  return (
    <button
      type="button"
      onClick={alPulsar}
      disabled={deshabilitado}
      title={deshabilitado ? 'No hay más páginas en este sentido' : undefined}
      className="rounded border border-borde px-1.5 py-0.5 text-texto disabled:text-texto2 disabled:cursor-not-allowed hover:border-texto2"
    >
      {etiqueta}
    </button>
  );
}

function TarjetaEnvio({
  envio,
  fechaReloj,
  resaltado,
  alResaltar,
}: {
  envio: EnvioSimulacion;
  fechaReloj: string | null;
  resaltado: boolean;
  alResaltar: (idPedido: number | null) => void;
}) {
  return (
    <li>
      <button
        type="button"
        aria-pressed={resaltado}
        onClick={() => alResaltar(resaltado ? null : envio.idPedido)}
        title={resaltado ? 'Quitar el resaltado del mapa' : 'Resaltar en el mapa la ruta de cada unidad'}
        className={`block w-full text-left rounded-md border px-2 py-1.5 ${
          resaltado ? 'border-mint bg-panel' : 'border-borde bg-bg hover:border-texto2'
        }`}
      >
        <span className="flex items-baseline gap-1.5">
          <span className="font-mono font-semibold text-texto">#{envio.idPedido}</span>
          <span className="font-mono text-[11px] text-texto2 truncate">{envio.idCliente}</span>
          <span className="ml-auto font-mono text-[11px] text-texto2 whitespace-nowrap">{envio.cantidad} paq</span>
        </span>
        <span className="block text-[11px] text-texto2">
          Destino <span className="font-mono text-texto">{formatoCoordenada(envio.destino.x, envio.destino.y)}</span>,{' '}
          plazo <span className="font-mono text-texto">{horaEnContexto(envio.fechaLimite, fechaReloj)}</span>
        </span>
        {envio.entregaParcial && (
          <Marca clase="text-ambar border-ambar/60">
            Entrega parcial: {envio.cantidadEntregada} de {envio.cantidad} productos
          </Marca>
        )}
        {envio.estado === 'RETRASADO' && <Marca clase="text-rojo border-rojo/60">Fuera de plazo</Marca>}
        {envio.asignaciones.map((a, i) => (
          <LineaAsignacion key={i} a={a} fechaLimite={envio.fechaLimite} fechaReloj={fechaReloj} />
        ))}
      </button>
    </li>
  );
}

function Marca({ clase, children }: { clase: string; children: ReactNode }) {
  return <span className={`inline-block mt-0.5 mr-1 rounded border px-1 text-[10px] font-semibold ${clase}`}>{children}</span>;
}

function LineaAsignacion({
  a,
  fechaLimite,
  fechaReloj,
}: {
  a: AsignacionEnvio;
  fechaLimite: string;
  fechaReloj: string | null;
}) {
  const real = rutaReal(a);
  const vigente = esVigente(a);
  return (
    <span className={`block mt-1 border-t border-borde/60 pt-1 text-[11px] ${vigente ? 'text-texto' : 'text-texto2'}`}>
      <span className="flex items-center gap-1.5">
        <span
          className="font-mono font-semibold"
          style={a.idVehiculo && vigente ? { color: colorDeVehiculo(a.idVehiculo) } : undefined}
        >
          {a.idVehiculo ?? 'Sin unidad'}
        </span>
        <span className="text-texto2">{ETIQUETA_ASIGNACION[a.estado]}</span>
        <span className="ml-auto font-mono whitespace-nowrap">{a.cantidad} paq</span>
      </span>
      <span className="block text-texto2 break-words">
        Productos <span className="font-mono text-texto">{formatoProductos(a.productos)}</span>
      </span>
      {a.estado === 'SIN_ASIGNAR' ? (
        <span className="block text-texto2">
          Sin asignar, plazo <span className="font-mono text-texto">{horaEnContexto(fechaLimite, fechaReloj)}</span>
        </span>
      ) : (
        <Horarios a={a} fechaReloj={fechaReloj} />
      )}
      {real.length > 1 && (
        <span className="block text-texto2 break-words">
          Ruta <span className="font-mono text-texto">{formatoRuta(real)}</span>
        </span>
      )}
      {a.rutaEstimada.length > 1 && (
        <span className="block mt-0.5 border-l-2 border-dashed border-ambar pl-1.5 text-texto2 break-words">
          <span className="font-semibold text-ambar">Ruta estimada</span>{' '}
          <span className="font-mono italic">{formatoRuta(a.rutaEstimada)}</span>
        </span>
      )}
    </span>
  );
}

function Horarios({ a, fechaReloj }: { a: AsignacionEnvio; fechaReloj: string | null }) {
  return (
    <span className="block text-texto2">
      Salida{' '}
      <span className="font-mono text-texto">
        {a.horaSalida ? horaEnContexto(a.horaSalida, fechaReloj) : 'pendiente'}
      </span>
      , llegada{' '}
      {a.horaLlegada ? (
        <>
          <span className={`font-mono ${a.llegadaReal ? 'text-texto' : 'text-ambar italic'}`}>
            {horaEnContexto(a.horaLlegada, fechaReloj)}
          </span>
          {!a.llegadaReal && <span className="text-ambar"> estimada</span>}
        </>
      ) : (
        <span className="text-texto">sin hora</span>
      )}
      {a.horaEntrega && (
        <>
          , entrega <span className="font-mono text-texto">{horaEnContexto(a.horaEntrega, fechaReloj)}</span>
        </>
      )}
    </span>
  );
}
