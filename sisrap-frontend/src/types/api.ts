/**
 * Contrato con sisrap-backend.
 *
 * Cada interfaz refleja exactamente un record de Java. Las fechas
 * LocalDateTime / LocalDate / LocalTime llegan como cadenas ISO SIN zona
 * horaria ("2026-09-01T08:30:00"); los Instant (sesión) llegan con "Z".
 * No se agrega ni se quita zona: se tratan como texto.
 */

/** LocalDateTime de Java: "AAAA-MM-DDTHH:MM[:SS[.fff]]", sin zona. */
export type FechaHoraLocal = string;
/** LocalDate de Java: "AAAA-MM-DD". */
export type FechaLocal = string;
/** LocalTime de Java: "HH:MM[:SS]". */
export type HoraLocal = string;
/** Instant de Java: ISO con "Z". */
export type Instante = string;

// ---------------------------------------------------------------------------
// simulacion/dominio
// ---------------------------------------------------------------------------

/** EscenarioSimulacion */
export type EscenarioSimulacion =
  | 'OPERACION_DIARIA'
  | 'SIMULACION_CINCO_DIAS'
  | 'COLAPSO_LOGISTICO';

/** EstadoSimulacion */
export type EstadoSimulacion =
  | 'DETENIDA'
  | 'EJECUTANDO'
  | 'PAUSADA'
  | 'FINALIZADA'
  | 'ERROR';

/** PuntoSimulacion */
export interface PuntoSimulacion {
  x: number;
  y: number;
}

/**
 * VehiculoSnapshot. `estado` es texto libre en el backend; los valores
 * que escribe hoy son los de EstadoVehiculo.
 */
export interface VehiculoSnapshot {
  idVehiculo: string;
  estado: string;
  x: number;
  y: number;
  pedidoObjetivo: number | null;
  caminoActual: PuntoSimulacion[];
}

/** ResumenPedidosSimulacion */
export interface ResumenPedidosSimulacion {
  total: number;
  futuros: number;
  pendientes: number;
  enRuta: number;
  reasignados: number;
  retrasados: number;
  entregados: number;
}

/**
 * SnapshotSimulacion. Cuando no hay corrida (DETENIDA inicial) escenario,
 * relojSimulado, fechaHoraInicio, fechaHoraFin, idSimulacion y mensaje
 * llegan en null.
 */
export interface SnapshotSimulacion {
  idSimulacion: number | null;
  estado: EstadoSimulacion;
  escenario: EscenarioSimulacion | null;
  relojSimulado: FechaHoraLocal | null;
  fechaHoraInicio: FechaHoraLocal | null;
  fechaHoraFin: FechaHoraLocal | null;
  algoritmo: string | null;
  perfil: string | null;
  pedidos: ResumenPedidosSimulacion;
  bloqueosActivos: number;
  vehiculos: VehiculoSnapshot[];
  mensaje: string | null;
}

/** SimulacionController.IniciarSimulacionRequest */
export interface IniciarSimulacionRequest {
  escenario: EscenarioSimulacion;
  fechaInicio: FechaLocal;
  semilla: number | null;
}

// ---------------------------------------------------------------------------
// sesion/api
// ---------------------------------------------------------------------------

/** SesionController.SesionCreadaRespuesta */
export interface SesionCreadaRespuesta {
  sesionId: string;
  token: string;
  creadaEn: Instante;
  expiraEn: Instante;
}

/** SesionController.SesionRespuesta */
export interface SesionRespuesta {
  sesionId: string;
  creadaEn: Instante;
  ultimaActividadEn: Instante;
  expiraEn: Instante;
}

// ---------------------------------------------------------------------------
// mapa
// ---------------------------------------------------------------------------

/** MapaCiudad */
export interface MapaCiudad {
  idCiudad: number;
  nombre: string;
  anchoKm: number;
  altoKm: number;
}

// ---------------------------------------------------------------------------
// almacen
// ---------------------------------------------------------------------------

/** TipoAlmacen */
export type TipoAlmacen = 'CENTRAL' | 'INTERMEDIO';

/** AlmacenOperativo. capacidadMaxima/stockActual en null = ilimitado (central). */
export interface AlmacenOperativo {
  idAlmacen: string;
  nombre: string;
  tipo: TipoAlmacen;
  ubicacionX: number;
  ubicacionY: number;
  capacidadMaxima: number | null;
  stockActual: number | null;
  horaRecarga: HoraLocal | null;
}

