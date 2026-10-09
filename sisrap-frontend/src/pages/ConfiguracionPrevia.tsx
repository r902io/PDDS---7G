import { useEffect, useState } from 'react';
import type { AlmacenConfigurado, ConfiguracionOperativa, TipoFlotaConfigurado } from '../types/api';
import { configuracionService } from '../services/configuracionService';
import { catalogoService } from '../services/catalogoService';
import { apiFetch, textoError } from '../services/apiClient';
import { useConsulta } from '../hooks/useConsulta';
import { Boton } from '../components/ui/Boton';
import { Cargando, ErrorCarga, Nota } from '../components/ui/Avisos';
import { formatoEntero } from '../utilitarios/formato';

const claseInput = 'w-full min-w-0 rounded-md border border-borde bg-bg text-texto px-2 py-1.5 text-sm font-mono focus:outline-none focus:border-mint disabled:opacity-55';
const nombres: Record<TipoFlotaConfigurado['tipo'], string> = { AUTO: 'Autos', MOTO: 'Motos', BICICLETA: 'Bicicletas' };

type CampoAlmacen = 'nombre' | 'ubicacionX' | 'ubicacionY' | 'capacidadMaxima';
type CampoFlota = 'cantidad' | 'capacidadPaquetes' | 'velocidadKmh' | 'costoPorKm';

function validar(c: ConfiguracionOperativa, ancho: number, alto: number): string | null {
  if (c.almacenes.length !== 3 || c.flota.length !== 3) return 'Se requieren tres almacenes y tres tipos de vehículos.';
  if (c.almacenes.filter(a => a.tipo === 'CENTRAL').length !== 1) return 'Debe existir un almacén central.';
  for (const a of c.almacenes) {
    if (!a.nombre.trim() || a.nombre.length > 50) return `Revisa el nombre de ${a.idAlmacen}.`;
    if (![a.ubicacionX, a.ubicacionY].every(Number.isInteger) || a.ubicacionX < 0 || a.ubicacionX > ancho || a.ubicacionY < 0 || a.ubicacionY > alto)
      return `La posición del almacén ${a.nombre} debe estar dentro del mapa (${ancho} × ${alto}).`;
    if (a.tipo === 'CENTRAL' && a.capacidadMaxima !== null) return 'El almacén central tiene stock ilimitado.';
    if (a.tipo === 'INTERMEDIO' && (!Number.isInteger(a.capacidadMaxima) || (a.capacidadMaxima ?? 0) <= 0)) return `La capacidad de ${a.nombre} debe ser positiva.`;
  }
  for (const f of c.flota) {
    if (!Number.isInteger(f.cantidad) || f.cantidad < 0 || f.cantidad > 200) return `La cantidad de ${nombres[f.tipo]} debe estar entre 0 y 200.`;
    if (!Number.isInteger(f.capacidadPaquetes) || f.capacidadPaquetes <= 0) return `La capacidad de ${nombres[f.tipo]} debe ser un entero positivo.`;
    if (!Number.isFinite(f.velocidadKmh) || f.velocidadKmh <= 0 || f.velocidadKmh > 999.99) return `La velocidad de ${nombres[f.tipo]} debe ser mayor que 0 y hasta 999.99.`;
    if (!Number.isFinite(f.costoPorKm) || f.costoPorKm < 0 || f.costoPorKm > 9999.99) return `El costo por km de ${nombres[f.tipo]} debe estar entre 0 y 9999.99.`;
  }
  if (c.flota.reduce((s, f) => s + f.cantidad, 0) === 0) return 'Debes configurar al menos un vehículo.';
  return null;
}

interface Props { bloqueado: boolean; alDisponibilidad: (listo: boolean) => void; }

