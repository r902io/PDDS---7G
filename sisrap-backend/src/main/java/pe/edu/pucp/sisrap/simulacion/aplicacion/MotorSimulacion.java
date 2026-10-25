package pe.edu.pucp.sisrap.simulacion.aplicacion;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.Optional;
import java.util.function.Function;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.LinkedHashSet;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import pe.edu.pucp.sisrap.almacen.dominio.Almacen;
import pe.edu.pucp.sisrap.control.aplicacion.GestionarControlSimulacion;
import pe.edu.pucp.sisrap.geografia.dominio.Nodo;
import pe.edu.pucp.sisrap.flota.dominio.AveriaVehiculo;
import pe.edu.pucp.sisrap.flota.dominio.TipoAveria;
import pe.edu.pucp.sisrap.flota.dominio.Vehiculo;
import pe.edu.pucp.sisrap.parametros.dominio.ParametrosAlgoritmo;
import pe.edu.pucp.sisrap.parametros.dominio.Configuracion;
import pe.edu.pucp.sisrap.pedido.dominio.Pedido;
import pe.edu.pucp.sisrap.planificador.dominio.algoritmo.AlgoritmoGenetico;
import pe.edu.pucp.sisrap.planificador.dominio.construccion.ConstructorVoraz;
import pe.edu.pucp.sisrap.planificador.dominio.modelo.ContextoPlanificacion;
import pe.edu.pucp.sisrap.planificador.dominio.modelo.Ruta;
import pe.edu.pucp.sisrap.planificador.dominio.modelo.JornadaOperativa;
import pe.edu.pucp.sisrap.planificador.dominio.modelo.ArcoReticula;
import pe.edu.pucp.sisrap.planificador.dominio.modelo.Solucion;
import pe.edu.pucp.sisrap.planificador.dominio.objetivo.FuncionObjetivo;
import pe.edu.pucp.sisrap.simulacion.dominio.ConfiguracionSimulacion;
import pe.edu.pucp.sisrap.simulacion.dominio.ContextoOperativo;
import pe.edu.pucp.sisrap.simulacion.dominio.EstadoSimulacion;
import pe.edu.pucp.sisrap.simulacion.dominio.EscenarioSimulacion;
import pe.edu.pucp.sisrap.simulacion.dominio.PuntoSimulacion;
import pe.edu.pucp.sisrap.simulacion.dominio.RepositorioSimulacion;
import pe.edu.pucp.sisrap.simulacion.dominio.RecorridoVehiculo;
import pe.edu.pucp.sisrap.simulacion.dominio.DiagnosticoPlanificacion;
import pe.edu.pucp.sisrap.simulacion.dominio.SnapshotSimulacion;
import pe.edu.pucp.sisrap.simulacion.dominio.VehiculoPersistido;
import pe.edu.pucp.sisrap.simulacion.dominio.VehiculoSnapshot;
import pe.edu.pucp.sisrap.simulacion.infraestructura.SseSimulacion;

@Service
public class MotorSimulacion {

    private static final Logger LOG = LoggerFactory.getLogger(MotorSimulacion.class);

    private static final long NANO_SEGUNDO = 1_000_000_000L;
    private static final double NANOS_POR_HORA = 3_600_000_000_000.0;
    private static final String ALGORITMO_OPERATIVO = "GENETICO";
    private static final int INTERVALO_PLANIFICACION_MIN = 5;
    private static final int MAX_PEDIDOS_ANTICIPADOS = 60;

    private final RepositorioSimulacion repositorio;
    private final GestionarControlSimulacion control;
    private final SseSimulacion sse;
    private final String perfil;
    private final long tickMs;

