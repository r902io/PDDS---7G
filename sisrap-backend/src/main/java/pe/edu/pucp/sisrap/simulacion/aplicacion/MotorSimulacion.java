package pe.edu.pucp.sisrap.simulacion.aplicacion;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import pe.edu.pucp.sisrap.almacen.dominio.Almacen;
import pe.edu.pucp.sisrap.control.aplicacion.GestionarControlSimulacion;
import pe.edu.pucp.sisrap.geografia.dominio.Nodo;
import pe.edu.pucp.sisrap.parametros.dominio.ParametrosAlgoritmo;
import pe.edu.pucp.sisrap.pedido.dominio.Pedido;
import pe.edu.pucp.sisrap.planificador.dominio.algoritmo.AlgoritmoGenetico;
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
import pe.edu.pucp.sisrap.simulacion.dominio.SnapshotSimulacion;
import pe.edu.pucp.sisrap.simulacion.dominio.VehiculoPersistido;
import pe.edu.pucp.sisrap.simulacion.dominio.VehiculoSnapshot;
import pe.edu.pucp.sisrap.simulacion.infraestructura.SseSimulacion;

@Service
public class MotorSimulacion {

    private static final long NANO_SEGUNDO = 1_000_000_000L;
    private static final double NANOS_POR_HORA = 3_600_000_000_000.0;
    private static final String ALGORITMO_OPERATIVO = "GENETICO";

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

    private volatile SnapshotSimulacion snapshotActual;
    private EstadoSimulacion estado = EstadoSimulacion.DETENIDA;
    private ConfiguracionSimulacion configuracion;
    private Long idSimulacion;
    private LocalDateTime reloj;
    private Set<String> bloqueados = Set.of();

    private long nanoInicioReal;
    private long nanoUltimoTick;
    private long nanoUltimaPlanificacion;
    private long nanoUltimaPersistencia;
    private long nanoUltimaPublicacion;
    private long contadorPlanificaciones;
    private LocalDateTime proximaPlanificacionVirtual;
    private String mensajeEstado;

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
        repositorio.sincronizarDisponibilidad(nuevaConfiguracion.fechaHoraInicio());

        long id = repositorio.crearEjecucion(
                nuevaConfiguracion,
                ALGORITMO_OPERATIVO,
                perfil);

        planes.clear();
        configuracion = nuevaConfiguracion;
        idSimulacion = id;
        reloj = nuevaConfiguracion.fechaHoraInicio();
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
        proximaPlanificacionVirtual = reloj;

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

