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
import pe.edu.pucp.sisrap.experimentacion.dominio.EscenarioOperativo;
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
import pe.edu.pucp.sisrap.planificador.dominio.modelo.DistanciaReticula;
import pe.edu.pucp.sisrap.planificador.dominio.modelo.Solucion;
import pe.edu.pucp.sisrap.planificador.dominio.objetivo.FuncionObjetivo;

/** Motor de la experimentación numérica. */
public final class CorredorExperimento {
    private final Map<String, BiFunction<ParametrosAlgoritmo, FuncionObjetivo, IAlgoritmoPlanificacion>> fabricas = new LinkedHashMap<>();
    private final Consumer<String> registro;
    private final Consumer<CorridaRegistrada> alTerminarCorrida;

    public CorredorExperimento(Consumer<String> registro,
                               Consumer<CorridaRegistrada> alTerminarCorrida) {
        this.registro = registro;
        this.alTerminarCorrida = alTerminarCorrida;
        registrarAlgoritmo("GENETICO", AlgoritmoGenetico::new);
        registrarAlgoritmo("RECOCIDO_SIMULADO", RecocidoSimulado::new);
    }

    public void registrarAlgoritmo(String nombre,
                                   BiFunction<ParametrosAlgoritmo, FuncionObjetivo, IAlgoritmoPlanificacion> fabrica) {
        fabricas.put(nombre, fabrica);
    }

    public Set<String> algoritmosDisponibles() {
        return fabricas.keySet();
    }

    public ResultadoExperimento correr(PlanExperimento plan,
                                       BaseOperativa base,
                                       DatosArchivos archivos,
                                       List<Escenario> escenarios,
                                       List<String> advertenciasPrevias) {
        for (String algoritmo : plan.algoritmos()) {
            if (!fabricas.containsKey(algoritmo)) {
                throw new IllegalArgumentException(
                        "Algoritmo no registrado: " + algoritmo + ". Disponibles: " + fabricas.keySet());
            }
        }
        for (Variante variante : plan.variantes()) {
            ValidarConfiguracion.ejecutar(variante.aplicar(base.configuracion()));
        }

        LocalDateTime inicio = LocalDateTime.now();
        List<String> advertencias = new ArrayList<>(advertenciasPrevias);
        List<CorridaRegistrada> corridas = new ArrayList<>();
        Set<String> avisosDeVerificacion = new HashSet<>();
        long total = plan.totalCorridas();
        long hechas = 0;
        long t0 = System.nanoTime();

        for (Variante variante : plan.variantes()) {
            Configuracion cfg = variante.aplicar(base.configuracion());
            ValidarConfiguracion.ejecutar(cfg);
            ReglasPlanificacion reglas = ReglasPlanificacion.desde(cfg, base.anchoKm(), base.altoKm());

            if (variante.modificaFuncionObjetivo()) {
                advertencias.add("Variante " + variante.nombre()
                        + " cambia los pesos de F: no compare F entre variantes distintas; compare métricas operativas.");
            }

            if (plan.calentamiento()) {

            Escenario escenarioCalentamiento =
                    escenarios.stream()
                            .filter(e ->
                                    e.escenarioOperativo()
                                            == EscenarioOperativo.OPERACION_DIARIA)
                            .findFirst()
                            .orElse(null);

            if (escenarioCalentamiento != null) {

                var fechaCalentamiento =
                        escenarioCalentamiento
                                .pedidos()
                                .stream()
                                .map(p ->
                                        p.getFechaLlegada()
                                                .toLocalDate())
                                .min(java.time.LocalDate::compareTo)
                                .orElseThrow();

                ContextoPlanificacion contextoCalentamiento =
                        SimuladorJornada.crearContexto(
                                reglas,
                                plan,
                                base,
                                archivos,
                                fechaCalentamiento,
                                escenarioCalentamiento.pedidos(),
                                escenarioCalentamiento
                                        .bloqueosProgramados());

                calentar(
                        plan,
                        cfg,
                        contextoCalentamiento);

                DistanciaReticula.limpiarCache();
            }
        }

            for (Escenario escenario : escenarios) {
                for (int repeticion = 0; repeticion < plan.repeticiones(); repeticion++) {
                    long semilla = semillaCorrida(plan, escenario, repeticion);
                    List<String> orden = new ArrayList<>(plan.algoritmos());
                    Collections.rotate(orden, -repeticion);

                    for (String algoritmo : orden) {
                        DistanciaReticula.limpiarCache();

                        CorridaRegistrada corrida = switch (
                                escenario.escenarioOperativo()) {

                                case COLAPSO_LOGISTICO -> ejecutarColapso(
                                        variante.nombre(),
                                        algoritmo,
                                        cfg,
                                        reglas,
                                        plan,
                                        base,
                                        archivos,
                                        escenario,
                                        repeticion,
                                        semilla,
                                        avisosDeVerificacion);

                                case SIMULACION_CINCO_DIAS -> ejecutarCincoDias(
                                        variante.nombre(),
                                        algoritmo,
                                        cfg,
                                        reglas,
                                        plan,
                                        base,
                                        archivos,
                                        escenario,
                                        repeticion,
                                        semilla,
                                        avisosDeVerificacion);

                                case OPERACION_DIARIA -> ejecutarOperacionDiaria(
                                        variante.nombre(),
                                        algoritmo,
                                        cfg,
                                        reglas,
                                        plan,
                                        base,
                                        archivos,
                                        escenario,
                                        repeticion,
                                        semilla,
                                        avisosDeVerificacion);
                        };

                        corridas.add(corrida);
                        alTerminarCorrida.accept(corrida);
                        hechas++;
                        }
                }
                registro.accept(progreso(variante.nombre(), escenario.id(), hechas, total, t0));
            }
        }

        advertencias.addAll(avisosDeVerificacion.stream().sorted().toList());
        return new ResultadoExperimento(
                plan,
                base,
                archivos,
                escenarios,
                List.copyOf(corridas),
                AnalisisExperimento.resumir(corridas),
                AnalisisExperimento.comparar(corridas, plan.algoritmos()),
                List.copyOf(advertencias),
                inicio,
                LocalDateTime.now());
    }