    private final ScheduledExecutorService ejecutor =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "sisrap-motor-simulacion");
                t.setDaemon(true);
                return t;
            });

    private final Map<String, PlanUnidad> planes = new LinkedHashMap<>();
    private static final int MAX_PASOS_VISUALES = 16;
    private final Map<String, Deque<VehiculoSnapshot.PasoVisual>> pasosVisuales = new HashMap<>();
    private final Map<String, Long> secuenciasVisuales = new HashMap<>();
    private final Map<String, PuntoSimulacion> posicionesPersistidas = new HashMap<>();
    private final Map<String, CargaAveriada> cargasAveriadas = new LinkedHashMap<>();
    private final List<EntregaEnCliente> entregasEnCliente = new ArrayList<>();
    private final AcumuladorIndicadores indicadores = new AcumuladorIndicadores();
    private static final int MAX_LOTE_RECORRIDO = 500;
    private static final int MAX_RECORRIDOS_PENDIENTES = 30_000;
    private static final long REINTENTO_RECORRIDO_NS = TimeUnit.SECONDS.toNanos(5);
    private static final long LOG_RECORRIDO_NS = TimeUnit.MINUTES.toNanos(1);
    private static final long INTERVALO_PUNTO_ESTACIONARIO_MIN = 10;
    private final List<RecorridoVehiculo.Registro> registrosRecorrido = new ArrayList<>();
    private final Map<String, RecorridoVehiculo.Punto> ultimosPuntos = new HashMap<>();
    private long nanoUltimoArchivoRecorrido;
    private long nanoProximoReintentoRecorrido;
    private long nanoUltimoLogErrorRecorrido;
    private long puntosPerdidosRecorrido;
    private String advertenciaRecorrido;

    private volatile SnapshotSimulacion snapshotActual;
    private EstadoSimulacion estado = EstadoSimulacion.DETENIDA;
    private ConfiguracionSimulacion configuracion;
    private Long idSimulacion;
    private LocalDateTime reloj;
    private Set<String> bloqueados = Set.of();
    private List<pe.edu.pucp.sisrap.experimentacion.dominio.Bloqueo> calendarioBloqueos = List.of();
    private long deudaVirtualNanos;
    private int publicadosUltimoCiclo;
    private final Map<Long,String> ultimoDiagnosticoRiesgo = new HashMap<>();

    private long nanoInicioReal;
    private long nanoUltimoTick;
    private long nanoUltimaPlanificacion;
    private long nanoUltimaPersistencia;
    private long nanoUltimaPublicacion;
    private long contadorPlanificaciones;
    private LocalDateTime proximaPlanificacionVirtual;
    private String mensajeEstado;
    private boolean primerIncumplimientoRegistrado;
    private LocalDate fechaDemandaAnticipada;
    private LocalDateTime turnoAnticipado;
    private Map<String, Integer> ordenAnticipado = Map.of();
    private Map<String, String> vehiculoAnticipado = Map.of();
    private String firmaUltimaPlanificacion;

    /**
     * Relación interna entre tiempo simulado y tiempo real.
     *
     * <p>No es configurable ni se expone al frontend.</p>
     */
    private double factorTemporalInterno = 1.0;

    public MotorSimulacion(
            RepositorioSimulacion repositorio,
            GestionarControlSimulacion control,
            SseSimulacion sse,
            @Value("${sisrap.simulacion.perfil:BASE}") String perfil,
            @Value("${sisrap.simulacion.tick-ms:250}") long tickMs) {

        this.repositorio = repositorio;
        this.control = control;
        this.sse = sse;
        this.perfil = perfil == null || perfil.isBlank() ? "BASE" : perfil.trim();
        if (tickMs < 100 || tickMs > 2000) {
            throw new IllegalArgumentException("sisrap.simulacion.tick-ms debe estar entre 100 y 2000 ms");
        }
        this.tickMs = tickMs;
        this.snapshotActual = SnapshotSimulacion.detenida(ALGORITMO_OPERATIVO, this.perfil);
    }

    @PostConstruct
    void iniciarRelojInterno() {
        ejecutor.scheduleAtFixedRate(
                this::tickSeguro,
                tickMs,
                tickMs,
                TimeUnit.MILLISECONDS);
    }

    @PreDestroy
    void cerrar() {
        ejecutor.shutdownNow();
    }

    public synchronized SnapshotSimulacion iniciar(ConfiguracionSimulacion nuevaConfiguracion) {
        if (estado == EstadoSimulacion.EJECUTANDO || estado == EstadoSimulacion.PAUSADA) {
            throw new ConflictoSimulacionException("Ya existe una simulación activa");
        }

        repositorio.prepararEjecucion(nuevaConfiguracion);
        repositorio.activarIncidenciasProgramadas(nuevaConfiguracion.fechaHoraInicio());
        repositorio.sincronizarDisponibilidad(nuevaConfiguracion.fechaHoraInicio());

        long id = repositorio.crearEjecucion(
                nuevaConfiguracion,
                ALGORITMO_OPERATIVO,
                perfil);

        planes.clear();
        pasosVisuales.clear();
        secuenciasVisuales.clear();
        posicionesPersistidas.clear();
        cargasAveriadas.clear();
        entregasEnCliente.clear();
        registrosRecorrido.clear();
        ultimosPuntos.clear();
        configuracion = nuevaConfiguracion;
        idSimulacion = id;
        reloj = nuevaConfiguracion.fechaHoraInicio();
        indicadores.iniciar(reloj, repositorio.cargarPerfilesFlota());
        bloqueados = repositorio.cargarNodosBloqueados(reloj);
        estado = EstadoSimulacion.EJECUTANDO;

        factorTemporalInterno =
                nuevaConfiguracion.factorTemporalInterno();

        mensajeEstado =
                "Simulación iniciada: "
                        + nuevaConfiguracion.escenario().name()
                        + " (duración real objetivo ~"
                        + nuevaConfiguracion
                                .duracionRealObjetivo()
                                .toMinutes()
                        + " min)";

        long ahora = System.nanoTime();
        nanoInicioReal = ahora;
        nanoUltimoTick = ahora;
        nanoUltimaPlanificacion = 0L;
        nanoUltimaPersistencia = 0L;
        nanoUltimaPublicacion = 0L;
        contadorPlanificaciones = 0L;
        primerIncumplimientoRegistrado = false;
        deudaVirtualNanos = 0; publicadosUltimoCiclo = 0; ultimoDiagnosticoRiesgo.clear();
        proximaPlanificacionVirtual = reloj;
        fechaDemandaAnticipada = null;
        turnoAnticipado = null;
        ordenAnticipado = Map.of();
        vehiculoAnticipado = Map.of();
        firmaUltimaPlanificacion = null;
        calendarioBloqueos = repositorio.cargarCalendarioBloqueos(reloj, reloj.plusHours(48));
        nanoUltimoArchivoRecorrido = System.nanoTime();
        nanoProximoReintentoRecorrido = 0L;
        nanoUltimoLogErrorRecorrido = 0L;
        puntosPerdidosRecorrido = 0L;
        advertenciaRecorrido = null;
        registrosRecorrido.clear();
        ultimosPuntos.clear();
        repositorio.publicarPedidosHasta(reloj);
        intentarPlanificar();
        proximaPlanificacionVirtual = reloj.plusMinutes(INTERVALO_PLANIFICACION_MIN);
        nanoUltimoTick = System.nanoTime();
        registrarEstadoRecorrido("INICIO");
        vaciarRecorridos();

        publicarSnapshot(true);
        return snapshotActual;
    }

    public synchronized SnapshotSimulacion pausar() {
        exigirEstado(EstadoSimulacion.EJECUTANDO, "La simulación no está ejecutándose");
        estado = EstadoSimulacion.PAUSADA;
        mensajeEstado = "Simulación pausada";
        repositorio.actualizarEjecucion(
                idSimulacion,
                estado,
                reloj);
        publicarSnapshot(true);
        return snapshotActual;
    }

    public synchronized SnapshotSimulacion reanudar() {
        exigirEstado(EstadoSimulacion.PAUSADA, "La simulación no está pausada");
        estado = EstadoSimulacion.EJECUTANDO;
        nanoUltimoTick = System.nanoTime();
        mensajeEstado = "Simulación reanudada";
        repositorio.actualizarEjecucion(
                idSimulacion,
                estado,
                reloj);
        publicarSnapshot(true);
        return snapshotActual;
    }

    public synchronized SnapshotSimulacion detener() {
        if (estado != EstadoSimulacion.EJECUTANDO && estado != EstadoSimulacion.PAUSADA) {
            throw new ConflictoSimulacionException("No existe una simulación activa");
        }
        finalizar(false, "Simulación detenida por el controlador");
        return snapshotActual;
    }

    public SnapshotSimulacion estadoActual() {
        return snapshotActual;
    }

    /** Registra la avería bajo el mismo bloqueo del motor que avanza el reloj. */
    public synchronized AveriaVehiculo registrarAveriaVehiculo(
            String idVehiculo, Function<LocalDateTime, AveriaVehiculo> guardarAveria) {
        if (estado != EstadoSimulacion.EJECUTANDO && estado != EstadoSimulacion.PAUSADA) {
            throw new IllegalArgumentException("Debe existir una simulación activa");
        }
        PlanUnidad plan = planes.get(idVehiculo);
        if (plan != null) {
            repositorio.actualizarPosiciones(Map.of(idVehiculo,
                    new PuntoSimulacion(plan.posicion.getX(), plan.posicion.getY())));
        }
        AveriaVehiculo averia = guardarAveria.apply(reloj);
        if (plan != null) {
            registrarEventoRecorrido(plan, "AVERIA");
            interrumpirPorAveria(plan, averia);
        }
        programarPlanificacionInmediata();
        mensajeEstado = "Avería " + averia.tipoAveria()
                + " en " + idVehiculo + ": revisión de pedidos a bordo";
        publicarSnapshot(true);
        return averia;
    }

    /** Aplica velocidades a las rutas activas, incluso las que cruzan cambios de turno. */
    public synchronized void aplicarVelocidadesVehiculos(Map<String, Double> velocidades) {
        if (estado != EstadoSimulacion.EJECUTANDO && estado != EstadoSimulacion.PAUSADA) {
            throw new ConflictoSimulacionException("No existe una simulación activa");
        }
        for (PlanUnidad plan : planes.values()) {
            Double nueva = velocidades.get(indicadores.tipoVehiculo(plan.idVehiculo));
            if (nueva != null && Double.compare(plan.velocidadKmh, nueva) != 0) {
                plan.creditoHoras *= plan.velocidadKmh/nueva;
                plan.velocidadKmh = nueva;
                plan.camino.clear(); plan.salidasCamino.clear();
                registrarEventoRecorrido(plan, "VELOCIDAD");
            }
        }
        programarPlanificacionInmediata();
        mensajeEstado = "Velocidades actualizadas para la flota en esta corrida";
        publicarSnapshot(true);
    }

    public synchronized void sincronizarBloqueos() {
        if (estado != EstadoSimulacion.EJECUTANDO && estado != EstadoSimulacion.PAUSADA) {
            return;
        }
        calendarioBloqueos=repositorio.cargarCalendarioBloqueos(reloj,reloj.plusHours(48));
        if (actualizarBloqueosActivos()) {
            publicarSnapshot(true);
        }
    }

    public synchronized pe.edu.pucp.sisrap.pedido.dominio.PedidoOperativo registrarPedidoManual(
            String cliente, int cantidad,
            pe.edu.pucp.sisrap.pedido.dominio.TipoPrioridad prioridad, int x, int y) {
        if (estado != EstadoSimulacion.EJECUTANDO && estado != EstadoSimulacion.PAUSADA) {
            throw new ConflictoSimulacionException("La simulación debe estar activa para registrar pedidos");
        }
        var pedido = repositorio.registrarPedidoManual(cliente, cantidad, prioridad, x, y, reloj);
        proximaPlanificacionVirtual = reloj;
        nanoUltimaPlanificacion = 0L;
        firmaUltimaPlanificacion = null;
        publicarSnapshot(true);
        return pedido;
    }

    private void tickSeguro() {
        synchronized (this) {
            try {
                tick();
            } catch (Throwable e) {
                fallar(e);
            }
        }
    }

    private void tick() {
        if(estado!=EstadoSimulacion.EJECUTANDO) {
            if(estado==EstadoSimulacion.PAUSADA) publicarSnapshot(false);
            return;
        }
        long ahora=System.nanoTime();
        long delta=Math.max(0,ahora-nanoUltimoTick); nanoUltimoTick=ahora;
        long disponibles=Duration.between(reloj,configuracion.fechaHoraFin()).toNanos();
        deudaVirtualNanos=Math.min(disponibles,Math.addExact(deudaVirtualNanos,
                Math.max(1,Math.round(delta*factorTemporalInterno))));
        if (debeIntentarPlanificar(ahora)) {
            var estados = repositorio.sincronizarYCargarDisponibilidad(reloj);
            cancelarPlanesInterrumpidos(estados); actualizarCargasAveriadas(estados);
            intentarPlanificar(); nanoUltimaPlanificacion=ahora;
            LocalDateTime revision=reloj.plusMinutes(INTERVALO_PLANIFICACION_MIN);
            if(proximaPlanificacionVirtual==null || !proximaPlanificacionVirtual.isAfter(reloj)
                    || proximaPlanificacionVirtual.isAfter(revision)) proximaPlanificacionVirtual=revision;
        }
        int pasos=0;
        long inicioAvances = System.nanoTime();
        // Ceder entre eventos; la deuda pendiente se conserva íntegra para el próximo tick.
        while(deudaVirtualNanos>0 && estado==EstadoSimulacion.EJECUTANDO && pasos<64
                && (pasos == 0 || System.nanoTime() - inicioAvances < 50_000_000L)) {
            pasos++;
            LocalDateTime hasta=reloj.plusNanos(Math.min(deudaVirtualNanos,60L*NANO_SEGUNDO));
            LocalDateTime evento=repositorio.proximoEvento(reloj,hasta);
            if(evento.isBefore(hasta)) hasta=evento;
            LocalDateTime turno=JornadaOperativa.inicioTurno(reloj);
            for(LocalDateTime limite:List.of(turno.plusHours(4),turno.plusHours(5),turno.plusHours(8),
                    reloj.toLocalDate().atTime(23,59,59),reloj.toLocalDate().plusDays(1).atStartOfDay())) {
                if(limite.isAfter(reloj)&&limite.isBefore(hasta)) hasta=limite;
            }
            if(proximaPlanificacionVirtual!=null && proximaPlanificacionVirtual.isAfter(reloj)
                    && proximaPlanificacionVirtual.isBefore(hasta)) hasta=proximaPlanificacionVirtual;
            if(!JornadaOperativa.refrigerio(reloj)) {
                for(PlanUnidad p:planes.values()) {
                    double horas=p.trasvaseRestanteHoras>0 ? p.trasvaseRestanteHoras
                            : p.fase==FaseUnidad.ATENDIENDO ? p.servicioRestanteHoras
                            : p.distanciaNodoKm/p.velocidadKmh-p.creditoHoras;
                    if(horas>1e-12) {
                        LocalDateTime fin=JornadaOperativa.avanzar(reloj,horas);
                        if(fin.isAfter(reloj)&&fin.isBefore(hasta)) hasta=fin;
                    }
                }
            }
            long consumido=Duration.between(reloj,hasta).toNanos();
            if(consumido<=0) throw new IllegalStateException("El próximo evento no avanza el reloj");
            avanzarHasta(hasta,ahora);
            deudaVirtualNanos-=consumido;
        }
        if (estado == EstadoSimulacion.EJECUTANDO) publicarSnapshot(false);
    }

    private void avanzarHasta(LocalDateTime nuevoReloj,long ahoraNano) {
        LocalDateTime relojAnterior=reloj;
        LocalDateTime finTurno=JornadaOperativa.finTurno(relojAnterior);
        reloj=nuevoReloj;
        double deltaHoras=JornadaOperativa.horasEfectivas(relojAnterior,reloj);
        indicadores.registrarOcupacion(new LinkedHashSet<>(planes.keySet()),deltaHoras);
        actualizarPlanes(deltaHoras);
        actualizarEntregasEnCliente(deltaHoras);
        persistirPosiciones();

        repositorio.recargarAlmacenesSiCorresponde(
                relojAnterior,
                reloj
        );

        repositorio.publicarPedidosHasta(reloj);
        if (repositorio.activarIncidenciasProgramadas(reloj)) {
            calendarioBloqueos=repositorio.cargarCalendarioBloqueos(reloj,reloj.plusHours(48));
            programarPlanificacionInmediata();
        }
        Map<String, VehiculoPersistido> estadosVehiculos =
                repositorio.sincronizarYCargarDisponibilidad(reloj);
        cancelarPlanesInterrumpidos(estadosVehiculos);
        actualizarCargasAveriadas(estadosVehiculos);

        actualizarBloqueosActivos();

        if (!reloj.isBefore(finTurno)) relevarConductores();
        repositorio.marcarPedidosRetrasados(reloj);

        // Registrar el primer incumplimiento en los tres escenarios. Solo el
        // escenario COLAPSO_LOGISTICO finaliza por esta condición.
        if (!primerIncumplimientoRegistrado) {
            var incumplimiento = repositorio.primerIncumplimiento(reloj);
            if (incumplimiento.isPresent()) {
                var evento = incumplimiento.get();
                repositorio.guardarColapso(idSimulacion, evento);
                primerIncumplimientoRegistrado = true;
                if (configuracion.escenario() == EscenarioSimulacion.COLAPSO_LOGISTICO) {
                    finalizar(true, "Colapso logístico: pedido " + evento.idPedido()
                            + " fuera de plazo en " + evento.instante());
                    return;
                }
                mensajeEstado = "Se registró un incumplimiento del pedido "
                        + evento.idPedido() + " en " + evento.instante()
                        + ". La simulación continuará hasta completar el período.";
            }
        }

        // No crear más rutas ni anticipar demanda después de alcanzar el horizonte.
        if (!reloj.isBefore(configuracion.fechaHoraFin())) {
            String descripcion = switch (configuracion.escenario()) {
                case OPERACION_DIARIA -> "Operación diaria completada: 24 horas simuladas";
                case SIMULACION_CINCO_DIAS -> "Simulación de cinco días completada: 120 horas simuladas";
                case COLAPSO_LOGISTICO -> "Se alcanzó el horizonte máximo de la simulación de colapso sin incumplimientos";
            };
            finalizar(true, descripcion);
            return;
        }

        var resumenPublicados=repositorio.resumirPedidos(configuracion.fechaHoraInicio(),
                configuracion.fechaHoraFin(),reloj);
        int publicados=resumenPublicados.total()-resumenPublicados.futuros();
        if(publicados!=publicadosUltimoCiclo) {
            publicadosUltimoCiclo=publicados; programarPlanificacionInmediata();
        }
        if (debeIntentarPlanificar(ahoraNano)) {
            intentarPlanificar();
            nanoUltimaPlanificacion = ahoraNano;
            LocalDateTime revision=reloj.plusMinutes(INTERVALO_PLANIFICACION_MIN);
            if(proximaPlanificacionVirtual==null || !proximaPlanificacionVirtual.isAfter(reloj)
                    || proximaPlanificacionVirtual.isAfter(revision)) proximaPlanificacionVirtual=revision;
        }

        registrarEstadoRecorrido("ESTADO", estadosVehiculos);
        if (registrosRecorrido.size() >= MAX_LOTE_RECORRIDO
                || ahoraNano - nanoUltimoArchivoRecorrido >= NANO_SEGUNDO) {
            vaciarRecorridos();
            nanoUltimoArchivoRecorrido = ahoraNano;
        }

        if (ahoraNano - nanoUltimaPersistencia >= NANO_SEGUNDO) {
            repositorio.actualizarEjecucion(
                idSimulacion,
                estado,
                reloj);
            nanoUltimaPersistencia = ahoraNano;
        }

    }

    private void prepararAnticipoSiCorresponde() {
        LocalDate dia = reloj.toLocalDate();
        LocalDateTime turno = JornadaOperativa.inicioTurno(reloj);
        if (dia.equals(fechaDemandaAnticipada) && turno.equals(turnoAnticipado)) {
            return;
        }

        fechaDemandaAnticipada = dia;
        turnoAnticipado = turno;
        ordenAnticipado = Map.of();
        vehiculoAnticipado = Map.of();
        firmaUltimaPlanificacion = null;

        List<Pedido> previstos = repositorio.cargarPrevisionDiaria(dia, reloj);
        if (previstos.isEmpty()) {
            return;
        }

        LocalDateTime finTurno = JornadaOperativa.finTurno(reloj);
        List<Pedido> candidatos = previstos.stream()
                .filter(p -> !p.getFechaLlegada().isBefore(reloj))
                .filter(p -> p.getFechaLlegada().isBefore(finTurno))
                .sorted(Comparator.comparing(Pedido::getFechaLimite)
                        .thenComparing(Pedido::getIdPedido))
                .limit(MAX_PEDIDOS_ANTICIPADOS)
                .toList();
        if (candidatos.isEmpty()) {
            return;
        }

        ContextoOperativo operativo = repositorio.cargarContexto(
                perfil, reloj, configuracion.fechaHoraFin());
        ContextoPlanificacion base = operativo.contexto();
        int capacidadMaxima = base.getVehiculos().stream()
                .mapToInt(v -> v.getCapacidadPaquetes()).max().orElse(0);
        List<Pedido> porPlanificar = new ArrayList<>();
        for (Pedido pedido : candidatos) {
            int restante = pedido.getCantidadQq();
            int parte = 0;
            while (restante > 0 && capacidadMaxima > 0) {
                int cantidad = Math.min(capacidadMaxima, restante);
                long identificador = pedido.getIdPedido() * 1000L - ++parte;
                porPlanificar.add(new Pedido(
                        identificador, pedido.getIdCliente(), cantidad,
                        pedido.getPrioridad(), pedido.getUbicacion(),
                        pedido.getFechaLlegada(), pedido.getHorasLimite())
                        .disponibleDesde(reloj));
                restante -= cantidad;
            }
        }
        if (porPlanificar.isEmpty()) {
            return;
        }

        ContextoPlanificacion contexto = new ContextoPlanificacion(
                porPlanificar, base.getVehiculos(), base.getAlmacenes(), reloj,
                base.reglas(), base.nodosBloqueados(), true, base.calendarioBloqueos(), base.disponibleHasta());
        ParametrosAlgoritmo parametros = new ParametrosAlgoritmo(
                parametrosAcotados(operativo.configuracion(), 8, 8),
                configuracion.semilla() ^ dia.toEpochDay() ^ turno.getHour());
        AlgoritmoGenetico genetico = new AlgoritmoGenetico(
                parametros, new FuncionObjetivo(operativo.configuracion(), contexto));
        Solucion solucion = genetico.planificar(contexto);

        Map<String, Integer> orden = new HashMap<>();
        Map<String, String> vehiculos = new HashMap<>();
        int secuencia = 0;
        for (Ruta ruta : solucion.getRutas()) {
            for (Pedido pedido : ruta.getSecuenciaPedidos()) {
                orden.putIfAbsent(pedido.getIdCliente(), secuencia++);
                vehiculos.putIfAbsent(pedido.getIdCliente(), ruta.getVehiculo().getIdVehiculo());
            }
        }
        for (Pedido pedido : candidatos) {
            orden.putIfAbsent(pedido.getIdCliente(), secuencia++);
        }
        ordenAnticipado = Map.copyOf(orden);
        vehiculoAnticipado = Map.copyOf(vehiculos);
    }

    private static Configuracion parametrosAcotados(Configuracion base,
                                                   int poblacionMaxima,
                                                   int generacionesMaximas) {
        Map<String, String> valores = new HashMap<>(base.valores());
        int poblacion = Math.max(3, Math.min(base.entero("ga.poblacion"), poblacionMaxima));
        int elitismo = Math.max(1, Math.min(base.entero("ga.elitismo"), poblacion - 1));
        int torneo = Math.max(2, Math.min(base.entero("ga.torneo"), poblacion));
        valores.putIfAbsent("planificador.maxTiempoMs", "200");
        valores.put("ga.poblacion", Integer.toString(poblacion));
        valores.put("ga.generaciones", Integer.toString(
                Math.max(1, Math.min(base.entero("ga.generaciones"), generacionesMaximas))));
        valores.put("ga.elitismo", Integer.toString(elitismo));
        valores.put("ga.torneo", Integer.toString(torneo));
        return new Configuracion(valores);
    }

    private Solucion semillaDesdePrevision(ContextoPlanificacion contexto) {
        Solucion semilla = new Solucion();
        List<Ruta> rutas = ConstructorVoraz.crearRutasVacias(contexto);
        Map<String, Ruta> porVehiculo = new HashMap<>();
        rutas.forEach(r -> porVehiculo.put(r.getVehiculo().getIdVehiculo(), r));
        List<Pedido> ordenados = new ArrayList<>(contexto.getPedidos());
        ordenados.sort(Comparator
                .comparingInt((Pedido p) -> ordenAnticipado.getOrDefault(
                        p.getIdCliente(), Integer.MAX_VALUE))
                .thenComparing(Pedido::getFechaLimite)
                .thenComparing(Pedido::getIdPedido));
        for (Pedido pedido : ordenados) {
            Ruta ruta = porVehiculo.get(vehiculoAnticipado.get(pedido.getIdCliente()));
            if (ruta != null && ruta.getVehiculo().isDisponible()
                    && ruta.cargaTotal() + pedido.getCantidadQq()
                    <= ruta.getVehiculo().getCapacidadPaquetes()) {
                ruta.getSecuenciaPedidos().add(pedido);
            }
        }
        semilla.setRutas(rutas);
        return semilla;
    }

    private String firmarContexto(ContextoPlanificacion contexto) {
        StringBuilder firma = new StringBuilder();
        for (Pedido pedido : contexto.getPedidos()) {
            firma.append(pedido.getIdPedido()).append(':').append(pedido.getCantidadQq()).append(';');
        }
        firma.append('|');
        contexto.getVehiculos().stream()
                .filter(v -> v.isDisponible() && !planes.containsKey(v.getIdVehiculo()))
                .map(v -> v.getIdVehiculo() + '@'
                        + v.getPosicionActual().getX() + ',' + v.getPosicionActual().getY())
                .sorted().forEach(id -> firma.append(id).append(';'));
        for (var almacen : contexto.getAlmacenes()) {
            firma.append(almacen.getIdAlmacen()).append('=')
                    .append(almacen.getStockActual()).append(';');
        }
        firma.append('|').append(contexto.nodosBloqueados().hashCode());
        // No congelar un rechazo durante toda una hora: la criticidad cambia
        // aunque no lleguen pedidos, los vehículos no se muevan y no cambie el stock.
        firma.append('|').append(reloj.getHour()).append(':')
                .append(reloj.getMinute());
        return firma.toString();
    }

    private boolean debeIntentarPlanificar(long ahoraNano) {
        if (estado != EstadoSimulacion.EJECUTANDO) {
            return false;
        }
        if (proximaPlanificacionVirtual != null && reloj.isBefore(proximaPlanificacionVirtual)) {
            return false;
        }
        return true;
    }

    /** Reoptimiza pedidos físicamente a bordo; no los traslada a otra unidad. */
    private int reoptimizarRutasEnCurso() {
        int modificadas = 0;
        for (PlanUnidad plan : planes.values()) {
            if (plan.fase != FaseUnidad.IR_PEDIDO || !plan.stockTomado
                    || plan.origenRescate != null || !plan.entregasEnParada.isEmpty()) {
                continue;
            }
            if (plan.creditoHoras > 1e-12) {
                plan.reoptimizarEnSiguienteNodo = true; continue;
            }
            List<Pedido> original = new ArrayList<>();
            if (plan.pedidoObjetivo != null) original.add(plan.pedidoObjetivo);
            original.addAll(plan.pedidosPendientes);
            if (original.size() < 2 || original.size() > 24) continue;

            List<GrupoEntrega> grupos = agruparEntregas(original);
            if (grupos.size() < 2) continue;
            List<GrupoEntrega> porPlazo = new ArrayList<>(grupos);
            porPlazo.sort(Comparator.comparing(GrupoEntrega::plazo)
                    .thenComparing(g -> g.primero().getIdPedido()));
            List<GrupoEntrega> porProximidad = recorridoPorProximidad(plan, grupos);

            EvaluacionRuta base = evaluarRutaActiva(plan, grupos);
            List<GrupoEntrega> mejor = grupos;
            EvaluacionRuta mejorValor = base;
            for (List<GrupoEntrega> propuesta : List.of(porPlazo, porProximidad)) {
                if (propuesta.size() != grupos.size()) continue;
                EvaluacionRuta valor = evaluarRutaActiva(plan, propuesta);
                if (valor != null && (mejorValor == null || valor.mejorQue(mejorValor))) {
                    mejorValor = valor;
                    mejor = propuesta;
                }
            }
            if (mejor == grupos || mejorValor == null) continue;
            List<Pedido> nuevoOrden = mejor.stream()
                    .flatMap(g -> g.pedidos().stream()).toList();
            if (nuevoOrden.equals(original)) continue;
            plan.pedidoObjetivo = nuevoOrden.get(0);
            plan.pedidosPendientes.clear();
            for (int i = 1; i < nuevoOrden.size(); i++) {
                plan.pedidosPendientes.addLast(nuevoOrden.get(i));
            }
            plan.destinoActual = plan.pedidoObjetivo.getUbicacion();
            plan.camino.clear();
            plan.salidasCamino.clear();

            registrarEventoRecorrido(plan, "REPLANIFICACION");
            modificadas++;
        }
        return modificadas;
    }

    private record GrupoEntrega(List<Pedido> pedidos, Nodo destino, LocalDateTime plazo) {
        Pedido primero() { return pedidos.get(0); }
    }

    private record EvaluacionRuta(int tardios, long retrasoNanos, double tiempoAtencion,
                                 double margenMinimo, double distanciaKm) {
        boolean mejorQue(EvaluacionRuta otra) {
            if (tardios != otra.tardios) return tardios < otra.tardios;
            if (retrasoNanos != otra.retrasoNanos) return retrasoNanos < otra.retrasoNanos;
            if (Math.abs(tiempoAtencion - otra.tiempoAtencion) > 1e-6)
                return tiempoAtencion < otra.tiempoAtencion;
            if (Math.abs(margenMinimo - otra.margenMinimo) > 1e-6)
                return margenMinimo > otra.margenMinimo;
            return distanciaKm + 1e-6 < otra.distanciaKm;
        }
    }

    private List<GrupoEntrega> agruparEntregas(List<Pedido> pedidos) {
        Map<String, List<Pedido>> porParada = new LinkedHashMap<>();
        for (Pedido pedido : pedidos) {
            Nodo n = pedido.getUbicacion();
            String clave = n.getX() + "," + n.getY() + ":" + pedido.getIdCliente();
            porParada.computeIfAbsent(clave, k -> new ArrayList<>()).add(pedido);
        }
        List<GrupoEntrega> salida = new ArrayList<>();
        for (List<Pedido> parada : porParada.values()) {
            LocalDateTime plazo = parada.stream().map(Pedido::getFechaLimite)
                    .min(LocalDateTime::compareTo).orElseThrow();
            salida.add(new GrupoEntrega(List.copyOf(parada),
                    parada.get(0).getUbicacion(), plazo));
        }
        return salida;
    }

    private List<GrupoEntrega> recorridoPorProximidad(PlanUnidad plan,
                                                       List<GrupoEntrega> paradas) {
        List<GrupoEntrega> restantes = new ArrayList<>(paradas);
        List<GrupoEntrega> orden = new ArrayList<>();
        Nodo actual = plan.posicion;
        Nodo anterior = plan.posicionAnterior;
        LocalDateTime tiempo = reloj;
        while (!restantes.isEmpty()) {
            GrupoEntrega mejor = null;
            double mejorPuntuacion = Double.POSITIVE_INFINITY;
            pe.edu.pucp.sisrap.planificador.dominio.modelo.DistanciaReticula.Trayecto mejorTramo = null;
            for (GrupoEntrega parada : restantes) {
                var tramo = pe.edu.pucp.sisrap.planificador.dominio.modelo.DistanciaReticula.trayectoTemporal(
                        actual, parada.destino(), anterior, tiempo, plan.velocidadKmh,
                        plan.distanciaNodoKm, bloqueados, calendarioBloqueos, plan.ancho, plan.alto);
                if (tramo.isEmpty()) continue;
                LocalDateTime llegada = tramo.get().llegada();
                long retraso = Math.max(0, Duration.between(parada.plazo(), llegada).toNanos());
                double puntuacion = Duration.between(tiempo, llegada).toNanos() / NANOS_POR_HORA
                        + (retraso > 0 ? 100_000_000.0 : 0);
                if (puntuacion < mejorPuntuacion) {
                    mejorPuntuacion = puntuacion;
                    mejor = parada;
                    mejorTramo = tramo.get();
                }
            }
            if (mejor == null) return List.of();
            tiempo = JornadaOperativa.avanzar(mejorTramo.llegada(), plan.servicioHoras);
            List<Nodo> nodos = mejorTramo.nodos();
            if (!nodos.isEmpty()) anterior = nodos.size() == 1
                    ? actual : nodos.get(nodos.size() - 2);
            actual = mejor.destino();
            orden.add(mejor);
            restantes.remove(mejor);
        }
        return orden;
    }

    private EvaluacionRuta evaluarRutaActiva(PlanUnidad plan,List<GrupoEntrega> grupos) {
        Ruta ruta=rutaEnCurso(plan,grupos.stream().flatMap(g->g.pedidos().stream()).toList());
        ruta.recalcular();
        if(ruta.isIntransitable()) return null;
        long retraso=0;
        int tardios=0;
        double margen=Double.POSITIVE_INFINITY;
        double tiempoAtencion=0;
        for(int i=0;i<ruta.getSecuenciaPedidos().size();i++) {
            tiempoAtencion+=ruta.tiempoAtencionDe(i);
            double demora=ruta.retrasoDe(i);
            if(demora>0) tardios++;
            retraso=Math.addExact(retraso,Math.max(0,Math.round(demora*NANOS_POR_HORA)));
            margen=Math.min(margen,Duration.between(ruta.getInicio(),
                    ruta.getSecuenciaPedidos().get(i).getFechaLimite()).toNanos()/NANOS_POR_HORA
                    - ruta.horaLlegadaDe(i));
        }
        return new EvaluacionRuta(tardios,retraso,tiempoAtencion,margen,ruta.getDistanciaTotalKm());
    }

    private Ruta rutaEnCurso(PlanUnidad plan,List<Pedido> pedidos) {
        Vehiculo v=new Vehiculo(plan.idVehiculo,Math.max(plan.cargaInicial,1),plan.velocidadKmh,0,plan.posicion);
        var reglas=new pe.edu.pucp.sisrap.parametros.dominio.ReglasPlanificacion(
                plan.servicioHoras,plan.distanciaNodoKm,false,plan.incluirRetorno,plan.ancho,plan.alto);
        LocalDateTime inicio=reloj;
        if(plan.fase==FaseUnidad.ATENDIENDO)
            inicio=JornadaOperativa.avanzar(inicio,plan.servicioRestanteHoras);
        Ruta ruta=new Ruta(v,plan.almacenOrigen,plan.posicion,true,inicio,reglas);
        if(plan.stockTomado) ruta.continuarConCarga(plan.posicionAnterior);
        ruta.setAlmacenRetorno(plan.almacenRetorno);
        ruta.setNodosBloqueados(bloqueados);ruta.setCalendarioBloqueos(calendarioBloqueos);
        ruta.getSecuenciaPedidos().addAll(pedidos);
        return ruta;
    }

    private void intentarPlanificar() {
        calendarioBloqueos=repositorio.cargarCalendarioBloqueos(reloj,reloj.plusHours(48));
        int rutasReordenadas = reoptimizarRutasEnCurso();
        if (rutasReordenadas > 0) {
            mensajeEstado = "Se replanificaron " + rutasReordenadas
                    + " rutas activas desde su posición actual";
        }
        ContextoOperativo operativo = repositorio.cargarContexto(
                perfil,
                reloj,
                configuracion.fechaHoraFin());

        ContextoPlanificacion base = operativo.contexto();
        int rescates = planificarRescates(base);
        if (rescates > 0) firmaUltimaPlanificacion = null;

        // Un pedido vencido pero físicamente a bordo no se puede despachar de nuevo.
        ContextoPlanificacion disponibles = excluirPedidosYaComprometidos(base);
        List<Pedido> vencidos = disponibles.getPedidos().stream()
                .filter(p -> p.getFechaLimite().isBefore(reloj)).toList();
        List<Pedido> conPlazo = ConstructorVoraz.ordenarPorUrgencia(disponibles).stream()
                .filter(p -> !p.getFechaLimite().isBefore(reloj)).toList();

        // Reducir NUEVOS incumplimientos tiene precedencia sobre recuperar
        // incumplimientos ya irreversibles. El bloque finally garantiza que
        // tampoco se abandone a ningún pedido vencido si GA no encuentra ruta.
        try {
            planificarPedidosConPlazo(disponibles, conPlazo, operativo);
        } finally {
            revisarRiesgoPendientes(disponibles,conPlazo);
            planificarRescates(base, true);
            ContextoPlanificacion despuesDeGa = contextoConPedidos(disponibles, vencidos);
            planificarRecuperacionVencidos(despuesDeGa, vencidos, operativo);
        }
    }

    private void revisarRiesgoPendientes(ContextoPlanificacion contexto,List<Pedido> pedidos) {
        List<DiagnosticoPlanificacion> diagnosticos=new ArrayList<>();
        List<ContextoPlanificacion> disponibilidades = new ArrayList<>();
        for(PlanUnidad plan:planes.values()) {
            if(plan.origenRescate!=null || plan.retornoMantenimiento) continue;
            List<Pedido> carga=new ArrayList<>();
            if(plan.pedidoObjetivo!=null && plan.fase!=FaseUnidad.ATENDIENDO) carga.add(plan.pedidoObjetivo);
            carga.addAll(plan.pedidosPendientes);
            Ruta vigente=rutaEnCurso(plan,carga);vigente.recalcular();
            if(vigente.isIntransitable()) continue;
            LocalDateTime libre=vigente.getInicio().plusNanos(Math.round(vigente.getTiempoTotalHoras()*NANOS_POR_HORA));
            for(var v:contexto.getVehiculos()) if(v.getIdVehiculo().equals(plan.idVehiculo)) {
                Vehiculo proyectado=new Vehiculo(v.getIdVehiculo(),v.getCapacidadPaquetes(),v.getVelocidadKmh(),
                        v.getCostoPorKm(),plan.almacenRetorno.getUbicacion());
                disponibilidades.add(new ContextoPlanificacion(List.of(),List.of(proyectado),
                        contexto.getAlmacenes(),libre,contexto.reglas(),contexto.nodosBloqueados(),true,
                        contexto.calendarioBloqueos(), contexto.disponibleHasta()));
            }
        }
        Set<Long> comprometidos = new java.util.HashSet<>();
        for (PlanUnidad plan : planes.values()) comprometidos.addAll(plan.idsNoEntregados());
        for(Pedido pedido:pedidos) {
            if(comprometidos.contains(pedido.getIdPedido())) continue;
            double margen=ConstructorVoraz.holguraOptimista(pedido,contexto);
            for (ContextoPlanificacion futuro : disponibilidades)
                margen=Math.max(margen,ConstructorVoraz.holguraOptimista(pedido,futuro));
            String motivo=Double.isFinite(margen) ? (margen<0 ? "RIESGO_SIN_LLEGADA_PUNTUAL_ESTIMADA"
                    : margen<=1 ? "RIESGO_PLAZO_MARGEN_MENOR_60_MIN" : "PENDIENTE_REQUIERE_CONFIRMAR_RUTA")
                    : "RIESGO_SIN_VEHICULO_COMPATIBLE";
            long visible=repositorio.idPedidoVisible(pedido.getIdPedido());
            if(!motivo.equals(ultimoDiagnosticoRiesgo.put(visible,motivo)))
                diagnosticos.add(new DiagnosticoPlanificacion(reloj,visible,motivo));
            if(margen<=1 || !Double.isFinite(margen)) {
                proximaPlanificacionVirtual=reloj.plusMinutes(1);
                mensajeEstado="Alerta de plazo: pedido "+visible+" sin margen suficiente; se revisarán alternativas";
            }
        }
        if(!diagnosticos.isEmpty()) repositorio.registrarDiagnosticos(idSimulacion,diagnosticos);
    }

    private void planificarPedidosConPlazo(
            ContextoPlanificacion disponibles,
            List<Pedido> conPlazo,
            ContextoOperativo operativo) {
        if (conPlazo.isEmpty()) {
            firmaUltimaPlanificacion = null;
            return;
        }
        ContextoPlanificacion contexto = contextoConPedidos(disponibles, conPlazo);
        if (ConsolidadorDespachos.convieneEsperar(conPlazo, contexto,
                configuracion.fechaHoraFin())) {
            mensajeEstado = "Consolidando pedidos confirmados sin comprometer sus plazos";
            return;
        }
        boolean hayDisponibles = contexto.getVehiculos().stream()
                .anyMatch(v -> v.isDisponible() && !planes.containsKey(v.getIdVehiculo()));
        if (!hayDisponibles) {
            registrarDiagnosticosSinRuta(contexto.getPedidos(), "SIN_VEHICULOS_DISPONIBLES");
            return;
        }

        String firma = firmarContexto(contexto);
        if (firma.equals(firmaUltimaPlanificacion)) {
            return;
        }
        firmaUltimaPlanificacion = firma;

        long semilla = configuracion.semilla() + contadorPlanificaciones++;
        ParametrosAlgoritmo parametros = new ParametrosAlgoritmo(
                parametrosAcotados(operativo.configuracion(), 12, 14),
                semilla);
        FuncionObjetivo objetivo = new FuncionObjetivo(
                operativo.configuracion(),
                contexto);
        AlgoritmoGenetico planificador = new AlgoritmoGenetico(parametros, objetivo,
                Long.parseLong(operativo.configuracion().valores().getOrDefault("planificador.maxTiempoMs", "1000")));

        Solucion solucion = ordenAnticipado.isEmpty()
                ? planificador.planificar(contexto)
                : planificador.replanificar(semillaDesdePrevision(contexto), contexto);
        Map<String, Integer> cargasReservadas = new HashMap<>();
        for (PlanUnidad enCurso : planes.values()) {
            if (!enCurso.stockTomado) {
                cargasReservadas.merge(enCurso.almacenOrigen.getIdAlmacen(),
                        enCurso.cargaInicial, Integer::sum);
            }
        }
        ConsolidadorDespachos.consolidar(solucion, contexto, cargasReservadas);
        // Validar después de elegir el retorno, incluyendo su mantenimiento.
        solucion = ProtectorPlazos.proteger(contexto, solucion, objetivo, semilla);

        List<PlanUnidad> nuevos = new ArrayList<>();
        List<Ruta> rutasAceptadas = new ArrayList<>();
        List<Long> pedidosAsignados = new ArrayList<>();
        Set<Long> pendientes = new java.util.HashSet<>();
        contexto.getPedidos().forEach(p -> pendientes.add(p.getIdPedido()));
        Map<String, Integer> stockReservado = new HashMap<>();

        for (Ruta ruta : solucion.getRutas()) {
            if (ruta.getSecuenciaPedidos().isEmpty()) {
                continue;
            }
            String idVehiculo = ruta.getVehiculo().getIdVehiculo();
            if (!ruta.getVehiculo().isDisponible() || planes.containsKey(idVehiculo)) {
                continue;
            }
            if (ruta.isIntransitable() || ruta.isFueraDeTurno()
                    || ruta.cargaTotal() > ruta.getVehiculo().getCapacidadPaquetes()) {
                continue;
            }
            long nanosRuta = Math.max(0L, Math.round(ruta.getTiempoTotalHoras() * NANOS_POR_HORA));
            if (repositorio.tieneMantenimientoProgramado(idVehiculo, reloj,
                    reloj.plusNanos(nanosRuta))) {
                continue;
            }
            boolean valida = true;
            for (int i = 0; i < ruta.getSecuenciaPedidos().size(); i++) {
                Pedido pedido = ruta.getSecuenciaPedidos().get(i);
                if (!pendientes.contains(pedido.getIdPedido())
                        || pedidosAsignados.contains(pedido.getIdPedido())
                        || ruta.retrasoDe(i) > 1e-9) {
                    valida = false;
                    break;
                }
            }
            if (!valida) {
                continue;
            }
            Almacen origen = ruta.getAlmacenOrigen();
            if (origen.getCapacidadMaxima() != null) {
                int usado = stockReservado.getOrDefault(origen.getIdAlmacen(), 0);
                Integer disponible = origen.getStockActual();
                if (disponible == null || usado + ruta.cargaTotal() > disponible) {
                    continue;
                }
                stockReservado.put(origen.getIdAlmacen(), usado + ruta.cargaTotal());
            }

            PlanUnidad plan = PlanUnidad.desde(ruta, contexto);
            nuevos.add(plan);
            rutasAceptadas.add(ruta);
            for (Pedido pedido : ruta.getSecuenciaPedidos()) {
                pedidosAsignados.add(pedido.getIdPedido());
            }
        }

        if (nuevos.isEmpty()) {
            registrarDiagnosticosSinRuta(contexto.getPedidos(), "SIN_RUTA_ACEPTADA");
            mensajeEstado = solucion.getPedidosNoAsignados().isEmpty()
                    ? "No hubo rutas nuevas"
                    : "Hay pedidos pendientes sin vehículo/ruta factible";
            return;
        }

        Solucion ejecutable = new Solucion();
        ejecutable.setRutas(rutasAceptadas);
        objetivo.calcular(ejecutable);
        if (!ejecutable.isEsFactible() || ejecutable.getValorR() > 1e-9) {
            registrarDiagnosticosSinRuta(contexto.getPedidos(), "PLAN_RECHAZADO_POR_RESTRICCIONES");
            mensajeEstado = "La planificación incumple restricciones operativas";
            return;
        }
        registrarDiagnosticosSinRuta(contexto.getPedidos().stream()
                .filter(p -> !pedidosAsignados.contains(p.getIdPedido())).toList(),
                "SIN_ASIGNACION_EN_CICLO");
        repositorio.guardarPlan(idSimulacion, reloj, ejecutable, rutasAceptadas,
                operativo.configuracion().numero("objetivo.beta1"),
                operativo.configuracion().numero("objetivo.beta2"),
                operativo.configuracion().numero("objetivo.beta3"));
        repositorio.marcarPedidosEnRuta(pedidosAsignados);
        for (PlanUnidad plan : nuevos) {
            repositorio.actualizarEstadoVehiculo(plan.idVehiculo, "EN_RUTA");
            planes.put(plan.idVehiculo, plan);
        }

        mensajeEstado = "Planificación generada: " + nuevos.size() + " rutas nuevas";
    }


    private ContextoPlanificacion contextoConPedidos(
            ContextoPlanificacion base, List<Pedido> pedidos) {
        List<Vehiculo> vehiculos = base.getVehiculos().stream().map(v -> {
            if (!planes.containsKey(v.getIdVehiculo())) return v;
            Vehiculo ocupado = new Vehiculo(v.getIdVehiculo(),
                    v.getCapacidadPaquetes(), v.getVelocidadKmh(),
                    v.getCostoPorKm(), v.getPosicionActual());
            ocupado.setDisponible(false);
            return ocupado;
        }).toList();
        return new ContextoPlanificacion(pedidos, vehiculos,
                base.getAlmacenes(), base.instante(), base.reglas(),
                base.nodosBloqueados(), base.respetarPosicionVehiculo(), base.calendarioBloqueos(), base.disponibleHasta());
    }

    /** Excluye los paquetes que siguen sobre una unidad, en rescate o en descarga. */
    private ContextoPlanificacion excluirPedidosYaComprometidos(ContextoPlanificacion base) {
        Set<Long> comprometidos = new LinkedHashSet<>();
        for (PlanUnidad plan : planes.values()) comprometidos.addAll(plan.idsNoEntregados());
        for (CargaAveriada carga : cargasAveriadas.values()) {
            for (Pedido pedido : carga.pendientes) comprometidos.add(pedido.getIdPedido());
            comprometidos.addAll(carga.reservados.keySet());
        }
        for (EntregaEnCliente entrega : entregasEnCliente) comprometidos.add(entrega.idPedido);
        Map<String,Integer> reservas = new HashMap<>();
        for (PlanUnidad plan : planes.values()) if (!plan.stockTomado && plan.origenRescate == null)
            reservas.merge(plan.almacenOrigen.getIdAlmacen(),plan.cargaInicial,Integer::sum);
        List<Almacen> libres = base.getAlmacenes().stream().map(a -> a.getCapacidadMaxima() == null ? a
                : new Almacen(a.getIdAlmacen(),a.getUbicacion(),a.getCapacidadMaxima(),
                    Math.max(0,a.getStockActual()-reservas.getOrDefault(a.getIdAlmacen(),0)))).toList();
        ContextoPlanificacion libre = new ContextoPlanificacion(base.getPedidos(),base.getVehiculos(),libres,
                base.instante(),base.reglas(),base.nodosBloqueados(),true,base.calendarioBloqueos(),base.disponibleHasta());
        return contextoConPedidos(libre, base.getPedidos().stream()
                .filter(p -> !comprometidos.contains(p.getIdPedido())).toList());
    }

    /**
     * La recuperación es exclusivamente operativa: no declara factible un SLA
     * ya incumplido ni modifica GA. Siempre preserva capacidades, vías, stock
     * y disponibilidad. Los vencidos se priorizan por su plazo original;
     * después GA atiende los pedidos que todavía pueden cumplir su SLA.
     */
    private void planificarRecuperacionVencidos(
            ContextoPlanificacion base, List<Pedido> vencidos, ContextoOperativo operativo) {
        if (vencidos.isEmpty()
                || configuracion.escenario() == EscenarioSimulacion.COLAPSO_LOGISTICO) return;

        ContextoPlanificacion actuales = contextoConPedidos(base, vencidos.stream()
                .filter(p -> planes.values().stream()
                        .noneMatch(plan -> plan.idsNoEntregados().contains(p.getIdPedido())))
                .toList());
        if (actuales.getPedidos().isEmpty()) return;

        Map<String, Integer> reservado = new HashMap<>();

        List<Ruta> rutas = RecuperacionPedidosVencidos.construir(
                actuales, planes.keySet(), reservado,
                ruta -> !repositorio.tieneMantenimientoProgramado(
                        ruta.getVehiculo().getIdVehiculo(), reloj,
                        reloj.plusNanos(Math.max(0L, Math.round(
                                ruta.getTiempoTotalHoras() * NANOS_POR_HORA)))));
        if (rutas.isEmpty()) {
            registrarDiagnosticosRecuperacion(actuales.getPedidos(), "RECUPERACION_SIN_RUTA_FISICA");
            return;
        }

        Set<Long> idsRecuperados = new LinkedHashSet<>();
        for (Ruta ruta : rutas) {
            for (Pedido pedido : ruta.getSecuenciaPedidos()) idsRecuperados.add(pedido.getIdPedido());
        }
        Solucion solucion = new Solucion();
        solucion.setRutas(rutas);
        ContextoPlanificacion contextoRecuperacion = contextoConPedidos(actuales,
                actuales.getPedidos().stream()
                        .filter(p -> idsRecuperados.contains(p.getIdPedido())).toList());
        new FuncionObjetivo(operativo.configuracion(), contextoRecuperacion).calcular(solucion);
        // Una ruta que rescata un pedido vencido no recupera su cumplimiento SLA.
        solucion.setEsFactible(false);

        repositorio.guardarPlan(idSimulacion, reloj, solucion, rutas,
                operativo.configuracion().numero("objetivo.beta1"),
                operativo.configuracion().numero("objetivo.beta2"),
                operativo.configuracion().numero("objetivo.beta3"));
        repositorio.marcarPedidosEnRuta(idsRecuperados);
        for (Ruta ruta : rutas) {
            PlanUnidad plan = PlanUnidad.desde(ruta, contextoRecuperacion);
            repositorio.actualizarEstadoVehiculo(plan.idVehiculo, "EN_RUTA");
            planes.put(plan.idVehiculo, plan);
            registrarEventoRecorrido(plan, "RECUPERACION_PEDIDO_VENCIDO");
        }
        registrarDiagnosticosRecuperacion(actuales.getPedidos().stream()
                .filter(p -> !idsRecuperados.contains(p.getIdPedido())).toList(),
                "RECUPERACION_SIN_ASIGNACION");
        firmaUltimaPlanificacion = null;
        mensajeEstado = "Recuperación: " + idsRecuperados.size()
                + " pedidos vencidos asignados sin ocultar el incumplimiento del plazo";
    }

    private void registrarDiagnosticosRecuperacion(List<Pedido> pedidos, String motivo) {
        if (idSimulacion == null || reloj == null || pedidos == null || pedidos.isEmpty()) return;
        Map<Long, DiagnosticoPlanificacion> registros = new LinkedHashMap<>();
        for (Pedido pedido : pedidos) {
            long visible = repositorio.idPedidoVisible(pedido.getIdPedido());
            registros.putIfAbsent(visible, new DiagnosticoPlanificacion(reloj, visible, motivo));
        }
        repositorio.registrarDiagnosticos(idSimulacion, new ArrayList<>(registros.values()));
    }

    private void registrarDiagnosticosSinRuta(List<Pedido> pedidos, String motivo) {
        if (idSimulacion == null || reloj == null || pedidos == null) return;
        Map<Long, DiagnosticoPlanificacion> registros = new LinkedHashMap<>();
        for (Pedido pedido : pedidos) {
            long restante = java.time.Duration.between(reloj, pedido.getFechaLimite()).toMinutes();
            if (restante > 240) continue;
            long idVisible = repositorio.idPedidoVisible(pedido.getIdPedido());
            registros.putIfAbsent(idVisible,
                    new DiagnosticoPlanificacion(reloj, idVisible, motivo));
        }
        if (!registros.isEmpty()) repositorio.registrarDiagnosticos(idSimulacion,
                new ArrayList<>(registros.values()));
    }

    /**
     * El siguiente conductor releva al anterior en la posición actual del
     * vehículo y sin consumir tiempo de simulación. El cambio de turno no
     * modifica la ruta, la carga, el inventario ni los pedidos a bordo.
     */
    private void relevarConductores() {
        proximaPlanificacionVirtual = reloj;
        nanoUltimaPlanificacion = 0L;
        firmaUltimaPlanificacion = null;
        if (!planes.isEmpty()) {
            for (PlanUnidad plan : planes.values()) registrarEventoRecorrido(plan, "CAMBIO_TURNO");
            mensajeEstado = "Cambio de turno: los vehículos en ruta continúan "
                    + "desde su posición actual con los pedidos a bordo";
        }
    }

    private void cancelarPlanesInterrumpidos(Map<String, VehiculoPersistido> estados) {
        Map<String, AveriaVehiculo> averias = repositorio.cargarAveriasActivas(reloj);
        for (PlanUnidad plan : new ArrayList<>(planes.values())) {
            VehiculoPersistido persistido = estados.get(plan.idVehiculo);
            if (persistido == null) {
                planes.remove(plan.idVehiculo);
                liberarReservaDeRescate(plan);
                if (plan.stockTomado) {
                    repositorio.reencolarPedidos(plan.idsNoEntregados(), reloj);
                }
                continue;
            }
            if ("EN_AVERIA".equals(persistido.estado())) {
                AveriaVehiculo averia = averias.get(plan.idVehiculo);
                if (averia == null) {
                    throw new IllegalStateException(
                            "No se encontró la avería activa de " + plan.idVehiculo);
                }
                interrumpirPorAveria(plan, averia);
            } else if ("EN_MANTENIMIENTO".equals(persistido.estado()) && !plan.retornoMantenimiento) {
                liberarReservaDeRescate(plan);
                if (!plan.stockTomado) {
                    planes.remove(plan.idVehiculo);
                    if (plan.origenRescate == null) repositorio.reencolarPedidos(plan.idsNoEntregados(), reloj);
                } else {
                    if (plan.fase == FaseUnidad.ATENDIENDO) {
                        for (Pedido entrega : plan.entregasEnParada)
                            entregasEnCliente.add(new EntregaEnCliente(entrega.getIdPedido(), plan.servicioRestanteHoras));
                        plan.entregasEnParada.clear(); plan.pedidoObjetivo = null;
                    } else if (plan.pedidoObjetivo != null) {
                        plan.pedidosPendientes.addFirst(plan.pedidoObjetivo); plan.pedidoObjetivo = null;
                    }
                    var c = repositorio.cargarContexto(perfil,reloj,configuracion.fechaHoraFin()).contexto();
                    plan.almacenRetorno = c.getAlmacenes().stream().filter(a -> a.getCapacidadMaxima()==null)
                            .findFirst().orElseThrow();
                    plan.retornoMantenimiento = true;
                    plan.fase = FaseUnidad.RETORNANDO; plan.destinoActual = null;
                    plan.camino.clear(); plan.salidasCamino.clear(); plan.creditoHoras = 0;
                    plan.trasvaseRestanteHoras = 0;
                }
                programarPlanificacionInmediata();
            }
        }
    }

    private void interrumpirPorAveria(PlanUnidad plan, AveriaVehiculo averia) {
        planes.remove(plan.idVehiculo);
        plan.creditoHoras = 0;
        plan.camino.clear();
            plan.salidasCamino.clear();
        plan.destinoActual = null;
        if (!plan.stockTomado) {
            liberarReservaDeRescate(plan);
            if (plan.origenRescate == null) {
                repositorio.reencolarPedidos(plan.idsNoEntregados(), reloj);
            }
            programarPlanificacionInmediata();
            return;
        }

        // Si ya llegó al cliente, la mercancía queda allí y solo resta atenderla.
        if (plan.fase == FaseUnidad.ATENDIENDO && plan.pedidoObjetivo != null) {
            for (Pedido entrega : plan.entregasEnParada) {
                entregasEnCliente.add(new EntregaEnCliente(
                        entrega.getIdPedido(), plan.servicioRestanteHoras));
            }
            plan.entregasEnParada.clear();
            plan.pedidoObjetivo = null;
            plan.servicioRestanteHoras = 0;
        }

        if (plan.idsNoEntregados().isEmpty()) {
            programarPlanificacionInmediata();
            return;
        }
        CargaAveriada carga = new CargaAveriada(averia, plan,
                new Nodo(plan.posicion.getX(), plan.posicion.getY()));
        cargasAveriadas.put(plan.idVehiculo, carga);
        programarPlanificacionInmediata();
    }

    private void liberarReservaDeRescate(PlanUnidad plan) {
        if (plan.origenRescate == null || plan.stockTomado) return;
        CargaAveriada carga = plan.origenRescate;
        for (Pedido pedido : plan.pedidosPendientes) {
            carga.reservados.remove(pedido.getIdPedido(), plan.idVehiculo);
        }
    }

    private void actualizarCargasAveriadas(Map<String, VehiculoPersistido> estados) {
        List<String> completadas = new ArrayList<>();
        for (CargaAveriada carga : new ArrayList<>(cargasAveriadas.values())) {
            if (carga.averia.tipoAveria() == TipoAveria.TIPO1_MENOR) {
                VehiculoPersistido v = estados.get(carga.averia.idVehiculo());
                if (v != null && "DISPONIBLE".equals(v.estado())) {
                    cancelarRecogidasEnCurso(carga);
                    if (!carga.pendientes.isEmpty()) {
                        PlanUnidad original = carga.planSuspendido;
                        original.pedidosPendientes.clear();
                        original.pedidosPendientes.addAll(carga.pendientes);
                        original.pedidoObjetivo = null;
                        original.entregasEnParada.clear();
                        original.fase = FaseUnidad.IR_PEDIDO;
                        original.destinoActual = null;
                        original.camino.clear();
                        original.salidasCamino.clear();
                        original.creditoHoras = 0;
                        planes.put(original.idVehiculo, original);
                        repositorio.actualizarEstadoVehiculo(original.idVehiculo, "EN_RUTA");
                    }
                    completadas.add(carga.averia.idVehiculo());
                    programarPlanificacionInmediata();
                }
            } else if (!carga.trasladadaAlCentral
                    && !reloj.isBefore(carga.averia.fechaOcurrencia().plusHours(4))) {
                cancelarRecogidasEnCurso(carga);
                ContextoOperativo operativo = repositorio.cargarContexto(
                        perfil, reloj, configuracion.fechaHoraFin());
                Almacen central = operativo.contexto().getAlmacenes().stream()
                        .filter(a -> a.getCapacidadMaxima() == null)
                        .findFirst()
                        .orElseThrow(() -> new IllegalStateException("No existe almacén central"));
                carga.ubicacionCarga = central.getUbicacion();
                carga.trasladadaAlCentral = true;
                programarPlanificacionInmediata();
            }
            if (carga.pendientes.isEmpty() && carga.reservados.isEmpty()) {
                completadas.add(carga.averia.idVehiculo());
            }
        }
        for (String id : completadas) cargasAveriadas.remove(id);
    }

    private void cancelarRecogidasEnCurso(CargaAveriada carga) {
        for (PlanUnidad plan : new ArrayList<>(planes.values())) {
            if (plan.origenRescate != carga || plan.stockTomado) continue;
            planes.remove(plan.idVehiculo);
            liberarReservaDeRescate(plan);
            repositorio.actualizarEstadoVehiculo(plan.idVehiculo, "DISPONIBLE");
        }
        programarPlanificacionInmediata();
    }

    private void actualizarEntregasEnCliente(double horasEfectivas) {
        if (horasEfectivas <= 0) return;
        for (EntregaEnCliente entrega : new ArrayList<>(entregasEnCliente)) {
            entrega.horasRestantes -= horasEfectivas;
            if (entrega.horasRestantes <= 1e-9) {
                repositorio.entregarPedido(entrega.idPedido, reloj);
                entregasEnCliente.remove(entrega);
            }
        }
    }

    private int planificarRescates(ContextoPlanificacion contexto) {
        return planificarRescates(contexto, false);
    }

    private int planificarRescates(ContextoPlanificacion contexto, boolean recuperarVencidos) {
        if (cargasAveriadas.isEmpty()) return 0;
        List<Vehiculo> disponibles = contexto.getVehiculos().stream()
                .filter(v -> v.isDisponible() && !planes.containsKey(v.getIdVehiculo())).toList();
        int capacidad = disponibles.stream().mapToInt(Vehiculo::getCapacidadPaquetes).min().orElse(0);
        if (capacidad == 0) return 0;
        int creados = 0;
        for (CargaAveriada carga : cargasAveriadas.values()) {
            for (int i=0; i<carga.pendientes.size(); i++) {
                Pedido pedido=carga.pendientes.get(i);
                if (pedido.getCantidadQq()<=capacidad || carga.reservados.containsKey(pedido.getIdPedido())) continue;
                List<Pedido> partes=repositorio.dividirPedidoEnRuta(pedido,capacidad);
                carga.pendientes.remove(i); carga.pendientes.addAll(i,partes); i+=partes.size()-1;
            }
            List<Pedido> candidatos=carga.pendientes.stream()
                    .filter(p -> !carga.reservados.containsKey(p.getIdPedido()))
                    .filter(p -> recuperarVencidos == p.getFechaLimite().isBefore(reloj))
                    .sorted(Comparator.comparing(Pedido::getFechaLimite).thenComparing(Pedido::getIdPedido)).toList();
            if(candidatos.isEmpty()) continue;
            var punto=new Almacen("RESCATE",carga.ubicacionCarga,null,null);
            for(Vehiculo v:disponibles) {
                if(planes.containsKey(v.getIdVehiculo())) continue;
                var recogida=pe.edu.pucp.sisrap.planificador.dominio.modelo.DistanciaReticula.trayectoTemporal(
                        v.getPosicionActual(),carga.ubicacionCarga,null,reloj,v.getVelocidadKmh(),
                        contexto.reglas().distanciaNodoKm(),bloqueados,calendarioBloqueos,
                        contexto.reglas().anchoCiudad(),contexto.reglas().altoCiudad());
                if(recogida.isEmpty()) continue;
                LocalDateTime salida=JornadaOperativa.avanzar(recogida.get().llegada(),0.5);
                LocalDateTime limite=carga.averia.tipoAveria()==TipoAveria.TIPO1_MENOR
                        ? carga.averia.horaRetornoEstimada() : carga.trasladadaAlCentral ? null
                        : carga.averia.fechaOcurrencia().plusHours(4);
                if(limite!=null && salida.isAfter(limite)) continue;
                Ruta ruta=new Ruta(v,punto,v.getPosicionActual(),true,reloj,contexto.reglas());
                ruta.setNodosBloqueados(bloqueados); ruta.setCalendarioBloqueos(calendarioBloqueos);
                ruta.setTiempoCargaHoras(0.5);
                ruta.setDisponibleHasta(contexto.disponibleHasta().get(v.getIdVehiculo()));
                ruta.setAlmacenRetorno(carga.planSuspendido.almacenRetorno);
                for(Pedido pedido:candidatos) {
                    if(carga.reservados.containsKey(pedido.getIdPedido())
                            || ruta.cargaTotal()+pedido.getCantidadQq()>v.getCapacidadPaquetes()) continue;
                    Ruta prueba=ruta.copiar(); prueba.getSecuenciaPedidos().add(pedido); prueba.recalcular();
                    if(prueba.isIntransitable()) continue;
                    boolean puntual=true;
                    if(!recuperarVencidos) for(int i=0;i<prueba.getSecuenciaPedidos().size();i++)
                        if(prueba.retrasoDe(i)>0) { puntual=false; break; }
                    if(puntual) ruta=prueba;
                }
                if(ruta.getSecuenciaPedidos().isEmpty()) continue;
                List<Pedido> asignados=List.copyOf(ruta.getSecuenciaPedidos());
                PlanUnidad rescate=PlanUnidad.desdeRescate(v,carga,asignados,contexto);
                for(Pedido pedido:asignados) carga.reservados.put(pedido.getIdPedido(),rescate.idVehiculo);
                planes.put(rescate.idVehiculo,rescate);
                repositorio.actualizarEstadoVehiculo(rescate.idVehiculo,"EN_RUTA");
                creados++;
            }
        }
        if(creados>0) mensajeEstado="Reasignación por avería: "+creados+" vehículos enviados a recoger paquetes";
        return creados;
    }

    private void programarPlanificacionInmediata() {
        firmaUltimaPlanificacion = null;
        proximaPlanificacionVirtual = reloj;
        nanoUltimaPlanificacion = 0;
    }

    private boolean actualizarBloqueosActivos() {
        Set<String> actuales = repositorio.cargarNodosBloqueados(reloj);
        if (actuales.equals(bloqueados)) {
            return false;
        }
        bloqueados = actuales;
        invalidarCaminosBloqueados();
        proximaPlanificacionVirtual = reloj;
        nanoUltimaPlanificacion = 0L;
        firmaUltimaPlanificacion = null;
        return true;
    }

    private void invalidarCaminosBloqueados() {
        for (PlanUnidad plan : planes.values()) {
            if (plan.destinoActual == null || mismaPosicion(plan.posicion, plan.destinoActual)) {
                continue;
            }
            plan.camino.clear();
            plan.salidasCamino.clear();

            registrarEventoRecorrido(plan, "DESVIO_BLOQUEO");
        }
    }

    private void actualizarPlanes(double deltaHoras) {
        if (deltaHoras <= 0 || planes.isEmpty()) {
            return;
        }

        List<String> finalizados = new ArrayList<>();

        for (PlanUnidad plan : new ArrayList<>(planes.values())) {
            plan.creditoHoras += deltaHoras;

            int guardia = 0;
            while (plan.creditoHoras > 0 && guardia++ < 10_000) {
                if (plan.trasvaseRestanteHoras > 0) {
                    double usado = Math.min(plan.creditoHoras, plan.trasvaseRestanteHoras);
                    plan.trasvaseRestanteHoras -= usado; plan.creditoHoras -= usado;
                    if (plan.trasvaseRestanteHoras > 1e-12) break;
                    plan.trasvaseRestanteHoras = 0;
                }
                if (plan.fase == FaseUnidad.ATENDIENDO) {
                    if (plan.servicioRestanteHoras > plan.creditoHoras) {
                        plan.servicioRestanteHoras -= plan.creditoHoras;
                        plan.creditoHoras = 0;
                        break;
                    }

                    plan.creditoHoras -= plan.servicioRestanteHoras;
                    plan.servicioRestanteHoras = 0;
                    for (Pedido entregado : plan.entregasEnParada) {
                        repositorio.entregarPedido(entregado.getIdPedido(), instanteEvento(plan));
                    }
                    plan.entregasEnParada.clear();
                    plan.pedidoObjetivo = null;
                    prepararDespuesDeEntrega(plan, finalizados);
                    if (finalizados.contains(plan.idVehiculo)) {
                        break;
                    }
                    continue;
                }

                if (plan.destinoActual == null) {
                    if (!prepararDestinoSegunFase(plan, finalizados)) {
                        break;
                    }
                    if (finalizados.contains(plan.idVehiculo)) {
                        break;
                    }
                }

                if (mismaPosicion(plan.posicion, plan.destinoActual)) {
                    procesarLlegada(plan, finalizados);
                    if (finalizados.contains(plan.idVehiculo)) {
                        break;
                    }
                    continue;
                }

                if (plan.camino.isEmpty()) {
                    var tramo=pe.edu.pucp.sisrap.planificador.dominio.modelo.DistanciaReticula.trayectoTemporal(
                            plan.posicion,plan.destinoActual,plan.posicionAnterior,instanteEvento(plan),
                            plan.velocidadKmh,plan.distanciaNodoKm,bloqueados,calendarioBloqueos,plan.ancho,plan.alto);
                    plan.salidasCamino.clear();
                    if(tramo.isEmpty()) { plan.creditoHoras=0; break; }
                    plan.camino.addAll(tramo.get().nodos());
                    plan.salidasCamino.addAll(tramo.get().salidas());
                }
                if(!plan.salidasCamino.isEmpty()) {
                    LocalDateTime salida=plan.salidasCamino.peekFirst();
                    LocalDateTime disponible=instanteEvento(plan);
                    if(salida.isAfter(disponible)) {
                        if(salida.isAfter(reloj)) { plan.creditoHoras=0; break; }
                        double espera=JornadaOperativa.horasEfectivas(disponible,salida);
                        plan.creditoHoras=Math.max(0,plan.creditoHoras-espera);
                    }
                }

                double horasPorNodo = plan.distanciaNodoKm / plan.velocidadKmh;
                if (plan.creditoHoras + 1e-12 < horasPorNodo) {
                    break;
                }

                Nodo siguiente = plan.camino.peekFirst();
                if (siguiente == null || !ArcoReticula.transitableComoExtremo(
                        plan.posicion.getX(), plan.posicion.getY(),
                        siguiente.getX(), siguiente.getY(), bloqueados,
                        true, mismaPosicion(siguiente, plan.destinoActual))
                        || (bloqueados.contains(CaminoReticula.clave(
                                plan.posicion.getX(), plan.posicion.getY()))
                        && plan.posicionAnterior != null
                        && !mismaPosicion(siguiente, plan.posicionAnterior))) {
                    plan.camino.clear();
            plan.salidasCamino.clear();
                    plan.creditoHoras = 0;
                    break;
                }

                plan.creditoHoras -= horasPorNodo;
                plan.camino.removeFirst();
                if(!plan.salidasCamino.isEmpty()) plan.salidasCamino.removeFirst();
                plan.posicionAnterior = plan.posicion;
                plan.posicion = siguiente;
                if (plan.reoptimizarEnSiguienteNodo) {
                    plan.reoptimizarEnSiguienteNodo = false; programarPlanificacionInmediata();
                }
                indicadores.registrarTramo(plan.idVehiculo, plan.distanciaNodoKm);
                registrarMovimientoRecorrido(plan);

                if (plan.camino.isEmpty() && mismaPosicion(plan.posicion, plan.destinoActual)) {
                    procesarLlegada(plan, finalizados);
                    if (finalizados.contains(plan.idVehiculo)) {
                        break;
                    }
                }
            }
        }

        Map<String, PuntoSimulacion> posicionesFinales = new HashMap<>();
        for (String id : finalizados) {
            PlanUnidad terminado = planes.remove(id);
            if (terminado != null) {
                PuntoSimulacion posicion = new PuntoSimulacion(terminado.posicion.getX(), terminado.posicion.getY());
                if (!posicion.equals(posicionesPersistidas.get(id))) posicionesFinales.put(id, posicion);
            }
        }
        if (!posicionesFinales.isEmpty()) {
            repositorio.actualizarPosiciones(posicionesFinales);
            posicionesPersistidas.putAll(posicionesFinales);
        }
    }

    private boolean prepararDestinoSegunFase(
            PlanUnidad plan,
            List<String> finalizados) {

        return switch (plan.fase) {
            case IR_ALMACEN -> {
                plan.destinoActual = plan.almacenOrigen.getUbicacion();
                yield true;
            }
            case IR_RECOGER -> {
                plan.destinoActual = plan.origenRescate.ubicacionCarga;
                yield true;
            }
            case IR_PEDIDO -> {
                if (plan.pedidoObjetivo == null) {
                    if (plan.pedidosPendientes.isEmpty()) {
                        prepararRetornoOFinalizar(plan, finalizados);
                        yield false;
                    }
                    plan.pedidoObjetivo = plan.pedidosPendientes.removeFirst();
                }
                plan.destinoActual = plan.pedidoObjetivo.getUbicacion();
                yield true;
            }
            case RETORNANDO -> {
                plan.destinoActual = plan.almacenRetorno.getUbicacion();
                yield true;
            }
            case ATENDIENDO -> false;
        };
    }

    private void procesarLlegada(
            PlanUnidad plan,
            List<String> finalizados) {

        plan.camino.clear();
            plan.salidasCamino.clear();
        plan.destinoActual = null;

        switch (plan.fase) {
            case IR_ALMACEN -> {
                if (!plan.stockTomado) {
                    boolean ok = repositorio.consumirStock(
                            plan.almacenOrigen.getIdAlmacen(),
                            plan.cargaInicial);
                    if (!ok) {
                        repositorio.reencolarPedidos(plan.idsNoEntregados(), reloj);
                        repositorio.actualizarEstadoVehiculo(plan.idVehiculo, "DISPONIBLE");
                        finalizados.add(plan.idVehiculo);
                        mensajeEstado = "Ruta cancelada por stock insuficiente en "
                                + plan.almacenOrigen.getIdAlmacen();
                        proximaPlanificacionVirtual = reloj.plusMinutes(1);
                        return;
                    }
                    plan.stockTomado = true;
                }
                plan.fase = FaseUnidad.IR_PEDIDO;
            }
            case IR_RECOGER -> {
                CargaAveriada carga = plan.origenRescate;
                for (Pedido pedido : plan.pedidosPendientes) {
                    if (!carga.pendientes.remove(pedido)) {
                        throw new IllegalStateException(
                                "El paquete ya no está en el punto de trasvase: "
                                        + pedido.getIdPedido());
                    }
                    carga.reservados.remove(pedido.getIdPedido());
                }
                List<Pedido> recogidos = repositorio.registrarTrasvase(
                        new ArrayList<>(plan.pedidosPendientes), plan.idVehiculo);
                plan.pedidosPendientes.clear();
                plan.pedidosPendientes.addAll(recogidos);
                plan.trasvaseRestanteHoras = 0.5;
                plan.stockTomado = true;
                plan.fase = FaseUnidad.IR_PEDIDO;
            }
            case IR_PEDIDO -> {
                // Varias fracciones del mismo cliente se descargan en una sola parada.
                plan.entregasEnParada.clear();
                plan.entregasEnParada.add(plan.pedidoObjetivo);
                while (!plan.pedidosPendientes.isEmpty()) {
                    Pedido siguiente = plan.pedidosPendientes.peekFirst();
                    if (!mismaPosicion(siguiente.getUbicacion(),
                            plan.pedidoObjetivo.getUbicacion())
                            || !siguiente.getIdCliente().equals(
                                    plan.pedidoObjetivo.getIdCliente())) break;
                    plan.entregasEnParada.add(plan.pedidosPendientes.removeFirst());
                }
                for (Pedido entrega : plan.entregasEnParada) {
                    repositorio.registrarArribo(entrega.getIdPedido(), instanteEvento(plan));
                }
                plan.fase = FaseUnidad.ATENDIENDO;
                plan.servicioRestanteHoras = plan.servicioHoras;
                if (plan.servicioRestanteHoras <= 0) {
                    for (Pedido entrega : plan.entregasEnParada) {
                        repositorio.entregarPedido(entrega.getIdPedido(), instanteEvento(plan));
                    }
                    plan.entregasEnParada.clear();
                    plan.pedidoObjetivo = null;
                    prepararDespuesDeEntrega(plan, finalizados);
                }
            }
            case RETORNANDO -> finalizarPlan(plan, finalizados);
            case ATENDIENDO -> {
                // No aplica.
            }
        }
    }

    private void prepararDespuesDeEntrega(
            PlanUnidad plan,
            List<String> finalizados) {
        if (!plan.pedidosPendientes.isEmpty()) {
            plan.fase = FaseUnidad.IR_PEDIDO;
            plan.destinoActual = null;
            return;
        }
        prepararRetornoOFinalizar(plan, finalizados);
    }

    private void prepararRetornoOFinalizar(
            PlanUnidad plan,
            List<String> finalizados) {
        if(plan.incluirRetorno) {
            ContextoPlanificacion c=repositorio.cargarContexto(perfil,reloj,configuracion.fechaHoraFin()).contexto();
            Almacen elegido=null; LocalDateTime mejor=null;
            for(Almacen a:c.getAlmacenes()) {
                if(!a.tieneStockDisponible()) continue;
                var t=pe.edu.pucp.sisrap.planificador.dominio.modelo.DistanciaReticula.trayectoTemporal(
                        plan.posicion,a.getUbicacion(),plan.posicionAnterior,reloj,plan.velocidadKmh,
                        plan.distanciaNodoKm,bloqueados,calendarioBloqueos,plan.ancho,plan.alto);
                if(t.isPresent() && (mejor==null || t.get().llegada().isBefore(mejor))) {
                    elegido=a;mejor=t.get().llegada();
                }
            }
            if(elegido!=null) plan.almacenRetorno=elegido;
        }
        if (plan.incluirRetorno
                && !mismaPosicion(plan.posicion, plan.almacenRetorno.getUbicacion())) {
            plan.fase = FaseUnidad.RETORNANDO;
            plan.destinoActual = null;
            return;
        }
        finalizarPlan(plan, finalizados);
    }

    private void finalizarPlan(
            PlanUnidad plan,
            List<String> finalizados) {
        if (plan.retornoMantenimiento) {
            repositorio.reencolarPedidos(plan.idsNoEntregados(), reloj);
            if (plan.stockTomado) repositorio.devolverStock(plan.almacenRetorno.getIdAlmacen(), plan.cantidadNoEntregada());
        } else repositorio.actualizarEstadoVehiculo(plan.idVehiculo, "DISPONIBLE");
        finalizados.add(plan.idVehiculo);
        proximaPlanificacionVirtual = reloj;
        firmaUltimaPlanificacion = null;
    }

    /** Reconstruye el instante virtual del evento dentro del tick de simulación. */
    private LocalDateTime instanteEvento(PlanUnidad plan) {
        long restante = Math.max(0L, Math.round(plan.creditoHoras * NANOS_POR_HORA));
        LocalDateTime estimado = reloj;
        while (restante > 0) {
            LocalDateTime turno = JornadaOperativa.inicioTurno(estimado.minusNanos(1));
            LocalDateTime inicioComida = turno.plusHours(4), finComida = turno.plusHours(5);
            if (estimado.isAfter(inicioComida) && !estimado.isAfter(finComida)) {
                estimado = inicioComida; continue;
            }
            LocalDateTime limite = estimado.isAfter(finComida) ? finComida : turno;
            long disponibles = Duration.between(limite, estimado).toNanos();
            long usado = Math.min(restante, disponibles);
            estimado = estimado.minusNanos(usado); restante -= usado;
        }
        return estimado.isBefore(configuracion.fechaHoraInicio())
                ? configuracion.fechaHoraInicio() : estimado;
    }

    private void persistirPosiciones() {
        if (planes.isEmpty()) {
            return;
        }
        Map<String, PuntoSimulacion> posiciones = new HashMap<>();
        for (PlanUnidad plan : planes.values()) {
            PuntoSimulacion posicion = new PuntoSimulacion(plan.posicion.getX(), plan.posicion.getY());
            if (!posicion.equals(posicionesPersistidas.get(plan.idVehiculo)))
                posiciones.put(plan.idVehiculo, posicion);
        }
        if (!posiciones.isEmpty()) {
            repositorio.actualizarPosiciones(posiciones);
            posicionesPersistidas.putAll(posiciones);
        }
    }

    private void finalizar(boolean natural, String mensaje) {
        registrarEstadoRecorrido("FIN");
        vaciarRecorridos();
        // Cierre de observación: los pedidos, las cargas y los vehículos conservan
        // exactamente el estado físico del instante de corte. Una reasignación
        // solamente ocurre si hubo una operación logística real de reasignación.
        // La siguiente corrida regenerará su flota y limpiará datos operativos
        // después de archivar la ejecución actual.
        planes.clear();
        cargasAveriadas.clear();
        entregasEnCliente.clear();

        estado = natural ? EstadoSimulacion.FINALIZADA : EstadoSimulacion.DETENIDA;
        mensajeEstado = mensaje;

        long tiempoMs = Math.max(
                0L,
                TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - nanoInicioReal));

        repositorio.finalizarEjecucion(
                idSimulacion,
                estado,
                reloj,
                tiempoMs);

        /*
         * El propietario se libera automáticamente al terminar la ejecución.
         * No existe lease ni endpoint público de liberación.
         */
        control.liberarInternamente();
        publicarSnapshot(true);
    }

    private void fallar(Throwable error) {
        if (estado == EstadoSimulacion.DETENIDA
                || estado == EstadoSimulacion.FINALIZADA
                || estado == EstadoSimulacion.ERROR) {
            return;
        }

        try {
            registrarEstadoRecorrido("ERROR");
            vaciarRecorridos();
        } catch (Exception ignored) {
            // El registro de error no debe ocultar el error original.
        }
        estado = EstadoSimulacion.ERROR;
        mensajeEstado = "Error de simulación: " + mensaje(error);

        try {
            // Ante un error se archiva el último estado consistente conocido;
            // los pedidos no se marcan artificialmente como reasignados.
            planes.clear();
            cargasAveriadas.clear();
            entregasEnCliente.clear();

            if (idSimulacion != null) {
                long tiempoMs = Math.max(
                        0L,
                        TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - nanoInicioReal));
                repositorio.finalizarEjecucion(
                        idSimulacion,
                        estado,
                        reloj,
                        tiempoMs);
            }
        } catch (Exception ignored) {
            // Se conserva el error original en mensajeEstado.
        } finally {
            /*
             * Ante un error, el control también se libera para no bloquear
             * futuras ejecuciones.
             */
            control.liberarInternamente();
        }

        publicarSnapshot(true);
    }

    /** Registra cada nodo alcanzado, no solamente el último plan generado. */
    private void registrarEventoRecorrido(PlanUnidad plan, String evento) {
        registrarPuntoRecorrido(plan.idVehiculo, reloj, plan.posicion.getX(),
                plan.posicion.getY(), "EN_RUTA", plan.fase.name(), evento,
                plan.stockTomado ? plan.cantidadNoEntregada() : 0,
                plan.pedidoObjetivo == null ? null : plan.pedidoObjetivo.getIdPedido(), 0, false);
    }

    private void registrarMovimientoRecorrido(PlanUnidad plan) {
        long secuencia = secuenciasVisuales.merge(plan.idVehiculo, 1L, Long::sum);
        Deque<VehiculoSnapshot.PasoVisual> pasos = pasosVisuales.computeIfAbsent(
                plan.idVehiculo, id -> new ArrayDeque<>());
        pasos.addLast(new VehiculoSnapshot.PasoVisual(secuencia, plan.posicion.getX(), plan.posicion.getY()));
        while (pasos.size() > MAX_PASOS_VISUALES) pasos.removeFirst();
        registrarPuntoRecorrido(plan.idVehiculo, instanteEvento(plan), plan.posicion.getX(),
                plan.posicion.getY(), "EN_RUTA", plan.fase.name(), "MOVIMIENTO",
                plan.stockTomado ? plan.cantidadNoEntregada() : 0,
                plan.pedidoObjetivo == null ? null : plan.pedidoObjetivo.getIdPedido(),
                plan.distanciaNodoKm, true);
    }

    /** Conserva estados en reposo, tiempos de servicio, mantenimiento y cambios de plan. */
    private void registrarEstadoRecorrido(String evento) {
        if (idSimulacion == null || reloj == null) return;
        registrarEstadoRecorrido(evento, repositorio.cargarVehiculosPersistidos());
    }

    private void registrarEstadoRecorrido(String evento, Map<String, VehiculoPersistido> vehiculos) {
        if (idSimulacion == null || reloj == null) return;
        for (VehiculoPersistido vehiculo : vehiculos.values()) {
            PlanUnidad plan = planes.get(vehiculo.idVehiculo());
            CargaAveriada averiada = cargasAveriadas.get(vehiculo.idVehiculo());
            int x = plan == null ? vehiculo.x() : plan.posicion.getX();
            int y = plan == null ? vehiculo.y() : plan.posicion.getY();
            String fase = plan != null ? plan.fase.name()
                    : averiada != null ? "AVERIADO" : vehiculo.estado();
            int carga = plan != null ? (plan.stockTomado ? plan.cantidadNoEntregada() : 0)
                    : averiada != null && !averiada.trasladadaAlCentral
                        ? averiada.pendientes.stream().mapToInt(Pedido::getCantidadQq).sum() : 0;
            Long objetivo = plan == null || plan.pedidoObjetivo == null
                    ? null : plan.pedidoObjetivo.getIdPedido();
            registrarPuntoRecorrido(vehiculo.idVehiculo(), reloj, x, y,
                    plan != null && !plan.retornoMantenimiento ? "EN_RUTA" : vehiculo.estado(),
                    fase, evento, carga, objetivo, 0, false);
        }
    }

    private void registrarPuntoRecorrido(String vehiculo, LocalDateTime instante, int x, int y,
                                        String estado, String fase, String evento,
                                        int carga, Long objetivo, double distancia, boolean movimiento) {
        RecorridoVehiculo.Punto anterior = ultimosPuntos.get(vehiculo);
        if (anterior != null) {
            if (instante.isBefore(anterior.instante())) instante = anterior.instante();
            boolean igual = anterior.x() == x && anterior.y() == y
                    && anterior.estado().equals(estado) && anterior.fase().equals(fase)
                    && anterior.cargaPaquetes() == carga
                    && java.util.Objects.equals(anterior.pedidoObjetivo(), objetivo);
            long intervaloReposoMin = configuracion != null
                    && configuracion.escenario() == EscenarioSimulacion.COLAPSO_LOGISTICO
                    ? 60 : INTERVALO_PUNTO_ESTACIONARIO_MIN;
            boolean reciente = java.time.Duration.between(anterior.instante(), instante)
                    .toMinutes() < intervaloReposoMin;
            if (!movimiento && "ESTADO".equals(evento) && igual && reciente) return;
        }
        RecorridoVehiculo.Punto punto = new RecorridoVehiculo.Punto(instante, x, y,
                estado, fase, evento, Math.max(0, carga), objetivo, distancia);
        ultimosPuntos.put(vehiculo, punto);
        registrosRecorrido.add(new RecorridoVehiculo.Registro(vehiculo, punto));
        if (registrosRecorrido.size() >= MAX_LOTE_RECORRIDO) vaciarRecorridos();
    }

    /** El historial es auxiliar: un fallo de archivo no debe detener el reparto. */
    private void vaciarRecorridos() {
        if (registrosRecorrido.isEmpty() || idSimulacion == null) return;
        long ahora = System.nanoTime();
        if (ahora < nanoProximoReintentoRecorrido) return;
        try {
            // Se quita el lote de memoria solamente después del COMMIT en MySQL.
            repositorio.guardarRecorridos(idSimulacion, registrosRecorrido);
            registrosRecorrido.clear();
            nanoProximoReintentoRecorrido = 0L;
            advertenciaRecorrido = puntosPerdidosRecorrido == 0 ? null
                    : "Historial de vehículos incompleto: " + puntosPerdidosRecorrido
                    + " puntos no pudieron archivarse";
        } catch (RuntimeException e) {
            // Los puntos se conservan para volver a intentarlo sin generar duplicados.
            nanoProximoReintentoRecorrido = ahora + REINTENTO_RECORRIDO_NS;
            if (nanoUltimoLogErrorRecorrido == 0L
                    || ahora - nanoUltimoLogErrorRecorrido >= LOG_RECORRIDO_NS) {
                LOG.error("Corrida {}: fallo al guardar {} puntos del recorrido; "
                        + "se reintentará sin detener la simulación",
                        idSimulacion, registrosRecorrido.size(), e);
                nanoUltimoLogErrorRecorrido = ahora;
            }
            if (registrosRecorrido.size() > MAX_RECORRIDOS_PENDIENTES) {
                int exceso = registrosRecorrido.size() - MAX_RECORRIDOS_PENDIENTES;
                registrosRecorrido.subList(0, exceso).clear();
                puntosPerdidosRecorrido += exceso;
                LOG.error("Corrida {}: se perdieron {} puntos de recorrido por falta prolongada "
                        + "de persistencia. Total no archivado: {}",
                        idSimulacion, exceso, puntosPerdidosRecorrido);
            }
            advertenciaRecorrido = "Aviso: historial de vehículos pendiente de guardar ("
                    + registrosRecorrido.size() + " puntos en espera; reintentando)"
                    + (puntosPerdidosRecorrido == 0 ? ""
                        : "; " + puntosPerdidosRecorrido + " puntos no archivados");
        }
    }

    private void publicarSnapshot(boolean forzar) {
        long ahora = System.nanoTime();
        if (!forzar && ahora - nanoUltimaPublicacion < 500_000_000L) return;
        if (configuracion == null || reloj == null) {
            snapshotActual = SnapshotSimulacion.detenida(ALGORITMO_OPERATIVO, perfil);
            sse.publicar(snapshotActual);
            return;
        }

        var resumen = repositorio.resumirPedidos(
                configuracion.fechaHoraInicio(),
                configuracion.fechaHoraFin(),
                reloj);
        int cantidadBloqueos = repositorio.contarBloqueosActivos(reloj);

        List<VehiculoSnapshot> base = repositorio.cargarVehiculosSnapshot();
        Map<String, VehiculoSnapshot> porId = new LinkedHashMap<>();
        for (VehiculoSnapshot v : base) {
            porId.put(v.idVehiculo(), v);
        }

        for (PlanUnidad plan : planes.values()) {
            VehiculoSnapshot persistido = porId.get(plan.idVehiculo);
            String estadoVehiculo = persistido == null ? "EN_RUTA" : persistido.estado();
            int paquetesPendientes = plan.cantidadNoEntregada();
            int cargaActual = plan.stockTomado ? paquetesPendientes : 0;
            int porRecoger = plan.stockTomado ? 0 : paquetesPendientes;
            int capacidad = persistido == null ? 0 : persistido.capacidadPaquetes();
            // El crédito solo expresa avance sobre el próximo tramo ya calculado.
            double xVisual = plan.posicion.getX(), yVisual = plan.posicion.getY();
            Nodo siguiente = plan.camino.peekFirst();
            if (siguiente != null && plan.fase != FaseUnidad.ATENDIENDO
                    && plan.trasvaseRestanteHoras <= 0 && plan.creditoHoras > 0
                    && ArcoReticula.transitableComoExtremo(plan.posicion.getX(), plan.posicion.getY(),
                            siguiente.getX(), siguiente.getY(), bloqueados, true,
                            plan.destinoActual != null && mismaPosicion(siguiente, plan.destinoActual))) {
                double fraccion = Math.min(1, plan.creditoHoras * plan.velocidadKmh / plan.distanciaNodoKm);
                xVisual += (siguiente.getX() - plan.posicion.getX()) * fraccion;
                yVisual += (siguiente.getY() - plan.posicion.getY()) * fraccion;
            }
            porId.put(
                    plan.idVehiculo,
                    new VehiculoSnapshot(
                            plan.idVehiculo,
                            estadoVehiculo,
                            plan.posicion.getX(),
                            plan.posicion.getY(),
                            plan.pedidoObjetivo == null ? null
                                    : repositorio.idPedidoVisible(plan.pedidoObjetivo.getIdPedido()),
                            plan.camino.stream()
                                    .map(n -> new PuntoSimulacion(n.getX(), n.getY()))
                                    .toList(),
                            capacidad,
                            cargaActual,
                            porRecoger,
                            plan.idsNoEntregados().size(), xVisual, yVisual,
                            secuenciasVisuales.getOrDefault(plan.idVehiculo, 0L),
                            List.copyOf(pasosVisuales.getOrDefault(plan.idVehiculo, new ArrayDeque<>()))));
        }

        for (CargaAveriada carga : cargasAveriadas.values()) {
            VehiculoSnapshot averiado = porId.get(carga.averia.idVehiculo());
            if (averiado == null) continue;
            int paquetes = carga.pendientes.stream().mapToInt(Pedido::getCantidadQq).sum();
            int aBordo = carga.trasladadaAlCentral ? 0 : paquetes;
            porId.put(averiado.idVehiculo(), new VehiculoSnapshot(
                    averiado.idVehiculo(), averiado.estado(), averiado.x(), averiado.y(),
                    null, List.of(), averiado.capacidadPaquetes(), aBordo, 0,
                    carga.trasladadaAlCentral ? 0 : carga.pendientes.size()));
        }

        // Conservar el último tramo también al terminar una ruta entre dos envíos.
        porId.replaceAll((id, v) -> planes.containsKey(id) ? v : new VehiculoSnapshot(
                v.idVehiculo(), v.estado(), v.x(), v.y(), v.pedidoObjetivo(), v.caminoActual(),
                v.capacidadPaquetes(), v.cargaActualPaquetes(), v.cargaPorRecogerPaquetes(),
                v.entregasPendientes(), v.x(), v.y(), secuenciasVisuales.getOrDefault(id, 0L),
                List.copyOf(pasosVisuales.getOrDefault(id, new ArrayDeque<>()))));

        var indicadoresActuales = indicadores.obtener(reloj,
                new LinkedHashSet<>(planes.keySet()), resumen, true);

        snapshotActual = new SnapshotSimulacion(
                idSimulacion,
                estado,
                configuracion.escenario(),
                reloj,
                configuracion.fechaHoraInicio(),
                configuracion.fechaHoraFin(),
                ALGORITMO_OPERATIVO,
                perfil,
                resumen,
                cantidadBloqueos,
                new ArrayList<>(porId.values()),
                advertenciaRecorrido == null ? mensajeEstado
                        : (mensajeEstado == null || mensajeEstado.isBlank()
                                ? advertenciaRecorrido
                                : mensajeEstado + " | " + advertenciaRecorrido),
                indicadoresActuales);

        sse.publicar(snapshotActual);
        nanoUltimaPublicacion = ahora;
    }

    private void exigirEstado(EstadoSimulacion requerido, String mensaje) {
        if (estado != requerido) {
            throw new ConflictoSimulacionException(mensaje);
        }
    }

    private static boolean mismaPosicion(Nodo a, Nodo b) {
        return a.getX() == b.getX() && a.getY() == b.getY();
    }

    private static String clave(Nodo nodo) {
        return nodo.getX() + "," + nodo.getY();
    }

    private static String mensaje(Throwable error) {
        String m = error.getMessage();
        return m == null || m.isBlank() ? error.getClass().getSimpleName() : m;
    }

    private enum FaseUnidad {
        IR_ALMACEN,
        IR_RECOGER,
        IR_PEDIDO,
        ATENDIENDO,
        RETORNANDO
    }

    private static final class CargaAveriada {
        private final AveriaVehiculo averia;
        private final PlanUnidad planSuspendido;
        private final List<Pedido> pendientes = new ArrayList<>();
        private final Map<Long, String> reservados = new HashMap<>();
        private Nodo ubicacionCarga;
        private boolean trasladadaAlCentral;

        private CargaAveriada(AveriaVehiculo averia, PlanUnidad plan, Nodo ubicacion) {
            this.averia = averia;
            this.planSuspendido = plan;
            this.ubicacionCarga = ubicacion;
            if (plan.pedidoObjetivo != null) pendientes.add(plan.pedidoObjetivo);
            pendientes.addAll(plan.pedidosPendientes);
        }
    }

    private static final class EntregaEnCliente {
        private final long idPedido;
        private double horasRestantes;

        private EntregaEnCliente(long idPedido, double horasRestantes) {
            this.idPedido = idPedido;
            this.horasRestantes = horasRestantes;
        }
    }

    private static final class PlanUnidad {
        private final String idVehiculo;
        private final Almacen almacenOrigen;
        private Almacen almacenRetorno;
        private final Deque<Pedido> pedidosPendientes;
        private double velocidadKmh;
        private final double distanciaNodoKm;
        private final double servicioHoras;
        private final boolean incluirRetorno;
        private final int ancho;
        private final int alto;
        private final int cargaInicial;
        private CargaAveriada origenRescate;

        private Nodo posicion;
        // Nodo usado para entrar al último punto de la red; permite vuelta en U.
        private Nodo posicionAnterior;
        private FaseUnidad fase = FaseUnidad.IR_ALMACEN;
        private Nodo destinoActual;
        private Pedido pedidoObjetivo;
        private final List<Pedido> entregasEnParada = new ArrayList<>();
        private final Deque<Nodo> camino = new ArrayDeque<>();
        private final Deque<LocalDateTime> salidasCamino = new ArrayDeque<>();
        private double creditoHoras;
        private double trasvaseRestanteHoras;
        private boolean retornoMantenimiento;
        private boolean reoptimizarEnSiguienteNodo;
        private double servicioRestanteHoras;
        private boolean stockTomado;

        private PlanUnidad(
                String idVehiculo,
                Almacen almacenOrigen,
                Almacen almacenRetorno,
                Deque<Pedido> pedidosPendientes,
                double velocidadKmh,
                double distanciaNodoKm,
                double servicioHoras,
                boolean incluirRetorno,
                int ancho,
                int alto,
                int cargaInicial,
                Nodo posicion) {
            this.idVehiculo = idVehiculo;
            this.almacenOrigen = almacenOrigen;
            this.almacenRetorno = almacenRetorno;
            this.pedidosPendientes = pedidosPendientes;
            this.velocidadKmh = velocidadKmh;
            this.distanciaNodoKm = distanciaNodoKm;
            this.servicioHoras = servicioHoras;
            this.incluirRetorno = incluirRetorno;
            this.ancho = ancho;
            this.alto = alto;
            this.cargaInicial = cargaInicial;
            this.posicion = posicion;
        }

        private static PlanUnidad desde(Ruta ruta, ContextoPlanificacion contexto) {
            Deque<Pedido> pedidos = new ArrayDeque<>(ruta.getSecuenciaPedidos());
            int carga = ruta.getSecuenciaPedidos().stream()
                    .mapToInt(Pedido::getCantidadQq)
                    .sum();
            Nodo actual = ruta.getVehiculo().getPosicionActual();
            if (actual == null) {
                actual = ruta.getAlmacenOrigen().getUbicacion();
            }
            return new PlanUnidad(
                    ruta.getVehiculo().getIdVehiculo(),
                    ruta.getAlmacenOrigen(),
                    ruta.getAlmacenRetorno(),
                    pedidos,
                    ruta.getVehiculo().getVelocidadKmh(),
                    contexto.reglas().distanciaNodoKm(),
                    contexto.reglas().servicioHoras(),
                    contexto.reglas().incluirRetorno(),
                    contexto.reglas().anchoCiudad(),
                    contexto.reglas().altoCiudad(),
                    carga,
                    new Nodo(actual.getX(), actual.getY()));
        }

        private static PlanUnidad desdeRescate(Vehiculo vehiculo, CargaAveriada carga,
                List<Pedido> pedidos, ContextoPlanificacion contexto) {
            int cantidad = pedidos.stream().mapToInt(Pedido::getCantidadQq).sum();
            PlanUnidad plan = new PlanUnidad(vehiculo.getIdVehiculo(),
                    carga.planSuspendido.almacenOrigen,
                    carga.planSuspendido.almacenRetorno, new ArrayDeque<>(pedidos),
                    vehiculo.getVelocidadKmh(), contexto.reglas().distanciaNodoKm(),
                    contexto.reglas().servicioHoras(), contexto.reglas().incluirRetorno(),
                    contexto.reglas().anchoCiudad(), contexto.reglas().altoCiudad(),
                    cantidad, vehiculo.getPosicionActual());
            plan.origenRescate = carga;
            plan.fase = FaseUnidad.IR_RECOGER;
            return plan;
        }

        private List<Long> idsNoEntregados() {
            List<Long> ids = new ArrayList<>();
            if (pedidoObjetivo != null) ids.add(pedidoObjetivo.getIdPedido());
            for (Pedido p : entregasEnParada) {
                if (pedidoObjetivo != p) ids.add(p.getIdPedido());
            }
            for (Pedido p : pedidosPendientes) ids.add(p.getIdPedido());
            return ids;
        }

        private int cantidadNoEntregada() {
            int cantidad = pedidoObjetivo == null ? 0 : pedidoObjetivo.getCantidadQq();
            for (Pedido p : entregasEnParada) {
                if (pedidoObjetivo != p) cantidad += p.getCantidadQq();
            }
            for (Pedido p : pedidosPendientes) cantidad += p.getCantidadQq();
            return cantidad;
        }
    }

    public static final class ConflictoSimulacionException extends RuntimeException {
        public ConflictoSimulacionException(String mensaje) {
            super(mensaje);
        }
    }
}