    public synchronized void sincronizarBloqueos() {
        if (estado != EstadoSimulacion.EJECUTANDO && estado != EstadoSimulacion.PAUSADA) {
            return;
        }
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
        if (estado == EstadoSimulacion.DETENIDA
                || estado == EstadoSimulacion.FINALIZADA
                || estado == EstadoSimulacion.ERROR) {
            return;
        }

        long ahoraNano = System.nanoTime();

        if (estado == EstadoSimulacion.PAUSADA) {
            if (ahoraNano - nanoUltimaPublicacion >= NANO_SEGUNDO) {
                publicarSnapshot(false);
            }
            return;
        }

        long deltaRealNanos = Math.max(0L, ahoraNano - nanoUltimoTick);
        nanoUltimoTick = ahoraNano;

        /*
         * Compresión temporal automática.
         *
         * El usuario no puede modificar este factor. Se calcula una sola vez
         * al iniciar según el escenario:
         *
         * - OPERACION_DIARIA       -> ~15 min reales para 1 día simulado.
         * - SIMULACION_CINCO_DIAS  -> ~30 min reales para 5 días simulados.
         * - COLAPSO_LOGISTICO      -> hasta ~45 min reales para el horizonte
         *                            máximo; termina antes al primer
         *                            incumplimiento.
         */
        long deltaVirtualNanos = Math.max(
                1L,
                Math.round(
                        deltaRealNanos
                                * factorTemporalInterno
                )
        );

        LocalDateTime relojAnterior = reloj;
        LocalDateTime nuevoReloj = reloj.plusNanos(deltaVirtualNanos);
        if (configuracion.escenario() != EscenarioSimulacion.OPERACION_DIARIA
                && nuevoReloj.isAfter(configuracion.fechaHoraFin())) {
            nuevoReloj = configuracion.fechaHoraFin();
            deltaVirtualNanos = Math.max(
                    0L,
                    Duration.between(reloj, nuevoReloj).toNanos());
        }
        LocalDateTime finTurno = JornadaOperativa.finTurno(relojAnterior);
        if (nuevoReloj.isAfter(finTurno)) nuevoReloj = finTurno;
        if (configuracion.escenario() == EscenarioSimulacion.COLAPSO_LOGISTICO) {
            var vencimiento = repositorio.primerVencimientoEntre(relojAnterior, nuevoReloj);
            if (vencimiento.isPresent() && vencimiento.get().instante().isBefore(nuevoReloj)) {
                nuevoReloj = vencimiento.get().instante();
            }
        }
        deltaVirtualNanos = Duration.between(relojAnterior, nuevoReloj).toNanos();
        reloj = nuevoReloj;

        repositorio.recargarAlmacenesSiCorresponde(
                relojAnterior,
                reloj
        );

        repositorio.publicarPedidosHasta(reloj);

        repositorio.sincronizarDisponibilidad(reloj);

        Map<String, VehiculoPersistido> estadosVehiculos =
                repositorio.cargarVehiculosPersistidos();
        cancelarPlanesInterrumpidos(estadosVehiculos);

        actualizarBloqueosActivos();

        double deltaHoras = JornadaOperativa.horasEfectivas(relojAnterior, reloj);
        actualizarPlanes(deltaHoras);
        persistirPosiciones();
        if (!reloj.isBefore(finTurno)) cerrarTurno();
        repositorio.marcarPedidosRetrasados(reloj);

        /*
         * En colapso logístico la ejecución termina exactamente cuando el
         * backend detecta el primer pedido fuera de plazo.
         */
        if (configuracion.escenario() == EscenarioSimulacion.COLAPSO_LOGISTICO) {
            var incumplimiento = repositorio.primerIncumplimiento(reloj);
            if (incumplimiento.isPresent()) {
                var evento = incumplimiento.get();
                repositorio.guardarColapso(idSimulacion, evento);
                mensajeEstado = "Colapso logístico: pedido " + evento.idPedido()
                        + " fuera de plazo en " + evento.instante();
                finalizar(true, mensajeEstado);
                return;
            }
        }

        if (debeIntentarPlanificar(ahoraNano)) {
            intentarPlanificar();
            nanoUltimaPlanificacion = ahoraNano;
            proximaPlanificacionVirtual = reloj.plusMinutes(1);
        }

        if (ahoraNano - nanoUltimaPersistencia >= NANO_SEGUNDO) {
            repositorio.actualizarEjecucion(
                idSimulacion,
                estado,
                reloj);
            nanoUltimaPersistencia = ahoraNano;
        }

        if (ahoraNano - nanoUltimaPublicacion >= 500_000_000L) {
            publicarSnapshot(false);
        }

        if (configuracion.escenario() != EscenarioSimulacion.OPERACION_DIARIA
                && !reloj.isBefore(configuracion.fechaHoraFin())) {
            finalizar(true, "La simulación alcanzó la fecha/hora final");
        }
    }

    private boolean debeIntentarPlanificar(long ahoraNano) {
        if (estado != EstadoSimulacion.EJECUTANDO) {
            return false;
        }
        if (proximaPlanificacionVirtual != null && reloj.isBefore(proximaPlanificacionVirtual)) {
            return false;
        }
        return nanoUltimaPlanificacion == 0L
                || ahoraNano - nanoUltimaPlanificacion >= NANO_SEGUNDO;
    }