    private CorridaRegistrada ejecutarOperacionDiaria(String variante,
                                                          String algoritmo,
                                                          Configuracion cfg,
                                                          ReglasPlanificacion reglas,
                                                          PlanExperimento plan,
                                                          BaseOperativa base,
                                                          DatosArchivos archivos,
                                                          Escenario escenario,
                                                          int repeticion,
                                                          long semilla,
                                                          Set<String> avisos) {
        var fecha = escenario.pedidos().stream()
                .map(p -> p.getFechaLlegada().toLocalDate())
                .min(java.time.LocalDate::compareTo)
                .orElseThrow();

        SimuladorJornada.Resultado resultado = SimuladorJornada.ejecutar(
                fabricas.get(algoritmo),
                cfg,
                reglas,
                plan,
                base,
                archivos,
                fecha,
                escenario.pedidos(),
                escenario.bloqueosProgramados(),
                semilla);

        String etiqueta = variante + "/" + escenario.id() + "/" + algoritmo + "/dia";
        boolean verificada = verificar(
                resultado.solucion(),
                resultado.contexto(),
                cfg,
                etiqueta,
                avisos);

        var corrida = MedicionCorridas.medir(
                algoritmo,
                resultado.tamanio(),
                semilla,
                resultado.tiempoEjecucionMs(),
                resultado.solucion(),
                resultado.contexto(),
                resultado.motor(),
                escenario.escenarioOperativo().name(),
                escenario.perfilPresion().name());

        return new CorridaRegistrada(
                variante,
                escenario.id(),
                escenario.instancia(),
                repeticion,
                resultado.evaluaciones(),
                verificada,
                corrida);
    }

    private CorridaRegistrada ejecutarCincoDias(String variante,
                                                 String algoritmo,
                                                 Configuracion cfg,
                                                 ReglasPlanificacion reglas,
                                                 PlanExperimento plan,
                                                 BaseOperativa base,
                                                 DatosArchivos archivos,
                                                 Escenario escenario,
                                                 int repeticion,
                                                 long semilla,
                                                 Set<String> avisos) {
        SimuladorCincoDias.Resultado resultado = SimuladorCincoDias.simular(
                fabricas.get(algoritmo),
                cfg,
                reglas,
                plan,
                base,
                archivos,
                escenario,
                semilla);

        boolean verificada = true;
        for (SimuladorCincoDias.Jornada jornada : resultado.jornadas()) {
            String etiqueta = variante + "/" + escenario.id() + "/" + algoritmo
                    + "/dia-" + jornada.numeroDia();
            verificada &= verificar(
                    jornada.solucion(),
                    jornada.contexto(),
                    cfg,
                    etiqueta,
                    avisos);
        }

        var corrida = MedicionCorridas.medirCincoDias(
                algoritmo,
                escenario.tamanio(),
                semilla,
                resultado,
                escenario.escenarioOperativo().name(),
                escenario.perfilPresion().name());

        return new CorridaRegistrada(
                variante,
                escenario.id(),
                escenario.instancia(),
                repeticion,
                resultado.evaluacionesTotales(),
                verificada,
                corrida);
    }