/** Edición REAL contra /api/configuracion-operativa (GET y PUT), nunca contra /api/vehiculos. */
export function ConfiguracionPrevia({ bloqueado, alDisponibilidad }: Props) {
  const consulta = useConsulta((s) => configuracionService.consultar(s), 'configuracion-operativa');
  const mapa = useConsulta((s) => catalogoService.mapa(s), 'configuracion-mapa');
  const [borrador, setBorrador] = useState<ConfiguracionOperativa | null>(null);
  const [modificado, setModificado] = useState(false);
  const [guardando, setGuardando] = useState(false);
  const [errorGuardar, setErrorGuardar] = useState<string | null>(null);
  const [confirmacion, setConfirmacion] = useState<string | null>(null);
  const [anchoEditado, setAnchoEditado] = useState<number | null>(null);
  const [altoEditado, setAltoEditado] = useState<number | null>(null);
  const [guardandoMapa, setGuardandoMapa] = useState(false);
  const [errorMapaGuardar, setErrorMapaGuardar] = useState<string | null>(null);
  const ancho = mapa.datos?.anchoKm ?? 70;
  const alto = mapa.datos?.altoKm ?? 50;

  useEffect(() => {
    if (consulta.datos && !modificado && !guardando) setBorrador(consulta.datos);
  }, [consulta.datos]);

  useEffect(() => {
    if (mapa.datos) { setAnchoEditado(mapa.datos.anchoKm); setAltoEditado(mapa.datos.altoKm); }
  }, [mapa.datos]);
  const dimensionesModificadas = anchoEditado !== null && altoEditado !== null && (anchoEditado !== ancho || altoEditado !== alto);
  const dimensionesValidas = Number.isInteger(anchoEditado) && Number.isInteger(altoEditado)
    && (anchoEditado ?? 0) > 0 && (altoEditado ?? 0) > 0
    && (anchoEditado ?? Infinity) <= 150 && (altoEditado ?? Infinity) <= 150;
  const errorValidacion = borrador ? validar(borrador, ancho, alto) : null;
  const listo = !!borrador && !consulta.error && !mapa.error && !modificado && !guardando && !guardandoMapa && !dimensionesModificadas && !errorValidacion;
  useEffect(() => { alDisponibilidad(listo); }, [alDisponibilidad, listo]);

  function cambiarAlmacen(id: string, campo: CampoAlmacen, valor: string) {
    if (bloqueado) return;
    setConfirmacion(null); setErrorGuardar(null); setModificado(true);
    setBorrador(c => c ? ({ ...c, almacenes: c.almacenes.map(a => a.idAlmacen === id ? {
      ...a, [campo]: campo === 'nombre' ? valor : Number(valor),
    } as AlmacenConfigurado : a) }) : c);
  }
  function cambiarFlota(tipo: string, campo: CampoFlota, valor: string) {
    if (bloqueado) return;
    setConfirmacion(null); setErrorGuardar(null); setModificado(true);
    setBorrador(c => c ? ({ ...c, flota: c.flota.map(f => f.tipo === tipo ? { ...f, [campo]: Number(valor) } : f) }) : c);
  }
  async function guardar() {
    if (!borrador || errorValidacion || bloqueado) return;
    setErrorGuardar(null); setGuardando(true);
    try {
      const guardada = await configuracionService.guardar(borrador);
      setBorrador(guardada); setModificado(false); consulta.recargar();
      setConfirmacion('Configuración guardada. Se usará para generar los recursos de la próxima corrida.');
    } catch (e) { setErrorGuardar(textoError(e)); }
    finally { setGuardando(false); }
  }
  function descartar() {
    if (consulta.datos) { setBorrador(consulta.datos); setModificado(false); setErrorGuardar(null); setConfirmacion(null); }
    else consulta.recargar();
  }
  async function guardarMapa() {
    if (bloqueado || guardandoMapa || !dimensionesModificadas || !dimensionesValidas) return;
    setErrorMapaGuardar(null); setGuardandoMapa(true);
    try {
      await apiFetch('/api/mapa/dimensiones', { metodo: 'PUT', cuerpo: { anchoKm: anchoEditado, altoKm: altoEditado } });
      mapa.recargar(); setConfirmacion('Dimensiones del mapa actualizadas. Comprueba las coordenadas de los almacenes.');
    } catch (e) { setErrorMapaGuardar(textoError(e)); }
    finally { setGuardandoMapa(false); }
  }
  const deshabilitado = bloqueado || guardando || guardandoMapa;

  return (
    <section className="rounded-xl border border-borde bg-panel p-4 sm:p-5 space-y-4" aria-label="Configuración previa de almacenes y flota">
      <div className="flex flex-wrap items-start justify-between gap-3 border-b border-borde pb-3">
        <div>
          <h2 className="text-base font-semibold text-texto">2 · Configuración de almacenes y flota</h2>
          <p className="text-xs text-texto2 mt-1">Modifica y guarda los recursos que se crearán al comenzar la próxima simulación.</p>
        </div>
        {borrador && <span className={`rounded px-2 py-1 text-xs ${modificado ? 'text-ambar border border-ambar' : 'text-mint border border-mint/50'}`}>
          {modificado ? 'Cambios pendientes de guardar' : 'Configuración guardada'}
        </span>}
      </div>
      {consulta.error && <ErrorCarga titulo="No se pudo obtener la configuración" error={consulta.error} reintentar={consulta.recargar} />}
      {mapa.error && <ErrorCarga titulo="No se pudo consultar el mapa" error={mapa.error} reintentar={mapa.recargar} />}
      {!borrador && !consulta.error && <Cargando texto="Cargando configuración desde el backend…" />}
      {mapa.datos && <div className="rounded-lg border border-borde bg-panel2 p-3 space-y-2">
        <h3 className="text-sm font-semibold text-texto">Dimensiones de la ciudad</h3>
        <div className="flex flex-wrap gap-3 items-end">
          <fieldset disabled={deshabilitado} className="w-28"><Entrada etiqueta="Ancho (km)" tipo="number" valor={anchoEditado ?? ancho} min={1} max={150} paso={1} alCambiar={v => setAnchoEditado(Number(v))} /></fieldset>
          <fieldset disabled={deshabilitado} className="w-28"><Entrada etiqueta="Alto (km)" tipo="number" valor={altoEditado ?? alto} min={1} max={150} paso={1} alCambiar={v => setAltoEditado(Number(v))} /></fieldset>
          <Boton variant="secundario" disabled={deshabilitado || !dimensionesModificadas || !dimensionesValidas}
            onClick={guardarMapa}>{guardandoMapa ? 'Guardando…' : 'Guardar dimensiones'}</Boton>
          {dimensionesModificadas && <span className="text-xs text-ambar">Cambios pendientes</span>}
        </div>
        <p className="text-xs text-texto2">La red seguirá siendo ortogonal y bidireccional. Las dimensiones del mapa se guardan independientemente de la configuración de la flota.</p>
        {errorMapaGuardar && <ErrorCarga titulo="No se pudo actualizar el mapa" error={errorMapaGuardar} />}
      </div>}
      {borrador && <>
        <fieldset disabled={deshabilitado} className="space-y-4 disabled:opacity-70">
          <div>
            <h3 className="text-sm font-semibold text-texto mb-2">Almacenes</h3>
            <p className="text-xs text-texto2 mb-3">Uno central con inventario ilimitado y dos intermedios. Ciudad: {ancho} × {alto} km.</p>
            <div className="grid grid-cols-1 md:grid-cols-3 gap-3">
              {borrador.almacenes.map(a => <div key={a.idAlmacen} className="rounded-lg border border-borde bg-panel2 p-3 space-y-3">
                <div className="flex items-center justify-between gap-2">
                  <strong className="text-sm text-texto truncate">{a.nombre}</strong>
                  <span className="text-[11px] text-texto2">{a.tipo === 'CENTRAL' ? 'Central' : 'Intermedio'}</span>
                </div>
                <Entrada etiqueta="Nombre" valor={a.nombre} alCambiar={v => cambiarAlmacen(a.idAlmacen, 'nombre', v)} />
                <div className="grid grid-cols-2 gap-2">
                  <Entrada etiqueta="X (km)" tipo="number" valor={a.ubicacionX} min={0} max={ancho} paso={1} alCambiar={v => cambiarAlmacen(a.idAlmacen, 'ubicacionX', v)} />
                  <Entrada etiqueta="Y (km)" tipo="number" valor={a.ubicacionY} min={0} max={alto} paso={1} alCambiar={v => cambiarAlmacen(a.idAlmacen, 'ubicacionY', v)} />
                </div>
                {a.tipo === 'CENTRAL' ? <p className="text-xs text-mint">Stock ilimitado</p> :
                  <Entrada etiqueta="Capacidad (paquetes)" tipo="number" valor={a.capacidadMaxima ?? 0} min={1} paso={1} alCambiar={v => cambiarAlmacen(a.idAlmacen, 'capacidadMaxima', v)} />}
              </div>)}
            </div>
          </div>
          <div>
            <h3 className="text-sm font-semibold text-texto mb-2">Flota inicial</h3>
            <div className="grid grid-cols-1 md:grid-cols-3 gap-3">
              {borrador.flota.map(f => <div key={f.tipo} className="rounded-lg border border-borde bg-panel2 p-3 space-y-3">
                <strong className="text-sm text-texto">{nombres[f.tipo]}</strong>
                <div className="grid grid-cols-2 gap-2">
                  <Entrada etiqueta="Unidades" tipo="number" valor={f.cantidad} min={0} max={200} paso={1} alCambiar={v => cambiarFlota(f.tipo, 'cantidad', v)} />
                  <Entrada etiqueta="Capacidad (paq)" tipo="number" valor={f.capacidadPaquetes} min={1} paso={1} alCambiar={v => cambiarFlota(f.tipo, 'capacidadPaquetes', v)} />
                  <Entrada etiqueta="Velocidad (km/h)" tipo="number" valor={f.velocidadKmh} min={0.01} max={999.99} paso={0.01} alCambiar={v => cambiarFlota(f.tipo, 'velocidadKmh', v)} />
                  <Entrada etiqueta="Costo (S/ por km)" tipo="number" valor={f.costoPorKm} min={0} max={9999.99} paso={0.01} alCambiar={v => cambiarFlota(f.tipo, 'costoPorKm', v)} />
                </div>
              </div>)}
            </div>
          </div>
        </fieldset>
        <div className="border-t border-borde pt-3 flex flex-wrap items-center gap-3 justify-between">
          <div className="text-xs text-texto2">Total: <span className="font-mono text-texto">{formatoEntero(borrador.flota.reduce((n, f) => n + f.cantidad, 0))}</span> vehículos</div>
          <div className="flex flex-wrap items-center gap-2">
            <Boton variant="secundario" onClick={descartar} disabled={deshabilitado || !modificado}>Descartar cambios</Boton>
            <Boton onClick={guardar} disabled={deshabilitado || !modificado || !!errorValidacion || !!consulta.error || !!mapa.error}>
              {guardando ? 'Guardando…' : 'Guardar configuración'}
            </Boton>
          </div>
        </div>
        {errorValidacion && <p role="alert" className="text-xs text-rojo">{errorValidacion}</p>}
        {errorGuardar && <ErrorCarga titulo="No se pudo guardar" error={errorGuardar} />}
        {confirmacion && <p role="status" className="text-xs text-mint">{confirmacion}</p>}
        <Nota>{bloqueado ? 'La configuración queda bloqueada mientras hay una corrida activa.' :
          'Al guardar se modifica únicamente la plantilla. Los vehículos operativos se recrean al iniciar la corrida; no cambia la flota de una operación en curso.'}
        </Nota>
      </>}
    </section>
  );
}

function Entrada({ etiqueta, valor, alCambiar, tipo = 'text', min, max, paso }: {
  etiqueta: string; valor: string | number; alCambiar: (v: string) => void;
  tipo?: 'text' | 'number'; min?: number; max?: number; paso?: number;
}) {
  return <label className="min-w-0 block space-y-1">
    <span className="block text-xs text-texto2">{etiqueta}</span>
    <input className={claseInput} type={tipo} min={min} max={max} step={paso} value={valor}
      onChange={e => alCambiar(e.target.value)} />
  </label>;
}