    private void intentarPlanificar() {
        ContextoOperativo operativo = repositorio.cargarContexto(
                perfil,
                reloj,
                configuracion.fechaHoraFin());

        ContextoPlanificacion contexto = operativo.contexto();
        if (contexto.getPedidos().isEmpty()) {
            return;
        }
        boolean hayDisponibles = contexto.getVehiculos().stream()
                .anyMatch(v -> v.isDisponible() && !planes.containsKey(v.getIdVehiculo()));
        if (!hayDisponibles) {
            return;
        }

        long semilla = configuracion.semilla() + contadorPlanificaciones++;
        ParametrosAlgoritmo parametros = new ParametrosAlgoritmo(
                operativo.configuracion(),
                semilla);
        FuncionObjetivo objetivo = new FuncionObjetivo(
                operativo.configuracion(),
                contexto);
        AlgoritmoGenetico planificador = new AlgoritmoGenetico(parametros, objetivo);

        // AlgoritmoGenetico construye su población inicial mediante ConstructorVoraz.
        Solucion solucion = planificador.planificar(contexto);
        objetivo.calcular(solucion);
        if (!solucion.isEsFactible() || solucion.getValorR() > 0) {
            mensajeEstado = "No se encontró un plan factible dentro de los plazos";
            return;
        }

        List<PlanUnidad> nuevos = new ArrayList<>();
        List<Ruta> rutasAceptadas = new ArrayList<>();
        List<Long> pedidosAsignados = new ArrayList<>();

        for (Ruta ruta : solucion.getRutas()) {
            if (ruta.getSecuenciaPedidos().isEmpty()) {
                continue;
            }
            String idVehiculo = ruta.getVehiculo().getIdVehiculo();
            if (!ruta.getVehiculo().isDisponible() || planes.containsKey(idVehiculo)) {
                continue;
            }

            PlanUnidad plan = PlanUnidad.desde(ruta, contexto);
            nuevos.add(plan);
            rutasAceptadas.add(ruta);
            for (Pedido pedido : ruta.getSecuenciaPedidos()) {
                pedidosAsignados.add(pedido.getIdPedido());
            }
        }

        if (nuevos.isEmpty()) {
            mensajeEstado = solucion.getPedidosNoAsignados().isEmpty()
                    ? "No hubo rutas nuevas"
                    : "Hay pedidos pendientes sin vehículo/ruta factible";
            return;
        }

        repositorio.guardarPlan(idSimulacion, reloj, solucion, rutasAceptadas,
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

    private void cerrarTurno() {
        if (planes.isEmpty()) return;
        for (PlanUnidad plan : new ArrayList<>(planes.values())) {
            repositorio.reencolarPedidos(plan.idsNoEntregados(), reloj);
            if (plan.stockTomado) {
                repositorio.devolverStock(plan.almacenOrigen.getIdAlmacen(),
                        plan.cantidadNoEntregada());
            }
            repositorio.actualizarEstadoVehiculo(plan.idVehiculo, "DISPONIBLE");
        }
        planes.clear();
        proximaPlanificacionVirtual = reloj;
        nanoUltimaPlanificacion = 0L;
    }

    private void cancelarPlanesInterrumpidos(
            Map<String, VehiculoPersistido> estados) {

        List<String> cancelar = new ArrayList<>();
        for (PlanUnidad plan : planes.values()) {
            VehiculoPersistido persistido = estados.get(plan.idVehiculo);
            if (persistido == null) {
                cancelar.add(plan.idVehiculo);
                continue;
            }
            if ("EN_AVERIA".equals(persistido.estado())
                    || "EN_MANTENIMIENTO".equals(persistido.estado())) {
                plan.posicion = new Nodo(persistido.x(), persistido.y());
                cancelar.add(plan.idVehiculo);
            }
        }

        for (String id : cancelar) {
            PlanUnidad plan = planes.remove(id);
            if (plan == null) {
                continue;
            }
            Collection<Long> pendientes = plan.idsNoEntregados();
            repositorio.reencolarPedidos(pendientes, reloj);
            if (plan.stockTomado) {
                repositorio.devolverStock(
                        plan.almacenOrigen.getIdAlmacen(),
                        plan.cantidadNoEntregada());
            }
        }

        if (!cancelar.isEmpty()) {
            proximaPlanificacionVirtual = reloj;
            nanoUltimaPlanificacion = 0L;
            mensajeEstado = "Se reencolaron pedidos por avería/mantenimiento";
        }
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
        return true;
    }

    private void invalidarCaminosBloqueados() {
        for (PlanUnidad plan : planes.values()) {
            if (plan.destinoActual == null || mismaPosicion(plan.posicion, plan.destinoActual)) {
                continue;
            }
            plan.camino.clear();
            CaminoReticula.calcular(plan.posicion, plan.destinoActual,
                    bloqueados, plan.ancho, plan.alto)
                    .ifPresent(plan.camino::addAll);
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
                if (plan.fase == FaseUnidad.ATENDIENDO) {
                    if (plan.servicioRestanteHoras > plan.creditoHoras) {
                        plan.servicioRestanteHoras -= plan.creditoHoras;
                        plan.creditoHoras = 0;
                        break;
                    }

                    plan.creditoHoras -= plan.servicioRestanteHoras;
                    plan.servicioRestanteHoras = 0;
                    Pedido entregado = plan.pedidoObjetivo;
                    repositorio.entregarPedido(entregado.getIdPedido(), instanteEvento(plan));
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
                    var camino = CaminoReticula.calcular(
                            plan.posicion,
                            plan.destinoActual,
                            bloqueados,
                            plan.ancho,
                            plan.alto);
                    if (camino.isEmpty()) {
                        // El vehículo queda esperando hasta que cambie la red vial.
                        plan.creditoHoras = 0;
                        break;
                    }
                    plan.camino.addAll(camino.get());
                }

                double horasPorNodo = plan.distanciaNodoKm / plan.velocidadKmh;
                if (plan.creditoHoras + 1e-12 < horasPorNodo) {
                    break;
                }

                Nodo siguiente = plan.camino.peekFirst();
                if (siguiente == null || ArcoReticula.bloqueado(
                        plan.posicion.getX(), plan.posicion.getY(),
                        siguiente.getX(), siguiente.getY(), bloqueados)) {
                    plan.camino.clear();
                    plan.creditoHoras = 0;
                    break;
                }

                plan.creditoHoras -= horasPorNodo;
                plan.camino.removeFirst();
                plan.posicion = siguiente;

                if (plan.camino.isEmpty() && mismaPosicion(plan.posicion, plan.destinoActual)) {
                    procesarLlegada(plan, finalizados);
                    if (finalizados.contains(plan.idVehiculo)) {
                        break;
                    }
                }
            }
        }

        for (String id : finalizados) {
            planes.remove(id);
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
                plan.destinoActual = plan.almacenOrigen.getUbicacion();
                yield true;
            }
            case ATENDIENDO -> false;
        };
    }