    private CorridaRegistrada ejecutarColapso(String variante,
                                               String algoritmo,
                                               Configuracion cfg,
                                               ReglasPlanificacion reglas,
                                               PlanExperimento plan,
                                               BaseOperativa base,
                                               DatosArchivos archivos,
                                               Escenario escenario,
                                               int repeticion,
                                               long semilla,
                                               Set<String> avisos) {
        SimuladorColapso.Resultado resultado = SimuladorColapso.simular(
                algoritmo,
                fabricas.get(algoritmo),
                cfg,
                reglas,
                plan,
                base,
                archivos,
                escenario,
                semilla);

        String etiqueta = variante + "/" + escenario.id() + "/" + algoritmo + "/colapso";
        boolean verificada = verificar(
                resultado.solucionFinal(),
                resultado.contextoFinal(),
                cfg,
                etiqueta,
                avisos);

        var corrida = MedicionCorridas.medir(
                algoritmo,
                resultado.tamanioUltimaJornada(),
                semilla,
                resultado.tiempoEjecucionTotalMs(),
                resultado.solucionFinal(),
                resultado.contextoFinal(),
                resultado.motorFinal(),
                escenario.escenarioOperativo().name(),
                escenario.perfilPresion().name(),
                null,
                null,
                null,
                null,
                resultado.tamanioUltimaJornada(),
                resultado.colapsoAlcanzado(),
                resultado.tiempoColapsoHoras(),
                resultado.instanteColapso(),
                resultado.pedidoCausaColapso(),
                resultado.prioridadCausaColapso(),
                resultado.deadlineCausaColapso(),
                resultado.noAsignadosAlColapso(),
                resultado.lambdaAlColapso());

        return new CorridaRegistrada(
                variante,
                escenario.id(),
                escenario.instancia(),
                repeticion,
                resultado.evaluacionesTotales(),
                verificada,
                corrida);
    }

    /** Comprobación independiente de la solución final. */
    private boolean verificar(Solucion s,
                              ContextoPlanificacion contexto,
                              Configuracion cfg,
                              String etiqueta,
                              Set<String> avisos) {
        boolean correcta = true;
        Set<Long> vistos = new HashSet<>();
        Set<String> vehiculosUsados = new HashSet<>();
        Set<String> idsContexto = contexto.getVehiculos().stream()
                .map(v -> v.getIdVehiculo())
                .collect(java.util.stream.Collectors.toSet());

        for (var ruta : s.getRutas()) {
            if (ruta.getSecuenciaPedidos().isEmpty()) continue;

            String idVehiculo = ruta.getVehiculo().getIdVehiculo();
            if (!idsContexto.contains(idVehiculo)) {
                avisos.add("Vehículo ajeno al contexto en " + etiqueta);
                correcta = false;
            }
            if (!vehiculosUsados.add(idVehiculo)) {
                avisos.add("Vehículo usado en más de una ruta en " + etiqueta);
                correcta = false;
            }

            var vehiculoContexto = contexto.getVehiculos().stream()
                    .filter(v -> v.getIdVehiculo().equals(idVehiculo))
                    .findFirst()
                    .orElse(null);
            if (vehiculoContexto == null || !vehiculoContexto.isDisponible()) {
                avisos.add("Vehículo no disponible con pedidos en " + etiqueta);
                correcta = false;
            } else if (ruta.cargaTotal() > vehiculoContexto.getCapacidadPaquetes()) {
                avisos.add("Capacidad excedida en " + etiqueta);
                correcta = false;
            }

            for (var p : ruta.getSecuenciaPedidos()) {
                if (!vistos.add(p.getIdPedido())) {
                    avisos.add("Pedido repetido en " + etiqueta);
                    correcta = false;
                }
            }
        }

        for (var p : s.getPedidosNoAsignados()) {
            if (!vistos.add(p.getIdPedido())) {
                avisos.add("Pedido asignado y no asignado a la vez en " + etiqueta);
                correcta = false;
            }
        }

        if (vistos.size() != contexto.getPedidos().size()) {
            avisos.add("Pedidos perdidos en " + etiqueta);
            correcta = false;
        }

        double reportado = s.getValorFuncionObjetivo();
        double recalculado = new FuncionObjetivo(cfg, contexto).calcular(s);
        if (Math.abs(reportado - recalculado) > 1e-6 * Math.max(1, Math.abs(reportado))) {
            avisos.add("F reportado distinto de F recalculado en " + etiqueta);
            correcta = false;
        }
        return correcta;
    }

    private static long semillaCorrida(PlanExperimento plan, Escenario escenario, int repeticion) {
        long desplazamientoInstancia = Math.multiplyExact((long) escenario.instancia() - 1L, plan.repeticiones());
        return Math.addExact(plan.semillaBase(), Math.addExact(desplazamientoInstancia, repeticion));
    }

    private void calentar(PlanExperimento plan,
                          Configuracion cfg,
                          ContextoPlanificacion contexto) {
        for (String algoritmo : plan.algoritmos()) {
            var parametros = new ParametrosAlgoritmo(cfg, plan.semillaBase() - 1);
            fabricas.get(algoritmo)
                    .apply(parametros, new FuncionObjetivo(cfg, contexto))
                    .planificar(contexto);
        }
    }

    private static String progreso(String variante,
                                   String escenario,
                                   long hechas,
                                   long total,
                                   long t0) {
        Duration transcurrido = Duration.ofNanos(System.nanoTime() - t0);
        double fraccion = (double) hechas / total;
        Duration estimado = hechas == 0
                ? Duration.ZERO
                : Duration.ofNanos((long) (transcurrido.toNanos() * (1 - fraccion) / fraccion));
        return String.format("[%d/%d] %s %s | transcurrido %s | restante aprox. %s",
                hechas, total, variante, escenario, formato(transcurrido), formato(estimado));
    }

    private static String formato(Duration d) {
        return String.format("%02d:%02d:%02d", d.toHours(), d.toMinutesPart(), d.toSecondsPart());
    }
}