// ---------------------------------------------------------------------------
// flota
// ---------------------------------------------------------------------------

/** EstadoVehiculo */
export type EstadoVehiculo =
  | 'DISPONIBLE'
  | 'EN_RUTA'
  | 'EN_AVERIA'
  | 'EN_MANTENIMIENTO'
  | 'RETORNANDO_ALMACEN';

/** VehiculoOperativo. `tipo` es el texto de la columna vehiculo.tipo. */
export interface VehiculoOperativo {
  idVehiculo: string;
  tipo: string;
  capacidadPaquetes: number;
  velocidadKmh: number;
  costoPorKm: number;
  estado: EstadoVehiculo;
  posicionX: number;
  posicionY: number;
  idConductorActual: string | null;
}

/** TipoAveria: los únicos tres tipos del caso. */
export type TipoAveria = 'TIPO1_MENOR' | 'TIPO2_INTERMEDIA' | 'TIPO3_MAYOR';

/** AveriaVehiculo */
export interface AveriaVehiculo {
  idIncidencia: number;
  idVehiculo: string;
  tipoAveria: TipoAveria;
  ubicacionX: number;
  ubicacionY: number;
  fechaOcurrencia: FechaHoraLocal;
  horaRetornoEstimada: FechaHoraLocal | null;
  activa: boolean;
}

/** TipoMantenimiento */
export type TipoMantenimiento = 'PREVENTIVO' | 'CORRECTIVO';

/** MantenimientoVehiculo */
export interface MantenimientoVehiculo {
  idMantenimiento: number;
  idVehiculo: string;
  tipo: TipoMantenimiento;
  fechaInicio: FechaHoraLocal;
  fechaFin: FechaHoraLocal;
  activo: boolean;
}

// ---------------------------------------------------------------------------
// pedido
// ---------------------------------------------------------------------------

/** EstadoPedido */
export type EstadoPedido =
  | 'PENDIENTE'
  | 'EN_RUTA'
  | 'ENTREGADO'
  | 'REASIGNADO'
  | 'RETRASADO';

/** TipoPrioridad */
export type TipoPrioridad =
  | 'REGULAR_36H'
  | 'PRIORIZADO_18H'
  | 'PRIORIZADO_12H'
  | 'PRIORIZADO_8H'
  | 'PRIORIZADO_4H';

/** PedidoOperativo */
export interface PedidoOperativo {
  idPedido: number;
  idCliente: string;
  cantidadQq: number;
  prioridad: TipoPrioridad;
  horasLimite: number;
  fechaLlegada: FechaHoraLocal;
  fechaEntregaReal: FechaHoraLocal | null;
  estado: EstadoPedido;
  ubicacionX: number;
  ubicacionY: number;
}

/** PaginaPedidos */
export interface PaginaPedidos {
  contenido: PedidoOperativo[];
  total: number;
  pagina: number;
  tamanio: number;
}

// ---------------------------------------------------------------------------
// bloqueo
// ---------------------------------------------------------------------------

/** EstadoBloqueo */
export type EstadoBloqueo = 'PROGRAMADO' | 'ACTIVO' | 'FINALIZADO' | 'CANCELADO';

/** BloqueoController.PuntoRespuesta */
export interface PuntoRespuesta {
  x: number;
  y: number;
}


/** BloqueoController.CrearBloqueoRequest */
export interface CrearBloqueoRequest {
  inicio: FechaHoraLocal;
  fin: FechaHoraLocal;
  vertices: PuntoRespuesta[];
}

/** BloqueoController.BloqueoRespuesta */
export interface BloqueoRespuesta {
  idIncidencia: number;
  inicio: FechaHoraLocal;
  fin: FechaHoraLocal;
  estado: EstadoBloqueo;
  vertices: PuntoRespuesta[];
}

// ---------------------------------------------------------------------------
// Errores (ResponseStatusException de Spring Boot)
// ---------------------------------------------------------------------------

/** Cuerpo por defecto de un error de Spring Boot. */
export interface ErrorSpring {
  timestamp?: string;
  status?: number;
  error?: string;
  message?: string;
  path?: string;
}