    private void procesarLlegada(
            PlanUnidad plan,
            List<String> finalizados) {

        plan.camino.clear();
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
            case IR_PEDIDO -> {
                // El SLA se verifica a la llegada; la descarga dura 1h adicional.
                repositorio.registrarArribo(plan.pedidoObjetivo.getIdPedido(), instanteEvento(plan));
                plan.fase = FaseUnidad.ATENDIENDO;
                plan.servicioRestanteHoras = plan.servicioHoras;
                if (plan.servicioRestanteHoras <= 0) {
                    Pedido entregado = plan.pedidoObjetivo;
                    repositorio.entregarPedido(entregado.getIdPedido(), instanteEvento(plan));
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
        if (plan.incluirRetorno
                && !mismaPosicion(plan.posicion, plan.almacenOrigen.getUbicacion())) {
            plan.fase = FaseUnidad.RETORNANDO;
            plan.destinoActual = null;
            return;
        }
        finalizarPlan(plan, finalizados);
    }

    private void finalizarPlan(
            PlanUnidad plan,
            List<String> finalizados) {
        repositorio.actualizarEstadoVehiculo(plan.idVehiculo, "DISPONIBLE");
        finalizados.add(plan.idVehiculo);
        proximaPlanificacionVirtual = reloj;
    }

    /** Reconstruye el instante virtual del evento dentro del tick de simulación. */
    private LocalDateTime instanteEvento(PlanUnidad plan) {
        long nanos = Math.max(0L, Math.round(plan.creditoHoras * NANOS_POR_HORA));
        LocalDateTime estimado = reloj.minusNanos(nanos);
        return estimado.isBefore(configuracion.fechaHoraInicio())
                ? configuracion.fechaHoraInicio() : estimado;
    }

    private void persistirPosiciones() {
        if (planes.isEmpty()) {
            return;
        }
        Map<String, PuntoSimulacion> posiciones = new HashMap<>();
        for (PlanUnidad plan : planes.values()) {
            posiciones.put(
                    plan.idVehiculo,
                    new PuntoSimulacion(plan.posicion.getX(), plan.posicion.getY()));
        }
        repositorio.actualizarPosiciones(posiciones);
    }

    private void finalizar(boolean natural, String mensaje) {
        Map<String, VehiculoPersistido> estadosPersistidos =
                repositorio.cargarVehiculosPersistidos();

        for (PlanUnidad plan : new ArrayList<>(planes.values())) {
            repositorio.reencolarPedidos(plan.idsNoEntregados(), reloj);
            if (plan.stockTomado) {
                repositorio.devolverStock(
                        plan.almacenOrigen.getIdAlmacen(),
                        plan.cantidadNoEntregada());
            }
            VehiculoPersistido actual = estadosPersistidos.get(plan.idVehiculo);
            if (actual != null && "EN_RUTA".equals(actual.estado())) {
                repositorio.actualizarEstadoVehiculo(plan.idVehiculo, "DISPONIBLE");
            }
        }
        planes.clear();

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

        estado = EstadoSimulacion.ERROR;
        mensajeEstado = "Error de simulación: " + mensaje(error);

        try {
            Map<String, VehiculoPersistido> estados = repositorio.cargarVehiculosPersistidos();
            for (PlanUnidad plan : new ArrayList<>(planes.values())) {
                repositorio.reencolarPedidos(plan.idsNoEntregados(), reloj);
                if (plan.stockTomado) {
                    repositorio.devolverStock(
                            plan.almacenOrigen.getIdAlmacen(),
                            plan.cantidadNoEntregada());
                }
                VehiculoPersistido actual = estados.get(plan.idVehiculo);
                if (actual != null && "EN_RUTA".equals(actual.estado())) {
                    repositorio.actualizarEstadoVehiculo(plan.idVehiculo, "DISPONIBLE");
                }
            }
            planes.clear();

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

    private void publicarSnapshot(boolean forzar) {
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
                                    .toList()));
        }

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
                mensajeEstado);

        sse.publicar(snapshotActual);
        nanoUltimaPublicacion = System.nanoTime();
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
        IR_PEDIDO,
        ATENDIENDO,
        RETORNANDO
    }

