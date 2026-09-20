package pe.edu.pucp.sisrap.experimentacion.aplicacion;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Consumer;

import pe.edu.pucp.sisrap.experimentacion.dominio.BaseOperativa;
import pe.edu.pucp.sisrap.experimentacion.dominio.DatosArchivos;
import pe.edu.pucp.sisrap.experimentacion.dominio.Escenario;
import pe.edu.pucp.sisrap.experimentacion.dominio.PlanExperimento;
import pe.edu.pucp.sisrap.experimentacion.dominio.ResultadoExperimento;
import pe.edu.pucp.sisrap.experimentacion.dominio.ResultadoExperimento.CorridaRegistrada;
import pe.edu.pucp.sisrap.experimentacion.dominio.Variante;
import pe.edu.pucp.sisrap.parametros.dominio.Configuracion;
import pe.edu.pucp.sisrap.parametros.dominio.ParametrosAlgoritmo;
import pe.edu.pucp.sisrap.parametros.dominio.ReglasPlanificacion;
import pe.edu.pucp.sisrap.parametros.dominio.ValidarConfiguracion;
import pe.edu.pucp.sisrap.planificador.dominio.algoritmo.AlgoritmoGenetico;
import pe.edu.pucp.sisrap.planificador.dominio.algoritmo.IAlgoritmoPlanificacion;
import pe.edu.pucp.sisrap.planificador.dominio.algoritmo.RecocidoSimulado;
import pe.edu.pucp.sisrap.planificador.dominio.modelo.ContextoPlanificacion;
import pe.edu.pucp.sisrap.planificador.dominio.modelo.PuntoConvergencia;
import pe.edu.pucp.sisrap.planificador.dominio.modelo.Solucion;
import pe.edu.pucp.sisrap.planificador.dominio.objetivo.FuncionObjetivo;

/**
 * Motor genérico del experimento: recorre la malla variantes x escenarios x repeticiones x algoritmos
 * y registra cada corrida. No sabe de archivos, BD ni formatos de salida.
 * <p>
 * Reglas metodológicas: (1) misma semilla para todos los algoritmos en una repetición (comparación pareada),
 * (2) el orden de los algoritmos rota en cada repetición para no favorecer a ninguno con el calentamiento de la JVM,
 * (3) corridas secuenciales para que los tiempos sean comparables, (4) cada solución se verifica de forma
 * independiente antes de aceptarla.
 */
public final class CorredorExperimento {
    private final Map<String, BiFunction<ParametrosAlgoritmo, FuncionObjetivo, IAlgoritmoPlanificacion>> fabricas = new LinkedHashMap<>();
    private final Consumer<String> registro;
    private final Consumer<CorridaRegistrada> alTerminarCorrida;

    /**
     * @param registro          recibe las líneas de progreso
     * @param alTerminarCorrida se invoca con cada corrida apenas termina (permite guardar avance si el proceso se interrumpe)
     */
    public CorredorExperimento(Consumer<String> registro, Consumer<CorridaRegistrada> alTerminarCorrida) {
        this.registro = registro;
        this.alTerminarCorrida = alTerminarCorrida;
        registrarAlgoritmo("GENETICO", AlgoritmoGenetico::new);
        registrarAlgoritmo("RECOCIDO_SIMULADO", RecocidoSimulado::new);
    }

    /** Punto de extensión: un algoritmo nuevo solo necesita implementar IAlgoritmoPlanificacion. */
    public void registrarAlgoritmo(String nombre, BiFunction<ParametrosAlgoritmo, FuncionObjetivo, IAlgoritmoPlanificacion> fabrica) {
        fabricas.put(nombre, fabrica);
    }

    public Set<String> algoritmosDisponibles() {
        return fabricas.keySet();
    }

    public ResultadoExperimento correr(PlanExperimento plan, BaseOperativa base, DatosArchivos archivos,
                                       List<Escenario> escenarios, List<String> advertenciasPrevias) {
        for (String algoritmo : plan.algoritmos())
            if (!fabricas.containsKey(algoritmo))
                throw new IllegalArgumentException("Algoritmo no registrado: " + algoritmo + ". Disponibles: " + fabricas.keySet());
        for (Variante variante : plan.variantes())   // falla antes de gastar tiempo, no a mitad del experimento
            ValidarConfiguracion.ejecutar(variante.aplicar(base.configuracion()));
        LocalDateTime inicio = LocalDateTime.now();
        List<String> advertencias = new ArrayList<>(advertenciasPrevias);
        List<CorridaRegistrada> corridas = new ArrayList<>();
        Set<String> avisosDeVerificacion = new HashSet<>();
        long total = plan.totalCorridas(), hechas = 0;
        long t0 = System.nanoTime();

        for (Variante variante : plan.variantes()) {
            Configuracion cfg = variante.aplicar(base.configuracion());
            ValidarConfiguracion.ejecutar(cfg);
            ReglasPlanificacion reglas = ReglasPlanificacion.desde(cfg);
            if (variante.modificaFuncionObjetivo())
                advertencias.add("Variante " + variante.nombre() + " cambia los pesos de F: su objetivo no es comparable con el de otras variantes; compare por cumplimiento, retraso y no asignados.");
            if (plan.calentamiento()) calentar(plan, cfg, escenarios.get(0).contexto(base, reglas));

            for (Escenario escenario : escenarios) {
                ContextoPlanificacion contexto = escenario.contexto(base, reglas);
                for (int repeticion = 0; repeticion < plan.repeticiones(); repeticion++) {
                    long semilla = plan.semillaBase() + repeticion;
                    List<String> orden = new ArrayList<>(plan.algoritmos());
                    Collections.rotate(orden, -repeticion);
                    for (String algoritmo : orden) {
                        var corrida = ejecutarUna(variante.nombre(), algoritmo, cfg, escenario, contexto, repeticion, semilla, avisosDeVerificacion);
                        corridas.add(corrida);
                        alTerminarCorrida.accept(corrida);
                        hechas++;
                    }
                }
                registro.accept(progreso(variante.nombre(), escenario.id(), hechas, total, t0));
            }
        }
        advertencias.addAll(avisosDeVerificacion.stream().sorted().toList());
        return new ResultadoExperimento(plan, base, archivos, escenarios, List.copyOf(corridas),
                AnalisisExperimento.resumir(corridas), AnalisisExperimento.comparar(corridas, plan.algoritmos()),
                List.copyOf(advertencias), inicio, LocalDateTime.now());
    }

