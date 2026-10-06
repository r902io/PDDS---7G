import type { IniciarSimulacionRequest, SnapshotSimulacion } from '../types/api';
import { apiFetch } from './apiClient';

/**
 * Operaciones de simulación que la interfaz puede usar.
 *
 * El backend también expone /api/simulacion/pausar y /reanudar, pero el
 * curso prohíbe cualquier control de reproducción: aquí deliberadamente
 * NO existen funciones para llamarlos.
 */
export const simulacionService = {
  estado(): Promise<SnapshotSimulacion> {
    return apiFetch<SnapshotSimulacion>('/api/simulacion/estado', { autenticar: false });
  },

  iniciar(solicitud: IniciarSimulacionRequest): Promise<SnapshotSimulacion> {
    return apiFetch<SnapshotSimulacion>('/api/simulacion/iniciar', {
      metodo: 'POST',
      cuerpo: solicitud,
    });
  },

  detener(): Promise<SnapshotSimulacion> {
    return apiFetch<SnapshotSimulacion>('/api/simulacion/detener', { metodo: 'POST' });
  },
};

export type EstadoConexion = 'CONECTANDO' | 'ABIERTA' | 'RECONECTANDO';

export interface ManejadoresEventos {
  alRecibir: (snapshot: SnapshotSimulacion) => void;
  alCambiarConexion: (estado: EstadoConexion) => void;
  /** Hay una corrida activa: el backend publica un snapshot cada ~0,5 s. */
  esperaActividad: () => boolean;
}

const ESPERA_MIN_MS = 1_000;
const ESPERA_MAX_MS = 15_000;
/**
 * Vigilante de silencio. Un intermediario (nginx, proxy) puede dejar la
 * conexión abierta pero muerta, sin disparar `error`. Con una corrida
 * activa el backend emite varias veces por segundo; sin corrida no emite
 * nada, así que la conexión se renueva periódicamente para recibir el
 * snapshot vigente (el backend lo envía al conectar) y detectar una
 * corrida iniciada desde otro dispositivo.
 */
const SILENCIO_ACTIVO_MS = 6_000;
const SILENCIO_INACTIVO_MS = 20_000;

/**
 * Suscripción a GET /api/simulacion/eventos (SSE, evento "snapshot").
 *
 * El backend envía el snapshot vigente apenas se conecta un cliente, así
 * que un dispositivo que entra a mitad de corrida ve el estado actual de
 * inmediato. Si la conexión se cae, EventSource reintenta por su cuenta;
 * si el navegador la da por cerrada (p. ej. el backend respondió con
 * error), se vuelve a abrir con espera exponencial.
 *
 * Devuelve la función para cerrar la suscripción.
 */
export function suscribirEventos(manejadores: ManejadoresEventos): () => void {
  let fuente: EventSource | null = null;
  let temporizador: ReturnType<typeof setTimeout> | null = null;
  let espera = ESPERA_MIN_MS;
  let cerrado = false;
  let ultimoEvento = Date.now();

  const abrir = () => {
    if (cerrado) return;
    // Al renovar una conexión sana no se anuncia corte: si de verdad cae,
    // `onerror` o el vigilante de silencio lo informan.
    if (!fuente) manejadores.alCambiarConexion('CONECTANDO');
    fuente?.close();
    ultimoEvento = Date.now();
    fuente = new EventSource('/api/simulacion/eventos');

    fuente.onopen = () => {
      espera = ESPERA_MIN_MS;
      manejadores.alCambiarConexion('ABIERTA');
    };

    fuente.addEventListener('snapshot', (ev) => {
      ultimoEvento = Date.now();
      try {
        manejadores.alRecibir(JSON.parse((ev as MessageEvent<string>).data) as SnapshotSimulacion);
      } catch {
        // Un mensaje ilegible no reemplaza el último estado válido.
      }
    });

    fuente.onerror = () => {
      if (cerrado || !fuente) return;
      manejadores.alCambiarConexion('RECONECTANDO');
      if (fuente.readyState === EventSource.CLOSED) {
        fuente.close();
        temporizador = setTimeout(() => {
          temporizador = null;
          abrir();
        }, espera);
        espera = Math.min(espera * 2, ESPERA_MAX_MS);
      }
    };
  };

  abrir();

  const vigilante = setInterval(() => {
    if (cerrado || temporizador != null) return;
    const limite = manejadores.esperaActividad() ? SILENCIO_ACTIVO_MS : SILENCIO_INACTIVO_MS;
    if (Date.now() - ultimoEvento > limite) abrir();
  }, 2_000);

  return () => {
    cerrado = true;
    clearInterval(vigilante);
    if (temporizador) clearTimeout(temporizador);
    fuente?.close();
  };
}
