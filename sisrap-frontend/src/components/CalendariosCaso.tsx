import { useState, type FormEvent } from 'react';
import { useConsulta } from '../hooks/useConsulta';
import { apiFetch, sesionGuardada, textoError } from '../services/apiClient';
import type { EscenarioSimulacion } from '../types/api';
import { Boton } from './ui/Boton';
import { ErrorCarga } from './ui/Avisos';
import { Modal } from './ui/Modal';

type TipoCalendario = 'BLOQUEO' | 'MANTENIMIENTO';
type ArchivoCalendario = {
  nombre: string;
  tipo: TipoCalendario;
  registros: number;
  desde: string;
  hasta: string;
  bytes: number;
};
type Prevision = {
  bloqueos: { inicio: string; fin: string; tramos: number }[];
  mantenimientos: { idVehiculo: string; inicio: string; fin: string }[];
};

function finalPeriodo(fecha: string, escenario: EscenarioSimulacion): string {
  const date = new Date(`${fecha}T12:00:00Z`);
  if (Number.isNaN(date.getTime())) return fecha;
  date.setUTCDate(date.getUTCDate() + (escenario === 'OPERACION_DIARIA' ? 0 : escenario === 'SIMULACION_CINCO_DIAS' ? 4 : 29));
  return date.toISOString().slice(0, 10);
}

function fechaCorta(iso: string): string {
  const fecha = iso.slice(0, 10);
  return /^\d{4}-\d\d-\d\d$/.test(fecha) ? `${fecha.slice(8, 10)}/${fecha.slice(5, 7)}/${fecha.slice(0, 4)}` : fecha;
}

function fechaHora(iso: string): string {
  return `${fechaCorta(iso)} ${iso.slice(11, 16)}`;
}

