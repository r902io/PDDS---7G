import React, {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useReducer,
  useRef,
  useState,
} from 'react';
import type { EscenarioSimulacion, SnapshotSimulacion } from '../types/api';
import { ApiError, textoError } from '../services/apiClient';
import { asegurarSesion } from '../services/sessionService';
import { simulacionService, suscribirEventos, type EstadoConexion } from '../services/simulacionService';
import { corridaActiva } from '../utilitarios/escenarios';

// ---------------------------------------------------------------------------
// Control de la corrida
// ---------------------------------------------------------------------------

/**
 * El snapshot no informa qué sesión controla la corrida. El backend asigna
 * el control a la sesión que hizo POST /iniciar con éxito, así que se
 * recuerda localmente qué sesión inició qué idSimulacion. Si el backend
 * luego responde 403 a /detener, se descarta.
 */
interface RegistroControl {
  sesionId: string;
  idSimulacion: number;
}

const CLAVE_CONTROL = 'sisrap.control';

function leerControl(): RegistroControl | null {
  try {
    const t = localStorage.getItem(CLAVE_CONTROL);
    return t ? (JSON.parse(t) as RegistroControl) : null;
  } catch {
    return null;
  }
}

function guardarControl(c: RegistroControl | null): void {
  try {
    if (c) localStorage.setItem(CLAVE_CONTROL, JSON.stringify(c));
    else localStorage.removeItem(CLAVE_CONTROL);
  } catch {
    // Sin almacenamiento: el rol se conserva solo en memoria.
  }
}

// ---------------------------------------------------------------------------
// Estado y reductor
// ---------------------------------------------------------------------------

export type EstadoSesion =
  | { fase: 'CARGANDO' }
  | { fase: 'LISTA'; sesionId: string }
  | { fase: 'ERROR'; error: string };

export type Conexion = EstadoConexion | 'SIN_INICIAR';

interface Estado {
  sesion: EstadoSesion;
  snapshot: SnapshotSimulacion | null;
  /** Hora del navegador (ms) en que llegó el último snapshot. */
  recibidoEn: number | null;
  errorEstado: string | null;
  conexion: Conexion;
  control: RegistroControl | null;
  accionEnCurso: 'INICIAR' | 'DETENER' | null;
  aviso: Aviso | null;
}

export interface Aviso {
  tipo: 'info' | 'error';
  texto: string;
}

type Accion =
  | { tipo: 'SESION_LISTA'; sesionId: string }
  | { tipo: 'SESION_ERROR'; error: string }
  | { tipo: 'SESION_CARGANDO' }
  | { tipo: 'SNAPSHOT'; snapshot: SnapshotSimulacion; origen: 'SSE' | 'HTTP' }
  | { tipo: 'ERROR_ESTADO'; error: string }
  | { tipo: 'CONEXION'; conexion: Conexion }
  | { tipo: 'CONTROL'; control: RegistroControl | null }
  | { tipo: 'ACCION'; accion: Estado['accionEnCurso'] }
  | { tipo: 'AVISO'; aviso: Aviso | null };

const estadoInicial: Estado = {
  sesion: { fase: 'CARGANDO' },
  snapshot: null,
  recibidoEn: null,
  errorEstado: null,
  conexion: 'SIN_INICIAR',
  control: leerControl(),
  accionEnCurso: null,
  aviso: null,
};

/**
 * Un snapshot HTTP (respuesta de /iniciar o /detener, o el GET inicial)
 * puede llegar después que uno SSE más reciente; no debe retroceder el
 * estado. Dentro de una misma corrida el reloj simulado nunca retrocede.
 */
function esMasReciente(nuevo: SnapshotSimulacion, actual: SnapshotSimulacion | null): boolean {
  if (!actual) return true;
  if (nuevo.idSimulacion !== actual.idSimulacion) return true;
  if (!nuevo.relojSimulado || !actual.relojSimulado) return true;
  if (nuevo.relojSimulado > actual.relojSimulado) return true;
  if (nuevo.relojSimulado < actual.relojSimulado) return false;
  // Mismo instante: un estado terminal reemplaza a uno activo, no al revés.
  return !(corridaActiva(nuevo) && !corridaActiva(actual));
}