    private static final class PlanUnidad {
        private final String idVehiculo;
        private final Almacen almacenOrigen;
        private final Deque<Pedido> pedidosPendientes;
        private final double velocidadKmh;
        private final double distanciaNodoKm;
        private final double servicioHoras;
        private final boolean incluirRetorno;
        private final int ancho;
        private final int alto;
        private final int cargaInicial;

        private Nodo posicion;
        private FaseUnidad fase = FaseUnidad.IR_ALMACEN;
        private Nodo destinoActual;
        private Pedido pedidoObjetivo;
        private final Deque<Nodo> camino = new ArrayDeque<>();
        private double creditoHoras;
        private double servicioRestanteHoras;
        private boolean stockTomado;

        private PlanUnidad(
                String idVehiculo,
                Almacen almacenOrigen,
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

        private List<Long> idsNoEntregados() {
            List<Long> ids = new ArrayList<>();
            if (pedidoObjetivo != null) {
                ids.add(pedidoObjetivo.getIdPedido());
            }
            for (Pedido p : pedidosPendientes) {
                ids.add(p.getIdPedido());
            }
            return ids;
        }

        private int cantidadNoEntregada() {
            int cantidad = pedidoObjetivo == null ? 0 : pedidoObjetivo.getCantidadQq();
            for (Pedido p : pedidosPendientes) {
                cantidad += p.getCantidadQq();
            }
            return cantidad;
        }
    }

    public static final class ConflictoSimulacionException extends RuntimeException {
        public ConflictoSimulacionException(String mensaje) {
            super(mensaje);
        }
    }
}