export function CalendariosCaso({ bloqueado, fechaInicio, escenario }: {
  bloqueado: boolean;
  fechaInicio: string;
  escenario: EscenarioSimulacion;
}) {
  const [abierto, setAbierto] = useState<TipoCalendario | null>(null);
  const [archivos, setArchivos] = useState<File[]>([]);
  const [procesando, setProcesando] = useState(false);
  const [eliminando, setEliminando] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const fin = finalPeriodo(fechaInicio, escenario);
  const periodoValido = /^\d{4}-\d{2}-\d{2}$/.test(fechaInicio) && /^\d{4}-\d{2}-\d{2}$/.test(fin);
  const consulta = useConsulta(s => apiFetch<ArchivoCalendario[]>('/api/datos-caso/calendarios', { autenticar: false, senal: s }), 'calendarios-archivos');
  const prevista = useConsulta(s => periodoValido ? apiFetch<Prevision>(
    `/api/datos-caso/calendarios/programados?desde=${fechaInicio}&hasta=${fin}`, { autenticar: false, senal: s })
    : Promise.resolve({ bloqueos: [], mantenimientos: [] }), `prevision-${fechaInicio}-${fin}`);
  const deshabilitado = bloqueado || procesando || eliminando !== null;
  const grupos = [
    { tipo: 'BLOQUEO' as const, titulo: 'Bloqueos programados', boton: 'Cargar bloqueos', ejemplo: 'bloqueo.2610.txt' },
    { tipo: 'MANTENIMIENTO' as const, titulo: 'Mantenimientos preventivos', boton: 'Cargar mantenimientos', ejemplo: 'mant.preventivo.09.10.txt' },
  ];

  function volverACargar() {
    consulta.recargar();
    prevista.recargar();
  }

  async function subir(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    if (!abierto || deshabilitado) return;
    if (archivos.length === 0 || archivos.length > 60) {
      setError('Selecciona entre 1 y 60 archivos.'); return;
    }
    const nombreValido = (f: File) => abierto === 'BLOQUEO'
      ? /^bloqueo\.\d{2}(0[1-9]|1[0-2])\.txt$/.test(f.name)
      : /^mant.*\.txt$/.test(f.name);
    if (archivos.some(f => !nombreValido(f) || !f.size || f.size > 10 * 1024 * 1024)) {
      setError(`Comprueba los nombres y tamaños de los archivos (máximo 10 MB; ejemplo: ${grupos.find(x => x.tipo === abierto)?.ejemplo}).`); return;
    }
    const token = sesionGuardada()?.token;
    if (!token) { setError('La sesión no está disponible. Recarga la página.'); return; }
    setProcesando(true); setError(null);
    try {
      const cuerpo = new FormData();
      archivos.forEach(f => cuerpo.append('archivos', f));
      const respuesta = await fetch('/api/datos-caso/calendarios', {
        method: 'POST',
        headers: { Accept: 'application/json', Authorization: `Bearer ${token}` },
        body: cuerpo,
        cache: 'no-store',
      });
      if (!respuesta.ok) {
        const detalle = await respuesta.json().catch(() => null) as { message?: string; detail?: string; error?: string } | null;
        throw new Error(detalle?.message || detalle?.detail || detalle?.error || `No se pudieron cargar los archivos (HTTP ${respuesta.status}).`);
      }
      setAbierto(null); setArchivos([]);
      volverACargar();
    } catch (e) { setError(textoError(e)); }
    finally { setProcesando(false); }
  }

  async function eliminar(nombre: string) {
    if (deshabilitado || !window.confirm(`¿Eliminar ${nombre} del calendario de futuras corridas?`)) return;
    setEliminando(nombre); setError(null);
    try {
      await apiFetch<void>(`/api/datos-caso/calendarios/${encodeURIComponent(nombre)}`, { metodo: 'DELETE' });
      volverACargar();
    } catch (e) { setError(textoError(e)); }
    finally { setEliminando(null); }
  }

  return (
    <section className="space-y-3 border-t border-borde pt-3">
      <div>
        <h4 className="text-sm font-semibold text-texto">Incidencias programadas</h4>
        <p className="text-xs text-texto2 mt-1">Se utilizan las fechas originales de los archivos; no se generan bloqueos ni mantenimientos aleatorios.</p>
      </div>
      {consulta.error && <ErrorCarga titulo="No se pudo consultar el calendario" error={consulta.error} reintentar={consulta.recargar} />}
      {grupos.map(grupo => {
        const listados = (consulta.datos ?? []).filter(f => f.tipo === grupo.tipo);
        return <div className="rounded-lg border border-borde bg-panel2 p-3 space-y-2" key={grupo.tipo}>
          <div className="flex flex-wrap items-center justify-between gap-2">
            <h5 className="text-xs font-semibold text-texto">{grupo.titulo}</h5>
            <Boton type="button" variant="secundario" className="px-2 py-1 text-xs"
              disabled={deshabilitado} disabledReason="Solo puede modificarse antes de iniciar una corrida."
              onClick={() => { setAbierto(grupo.tipo); setArchivos([]); setError(null); }}>
              {grupo.boton}
            </Boton>
          </div>
          {!consulta.datos && !consulta.error && <p className="text-xs text-texto2">Consultando archivos…</p>}
          {consulta.datos && !listados.length && <p className="text-xs text-texto2">No hay archivos cargados.</p>}
          {listados.length > 0 && <ul className="space-y-1 max-h-32 overflow-y-auto">
            {listados.map(f => <li key={f.nombre} className="flex flex-wrap items-center justify-between gap-2 border border-borde rounded px-2 py-1.5">
              <div className="min-w-0 text-xs">
                <span className="font-mono text-texto break-all">{f.nombre}</span>
                <span className="text-texto2 ml-2">{f.registros.toLocaleString('es-PE')} registros · {fechaCorta(f.desde)}–{fechaCorta(f.hasta)}</span>
                {periodoValido && f.desde <= fin && f.hasta >= fechaInicio && <span className="ml-2 text-mint">Coincide con el período</span>}
              </div>
              <button type="button" disabled={deshabilitado} onClick={() => void eliminar(f.nombre)}
                className="text-xs text-rojo hover:underline disabled:opacity-50"
                aria-label={`Eliminar ${f.nombre}`}>{eliminando === f.nombre ? 'Eliminando…' : 'Eliminar'}</button>
            </li>)}
          </ul>}
        </div>;
      })}
      {periodoValido && <div className="rounded border border-borde p-3 space-y-2">
        <p className="text-xs font-semibold text-texto">Programación para {fechaCorta(fechaInicio)}–{fechaCorta(fin)}
          {escenario === 'COLAPSO_LOGISTICO' ? ' (vista previa de 30 días)' : ''}</p>
        {prevista.error && <ErrorCarga titulo="No se pudo consultar la programación" error={prevista.error} reintentar={prevista.recargar} />}
        {prevista.cargando && !prevista.datos && <p className="text-xs text-texto2">Consultando incidencias…</p>}
        {prevista.datos && !prevista.error && <>
          <p className="text-xs text-texto2">{prevista.datos.bloqueos.length} bloqueos y {prevista.datos.mantenimientos.length} mantenimientos previstos para este período.</p>
          <details className="text-xs text-texto2">
            <summary className="cursor-pointer text-texto">Ver mantenimientos ({prevista.datos.mantenimientos.length})</summary>
            <ul className="mt-1 max-h-40 overflow-y-auto space-y-1">
              {prevista.datos.mantenimientos.map((m, i) => <li key={`${i}-${m.idVehiculo}`}>{m.idVehiculo} · {fechaHora(m.inicio)} hasta {fechaHora(m.fin)}</li>)}
            </ul>
          </details>
          <details className="text-xs text-texto2">
            <summary className="cursor-pointer text-texto">Ver bloqueos ({prevista.datos.bloqueos.length})</summary>
            <ul className="mt-1 max-h-40 overflow-y-auto space-y-1">
              {prevista.datos.bloqueos.map((b, i) => <li key={i}>{fechaHora(b.inicio)} hasta {fechaHora(b.fin)} · {b.tramos} tramos</li>)}
            </ul>
          </details>
        </>}
      </div>}
      {error && !abierto && <ErrorCarga titulo="No se pudo modificar el calendario" error={error} />}
      <p className="text-xs text-texto2">Cada mantenimiento indica el vehículo y la fecha del día completo de indisponibilidad (00:00–23:59). Los bloqueos conservan sus horarios exactos.</p>
      <Modal isOpen={abierto !== null} onClose={() => { if (!procesando) { setAbierto(null); setError(null); } }}
        titulo={abierto === 'BLOQUEO' ? 'Cargar bloqueos programados' : 'Cargar mantenimientos preventivos'}
        subtitulo="Los registros se aplicarán únicamente cuando coincidan sus fechas con la simulación.">
        <form className="space-y-4" onSubmit={subir}>
          <label className="block text-sm text-texto">Selecciona los archivos (.txt)
            <input type="file" accept=".txt" multiple disabled={procesando}
              onChange={e => { setArchivos(Array.from(e.target.files ?? [])); setError(null); }}
              className="block w-full mt-2 text-xs text-texto2 file:rounded file:border file:border-borde file:bg-panel file:text-texto file:px-3 file:py-2" />
          </label>
          <p className="text-xs text-texto2">Ejemplo: {grupos.find(g => g.tipo === abierto)?.ejemplo}. Máximo 10 MB por archivo. Si deseas reemplazar un archivo, elimina el anterior.</p>
          {archivos.length > 0 && <p className="text-xs">{archivos.length} archivo(s) seleccionado(s)</p>}
          {error && <ErrorCarga titulo="No se pudieron cargar los archivos" error={error} />}
          <div className="flex justify-end gap-2">
            <Boton type="button" variant="secundario" disabled={procesando} onClick={() => setAbierto(null)}>Cancelar</Boton>
            <Boton type="submit" disabled={procesando || !archivos.length}>{procesando ? 'Cargando…' : 'Guardar archivos'}</Boton>
          </div>
        </form>
      </Modal>
    </section>
  );
}