function reductor(estado: Estado, accion: Accion): Estado {
  switch (accion.tipo) {
    case 'SESION_CARGANDO':
      return { ...estado, sesion: { fase: 'CARGANDO' } };
    case 'SESION_LISTA':
      return { ...estado, sesion: { fase: 'LISTA', sesionId: accion.sesionId } };
    case 'SESION_ERROR':
      return { ...estado, sesion: { fase: 'ERROR', error: accion.error } };
    case 'SNAPSHOT':
      if (accion.origen === 'HTTP' && !esMasReciente(accion.snapshot, estado.snapshot)) {
        return { ...estado, errorEstado: null };
      }
      return { ...estado, snapshot: accion.snapshot, recibidoEn: Date.now(), errorEstado: null };
    case 'ERROR_ESTADO':
      return { ...estado, errorEstado: accion.error };
    case 'CONEXION':
      return { ...estado, conexion: accion.conexion };
    case 'CONTROL':
      return { ...estado, control: accion.control };
    case 'ACCION':
      return { ...estado, accionEnCurso: accion.accion };
    case 'AVISO':
      return { ...estado, aviso: accion.aviso };
  }
}

// ---------------------------------------------------------------------------
// Contexto
// ---------------------------------------------------------------------------

export type Rol = 'CONTROLADOR' | 'OBSERVADOR' | 'SIN_CORRIDA';

/** Sin snapshots durante este lapso con una corrida activa = datos no vigentes. */
const MAXIMO_SILENCIO_MS = 10_000;

export type ResultadoIniciar =
  | { ok: true }
  | { ok: false; corridaEnCurso: boolean; mensaje: string };

interface ValorContexto extends Estado {
  rol: Rol;
  /** El último snapshot es vigente: conexión SSE abierta y sin silencio prolongado. */
  datosVigentes: boolean;
  reintentarSesion: () => void;
  iniciar: (escenario: EscenarioSimulacion, fechaInicio: string, semilla: number | null) => Promise<ResultadoIniciar>;
  detener: () => Promise<void>;
  cerrarAviso: () => void;
}

const Contexto = createContext<ValorContexto | null>(null);