    private CorridaRegistrada ejecutarUna(String variante, String algoritmo, Configuracion cfg, Escenario escenario,
                                          ContextoPlanificacion contexto, int repeticion, long semilla, Set<String> avisos) {
        var parametros = new ParametrosAlgoritmo(cfg, semilla);
        var objetivo = new FuncionObjetivo(cfg, contexto);
        IAlgoritmoPlanificacion motor = fabricas.get(algoritmo).apply(parametros, objetivo);
        long inicio = System.nanoTime();
        Solucion solucion = motor.planificar(contexto);
        double ms = (System.nanoTime() - inicio) / 1_000_000.0;

        boolean verificada = verificar(solucion, contexto, cfg, variante + "/" + escenario.id() + "/" + algoritmo, avisos);
        var corrida = MedicionCorridas.medir(algoritmo, escenario.tamanio(), semilla, ms, solucion, contexto, motor);
        List<PuntoConvergencia> traza = corrida.convergencia();
        int evaluaciones = traza.isEmpty() ? 0 : traza.get(traza.size() - 1).evaluaciones();
        return new CorridaRegistrada(variante, escenario.id(), escenario.instancia(), repeticion, evaluaciones, verificada, corrida);
    }

    /**
     * Comprobación independiente de la solución: cada pedido exactamente una vez (en una ruta o como no asignado),
     * capacidades respetadas, sin vehículos no disponibles, y F recalculado igual al reportado.
     */
    private boolean verificar(Solucion s, ContextoPlanificacion contexto, Configuracion cfg, String etiqueta, Set<String> avisos) {
        boolean correcta = true;
        Set<Long> vistos = new HashSet<>();
        for (var ruta : s.getRutas()) {
            if (ruta.getSecuenciaPedidos().isEmpty()) continue;
            if (!ruta.getVehiculo().isDisponible()) { avisos.add("Vehículo no disponible con pedidos en " + etiqueta); correcta = false; }
            if (ruta.cargaTotal() > ruta.getVehiculo().getCapacidadPaquetes()) { avisos.add("Capacidad excedida en " + etiqueta); correcta = false; }
            for (var p : ruta.getSecuenciaPedidos())
                if (!vistos.add(p.getIdPedido())) { avisos.add("Pedido repetido en " + etiqueta); correcta = false; }
        }
        for (var p : s.getPedidosNoAsignados())
            if (!vistos.add(p.getIdPedido())) { avisos.add("Pedido asignado y no asignado a la vez en " + etiqueta); correcta = false; }
        if (vistos.size() != contexto.getPedidos().size()) { avisos.add("Pedidos perdidos en " + etiqueta); correcta = false; }
        double reportado = s.getValorFuncionObjetivo();
        double recalculado = new FuncionObjetivo(cfg, contexto).calcular(s);
        if (Math.abs(reportado - recalculado) > 1e-6 * Math.max(1, Math.abs(reportado))) {
            avisos.add("F reportado distinto de F recalculado en " + etiqueta);
            correcta = false;
        }
        return correcta;
    }

    /** Una corrida descartada por algoritmo para que la compilación JIT no distorsione los tiempos medidos. */
    private void calentar(PlanExperimento plan, Configuracion cfg, ContextoPlanificacion contexto) {
        for (String algoritmo : plan.algoritmos()) {
            var parametros = new ParametrosAlgoritmo(cfg, plan.semillaBase() - 1);
            fabricas.get(algoritmo).apply(parametros, new FuncionObjetivo(cfg, contexto)).planificar(contexto);
        }
    }

    private static String progreso(String variante, String escenario, long hechas, long total, long t0) {
        Duration transcurrido = Duration.ofNanos(System.nanoTime() - t0);
        double fraccion = (double) hechas / total;
        Duration estimado = hechas == 0 ? Duration.ZERO : Duration.ofNanos((long) (transcurrido.toNanos() * (1 - fraccion) / fraccion));
        return String.format("[%d/%d] %s %s | transcurrido %s | restante aprox. %s",
                hechas, total, variante, escenario, formato(transcurrido), formato(estimado));
    }

    private static String formato(Duration d) {
        return String.format("%02d:%02d:%02d", d.toHours(), d.toMinutesPart(), d.toSecondsPart());
    }
}