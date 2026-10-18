import { useState, type FormEvent } from 'react';
import type { CargaHistorica } from '../types/api';
import type { Consulta } from '../hooks/useConsulta';
import { apiFetch, sesionGuardada, textoError } from '../services/apiClient';
import { Boton } from './ui/Boton';
import { Modal } from './ui/Modal';
import { ErrorCarga } from './ui/Avisos';

type ResultadoImportacion = {
  archivosProcesados: number;
  pedidosInsertados: number;
};

export function HistorialPedidos({
  consulta,
  bloqueado,
}: {
  consulta: Consulta<CargaHistorica[]>;
  bloqueado: boolean;
}) {
  const [modalAbierto, setModalAbierto] = useState(false);
  const [archivos, setArchivos] = useState<File[]>([]);
  const [procesando, setProcesando] = useState(false);
  const [eliminando, setEliminando] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [resultado, setResultado] = useState<ResultadoImportacion | null>(null);
  const cargas = (consulta.datos ?? []).slice().sort((a, b) => b.anio - a.anio || b.mes - a.mes);
  const deshabilitado = bloqueado || procesando || eliminando !== null;

  function validarSeleccion(): string | null {
    if (archivos.length === 0) return 'Selecciona al menos un archivo.';
    if (archivos.length > 60) return 'Se pueden importar hasta 60 archivos por vez.';
    if (archivos.some(a => !/^ventas[._-]?\d{4}(0[1-9]|1[0-2])\.(txt|csv)$/i.test(a.name))) {
      return 'Los archivos deben tener nombres como ventas202601.txt o ventas202601.csv.';
    }
    if (archivos.some(a => a.size === 0 || a.size > 10 * 1024 * 1024)) {
      return 'Cada archivo debe tener contenido y no superar 10 MB.';
    }
    if (archivos.reduce((total, a) => total + a.size, 0) > 50 * 1024 * 1024) {
      return 'La carga completa no puede superar 50 MB.';
    }
    const periodos = archivos.map(a => a.name.match(/\d{6}(?=\.(txt|csv)$)/i)?.[0]);
    if (new Set(periodos).size !== periodos.length) return 'Seleccionaste dos archivos del mismo mes.';
    return null;
  }

  async function importar(e: FormEvent) {
    e.preventDefault();
    if (deshabilitado) return;
    const problema = validarSeleccion();
    if (problema) {
      setError(problema);
      return;
    }
    const token = sesionGuardada()?.token;
    if (!token) {
      setError('No hay una sesión válida. Actualiza la página e inténtalo nuevamente.');
      return;
    }
    setProcesando(true);
    setError(null);
    setResultado(null);
    try {
      const cuerpo = new FormData();
      archivos.forEach(a => cuerpo.append('archivos', a));
      const respuesta = await fetch('/api/pedidos/cargas-historicas', {
        method: 'POST',
        headers: { Accept: 'application/json', Authorization: `Bearer ${token}` },
        body: cuerpo,
        cache: 'no-store',
      });
      if (!respuesta.ok) {
        const datos = await respuesta.json().catch(() => null) as { message?: string; error?: string; detail?: string } | null;
        throw new Error(datos?.message || datos?.detail || datos?.error || `Error HTTP ${respuesta.status} al importar los históricos.`);
      }
      const importados = await respuesta.json() as ResultadoImportacion;
      setResultado(importados);
      setArchivos([]);
      setModalAbierto(false);
      consulta.recargar();
    } catch (e) {
      setError(textoError(e));
    } finally {
      setProcesando(false);
    }
  }

  async function eliminar(carga: CargaHistorica) {
    if (deshabilitado) return;
    const periodo = `${String(carga.mes).padStart(2, '0')}/${carga.anio}`;
    if (!window.confirm(`¿Eliminar los ${carga.filas.toLocaleString('es-PE')} pedidos históricos de ${periodo}?`)) return;
    setEliminando(carga.huella);
    setError(null);
    setResultado(null);
    try {
      await apiFetch<void>(`/api/pedidos/cargas-historicas/${encodeURIComponent(carga.huella)}`, { metodo: 'DELETE' });
      consulta.recargar();
    } catch (e) {
      setError(textoError(e));
    } finally {
      setEliminando(null);
    }
  }

  return (
    <section className="space-y-2 border-t border-borde pt-3">
      <div className="flex items-center justify-between gap-2">
        <h4 className="text-xs font-semibold text-texto">Pedidos históricos</h4>
        <Boton
          type="button"
          variant="secundario"
          className="px-3 py-1 text-xs"
          disabled={deshabilitado}
          disabledReason="Los históricos solo se pueden modificar fuera de una simulación."
          onClick={() => { setError(null); setModalAbierto(true); }}
        >
          Cargar históricos
        </Boton>
      </div>
      {consulta.error && <ErrorCarga titulo="No se pudieron consultar los históricos" error={consulta.error} reintentar={consulta.recargar} />}
      {consulta.cargando && !consulta.datos && <p className="text-xs text-texto2">Consultando archivos cargados…</p>}
      {consulta.datos && cargas.length === 0 && <p className="text-xs text-texto2">No hay pedidos históricos cargados.</p>}
      {cargas.length > 0 && (
        <ul className="space-y-1 max-h-36 overflow-y-auto">
          {cargas.map(carga => (
            <li key={carga.huella} className="flex items-center justify-between gap-2 rounded border border-borde bg-panel px-2 py-1.5">
              <span className="text-xs text-texto">
                {String(carga.mes).padStart(2, '0')}/{carga.anio}
                <span className="text-texto2 ml-2">{carga.filas.toLocaleString('es-PE')} pedidos</span>
              </span>
              <button
                type="button"
                className="text-xs text-rojo hover:underline disabled:opacity-50"
                disabled={deshabilitado}
                onClick={() => void eliminar(carga)}
                aria-label={`Eliminar históricos de ${carga.mes}/${carga.anio}`}
              >
                {eliminando === carga.huella ? 'Eliminando…' : 'Eliminar'}
              </button>
            </li>
          ))}
        </ul>
      )}
      {resultado && <p className="text-xs text-mint">{resultado.pedidosInsertados.toLocaleString('es-PE')} pedidos históricos importados.</p>}
      {error && !modalAbierto && <ErrorCarga titulo="No se pudo actualizar el histórico" error={error} />}
      <Modal
        isOpen={modalAbierto}
        onClose={() => { if (!procesando) { setModalAbierto(false); setError(null); } }}
        titulo="Cargar pedidos históricos"
        subtitulo="Los archivos permiten estimar la demanda de las próximas simulaciones."
      >
        <form onSubmit={importar} className="space-y-4">
          <label className="block text-sm text-texto">
            Archivos mensuales (.txt o .csv)
            <input
              type="file"
              accept=".txt,.csv"
              multiple
              disabled={procesando}
              onChange={e => { setArchivos(Array.from(e.target.files ?? [])); setError(null); }}
              className="block mt-2 w-full text-xs text-texto2 file:rounded file:border file:border-borde file:bg-panel file:text-texto file:px-3 file:py-2"
            />
          </label>
          <p className="text-xs text-texto2">Ejemplo: ventas202601.txt. Máximo 10 MB por archivo.</p>
          {archivos.length > 0 && <p className="text-xs text-texto">{archivos.length} archivo{archivos.length === 1 ? '' : 's'} seleccionado{archivos.length === 1 ? '' : 's'}.</p>}
          {error && <ErrorCarga titulo="No se pudieron cargar los archivos" error={error} />}
          <div className="flex justify-end gap-2">
            <Boton type="button" variant="secundario" disabled={procesando} disabledReason="La carga está en curso." onClick={() => setModalAbierto(false)}>Cancelar</Boton>
            <Boton type="submit" disabled={procesando || !archivos.length} disabledReason="Selecciona archivos para importar.">
              {procesando ? 'Importando…' : 'Importar'}
            </Boton>
          </div>
        </form>
      </Modal>
    </section>
  );
}