export function SimulacionProvider({ children }: { children: React.ReactNode }) {
  const [estado, despachar] = useReducer(reductor, estadoInicial);
  const [intentoSesion, setIntentoSesion] = useState(0);
  const [ahora, setAhora] = useState(() => Date.now());
  const snapshotActual = useRef<SnapshotSimulacion | null>(null);
  snapshotActual.current = estado.snapshot;

  // Sesión anónima automática.
  useEffect(() => {
    let vigente = true;
    despachar({ tipo: 'SESION_CARGANDO' });
    asegurarSesion()
      .then((s) => vigente && despachar({ tipo: 'SESION_LISTA', sesionId: s.sesionId }))
      .catch((e) => vigente && despachar({ tipo: 'SESION_ERROR', error: textoError(e) }));
    return () => {
      vigente = false;
    };
  }, [intentoSesion]);

  // Estado inicial por HTTP + flujo SSE en tiempo real.
  useEffect(() => {
    let vigente = true;
    simulacionService
      .estado()
      .then((s) => vigente && despachar({ tipo: 'SNAPSHOT', snapshot: s, origen: 'HTTP' }))
      .catch((e) => vigente && despachar({ tipo: 'ERROR_ESTADO', error: textoError(e) }));

    const cerrar = suscribirEventos({
      alRecibir: (s) => despachar({ tipo: 'SNAPSHOT', snapshot: s, origen: 'SSE' }),
      alCambiarConexion: (c) => despachar({ tipo: 'CONEXION', conexion: c }),
      esperaActividad: () => corridaActiva(snapshotActual.current),
    });
    return () => {
      vigente = false;
      cerrar();
    };
  }, []);

  // Reloj de pared para detectar silencio del flujo SSE.
  useEffect(() => {
    const id = setInterval(() => setAhora(Date.now()), 1_000);
    return () => clearInterval(id);
  }, []);

  const sesionId = estado.sesion.fase === 'LISTA' ? estado.sesion.sesionId : null;
  const activa = corridaActiva(estado.snapshot);

  const rol: Rol = !activa
    ? 'SIN_CORRIDA'
    : estado.control != null &&
        sesionId != null &&
        estado.control.sesionId === sesionId &&
        estado.control.idSimulacion === estado.snapshot?.idSimulacion
      ? 'CONTROLADOR'
      : 'OBSERVADOR';

  const silencio = estado.recibidoEn == null ? Infinity : ahora - estado.recibidoEn;
  const datosVigentes =
    estado.snapshot != null &&
    estado.conexion === 'ABIERTA' &&
    (!activa || silencio <= MAXIMO_SILENCIO_MS);

  const fijarControl = useCallback((c: RegistroControl | null) => {
    guardarControl(c);
    despachar({ tipo: 'CONTROL', control: c });
  }, []);

  const iniciar = useCallback<ValorContexto['iniciar']>(
    async (escenario, fechaInicio, semilla) => {
      if (!sesionId) {
        return { ok: false, corridaEnCurso: false, mensaje: 'No hay una sesión válida con el backend.' };
      }
      despachar({ tipo: 'ACCION', accion: 'INICIAR' });
      try {
        const s = await simulacionService.iniciar({ escenario, fechaInicio, semilla });
        if (s.idSimulacion != null) fijarControl({ sesionId, idSimulacion: s.idSimulacion });
        despachar({ tipo: 'SNAPSHOT', snapshot: s, origen: 'HTTP' });
        despachar({ tipo: 'AVISO', aviso: null });
        return { ok: true };
      } catch (e) {
        if (e instanceof ApiError && e.status === 409) {
          // Ya hay una corrida activa: se muestra la vigente.
          simulacionService
            .estado()
            .then((s) => despachar({ tipo: 'SNAPSHOT', snapshot: s, origen: 'HTTP' }))
            .catch(() => undefined);
          const mensaje =
            'Ya hay una corrida en curso. El backend ejecuta una sola corrida a la vez y la controla ' +
            'la sesión que la inició. Se le muestra la corrida vigente.';
          despachar({ tipo: 'AVISO', aviso: { tipo: 'info', texto: mensaje } });
          return { ok: false, corridaEnCurso: true, mensaje };
        }
        if (e instanceof ApiError && e.status === 401) setIntentoSesion((n) => n + 1);
        return { ok: false, corridaEnCurso: false, mensaje: textoError(e) };
      } finally {
        despachar({ tipo: 'ACCION', accion: null });
      }
    },
    [sesionId, fijarControl]
  );

  const detener = useCallback(async () => {
    despachar({ tipo: 'ACCION', accion: 'DETENER' });
    try {
      const s = await simulacionService.detener();
      despachar({ tipo: 'SNAPSHOT', snapshot: s, origen: 'HTTP' });
      fijarControl(null);
    } catch (e) {
      if (e instanceof ApiError && e.status === 403) {
        fijarControl(null);
        despachar({
          tipo: 'AVISO',
          aviso: {
            tipo: 'error',
            texto: 'El backend indica que esta sesión no controla la corrida. Continúa como observador.',
          },
        });
      } else {
        despachar({ tipo: 'AVISO', aviso: { tipo: 'error', texto: `No se pudo detener: ${textoError(e)}` } });
      }
    } finally {
      despachar({ tipo: 'ACCION', accion: null });
    }
  }, [fijarControl]);

  const valor = useMemo<ValorContexto>(
    () => ({
      ...estado,
      rol,
      datosVigentes,
      reintentarSesion: () => setIntentoSesion((n) => n + 1),
      iniciar,
      detener,
      cerrarAviso: () => despachar({ tipo: 'AVISO', aviso: null }),
    }),
    [estado, rol, datosVigentes, iniciar, detener]
  );

  return <Contexto.Provider value={valor}>{children}</Contexto.Provider>;
}

export function useSimulacion(): ValorContexto {
  const v = useContext(Contexto);
  if (!v) throw new Error('useSimulacion debe usarse dentro de SimulacionProvider');
  return v;
}
