import { useEffect, useState } from 'react';
import type { SnapshotSimulacion } from '../types/api';
import { corridaActiva } from '../utilitarios/escenarios';
import { useConsulta } from '../hooks/useConsulta';
import { simulacionService } from '../services/simulacionService';

type Registro = { inicio: number; fin: number | null };
const memoria = new Map<number, Registro>();
const clave = (id: number) => `sisrap.tiempo-real.${id}`;
function leer(id: number): Registro | null {
  try {
    const texto = localStorage.getItem(clave(id));
    const dato = texto ? JSON.parse(texto) as Registro : null;
    if (dato && Number.isFinite(dato.inicio) && (dato.fin === null || Number.isFinite(dato.fin))) return dato;
  } catch { /* El reloj también funciona si el navegador restringe el almacenamiento. */ }
  return memoria.get(id) ?? null;
}
function guardar(id: number, dato: Registro) {
  memoria.set(id, dato);
  try { localStorage.setItem(clave(id), JSON.stringify(dato)); } catch { /* Se conserva en memoria. */ }
}
export function registrarInicioReal(id: number, inicio: number) {
  guardar(id, { inicio, fin: null });
}
export function registrarFinReal(snapshot: SnapshotSimulacion) {
  if (snapshot.idSimulacion == null || corridaActiva(snapshot)) return;
  const dato = leer(snapshot.idSimulacion);
  if (dato && dato.fin === null) guardar(snapshot.idSimulacion, { ...dato, fin: Date.now() });
}
function duracion(ms: number) {
  const segundos = Math.max(0, Math.floor(ms / 1000));
  return [Math.floor(segundos / 3600), Math.floor(segundos / 60) % 60, segundos % 60]
    .map(n => String(n).padStart(2, '0')).join(':');
}
export function TiemposOperacion({ snapshot }: { snapshot: SnapshotSimulacion }) {
  const activa = corridaActiva(snapshot);
  const [ahora, setAhora] = useState(Date.now);
  useEffect(() => {
    setAhora(Date.now());
    const timer = window.setInterval(() => setAhora(Date.now()), 1000);
    return () => window.clearInterval(timer);
  }, []);
  const id = snapshot.idSimulacion;
  const resultado = useConsulta(s => id != null && !activa
    ? simulacionService.resultado(id, s) : Promise.resolve(null), `tiempo-resultado-${id}-${activa}`);
  if (id == null) return null;
  const registro = leer(id);
  const resultadoActual = resultado.datos?.idSimulacion === id ? resultado.datos : null;
  const realMs = !activa && resultadoActual ? resultadoActual.tiempoRealMs
    : registro ? (registro.fin ?? ahora) - registro.inicio : null;
  return (
    <section aria-label="Tiempos de la operación" className="flex flex-wrap justify-center items-center gap-x-5 gap-y-2 text-sm text-center">
      <div title="Hora actual de esta PC, según su zona horaria local">
        <span className="block text-texto2">Hora real</span>
        <time dateTime={new Date(ahora).toISOString()} className="tabular-nums text-base text-texto">
          {new Date(ahora).toLocaleTimeString('es-PE', { hour: '2-digit', minute: '2-digit', second: '2-digit', hourCycle: 'h23' })}
        </time>
      </div>
      <div title="Tiempo de reloj desde la solicitud de inicio; al finalizar se usa la duración registrada por el servidor">
        <span className="block text-texto2">Duración real</span>
        <span className="tabular-nums text-base font-medium text-texto">{realMs != null ? duracion(realMs) : '—'}</span>
      </div>
    </section>
  );
}
