package pe.edu.pucp.sisrap.experimentacion.infraestructura;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

import pe.edu.pucp.sisrap.experimentacion.dominio.Escenario;
import pe.edu.pucp.sisrap.experimentacion.dominio.EscenarioOperativo;
import pe.edu.pucp.sisrap.experimentacion.dominio.InformeExperimento.Estadistica;
import pe.edu.pucp.sisrap.experimentacion.dominio.ResultadoExperimento;
import pe.edu.pucp.sisrap.experimentacion.dominio.ResultadoExperimento.Comparacion;
import pe.edu.pucp.sisrap.experimentacion.dominio.ResultadoExperimento.Comparacion.MetricaSecundaria;
import pe.edu.pucp.sisrap.experimentacion.dominio.ResultadoExperimento.CorridaRegistrada;
import pe.edu.pucp.sisrap.experimentacion.dominio.ResultadoExperimento.ResumenGrupo;
import pe.edu.pucp.sisrap.experimentacion.dominio.Variante;

/** Exporta insumos y resultados del experimento en CSV y texto. */
public final class ExportadorResultados {
    private final Path carpeta;

    public ExportadorResultados(Path carpeta) {
        this.carpeta = carpeta;
    }

    public void escribirInsumos(ResultadoExperimento r) {
        crearCarpeta();
        escribir("manifiesto.txt", manifiesto(r));
        escribir("parametros.csv", parametros(r));
        escribir("flota.csv", flota(r));
        escribir("almacenes.csv", almacenes(r));
        escribir("instancias.csv", instancias(r));
        escribir("pedidos_instancias.csv", pedidosInstancias(r));
        escribir("bloqueos_programados.csv", bloqueosProgramados(r));
    }

    public void escribirResultados(ResultadoExperimento r, boolean detalle) {
        escribirInsumos(r);
        escribir("corridas.csv", corridas(r));
        escribir("resumen.csv", resumen(r));
        escribir("comparacion.csv", comparacion(r));
        escribir("comparacion_metricas.csv", comparacionMetricas(r));
        escribir("colapso.csv", colapso(r));
        escribir("resumen.txt", resumenTexto(r));
        if (detalle) {
            escribir("convergencia.csv", convergencia(r));
            escribir("rutas.csv", rutas(r));
        }
    }

    private String manifiesto(ResultadoExperimento r) {
        var p = r.plan();
        var sb = new StringBuilder();
        sb.append("EXPERIMENTO ").append(p.nombre()).append('\n');
        sb.append("Inicio: ").append(r.inicio())
                .append("  Fin: ").append(r.fin())
                .append("  Duración: ").append(duracion(Duration.between(r.inicio(), r.fin())))
                .append('\n');
        sb.append("Entorno: Java ").append(System.getProperty("java.version"))
                .append(", ").append(System.getProperty("os.name"))
                .append(", ").append(Runtime.getRuntime().availableProcessors())
                .append(" procesadores\n\n");

        sb.append("PLAN\n");
        sb.append("  perfil de parámetros (BD): ").append(p.perfil()).append('\n');
        sb.append("  algoritmos: ").append(p.algoritmos()).append('\n');
        sb.append("  escenarios operativos: ").append(p.escenariosOperativos()).append('\n');
        sb.append("  perfiles de presión: ").append(p.perfiles()).append('\n');
        sb.append("  demanda histórica P50 (NORMAL): ").append(p.demandaP50Diaria()).append(" pedidos/día\n");
        sb.append("  demanda histórica P75 (ALTA): ").append(p.demandaP75Diaria()).append(" pedidos/día\n");
        sb.append("  demanda histórica P90 (CRITICA): ").append(p.demandaP90Diaria()).append(" pedidos/día\n");
        sb.append("  incremento diario en COLAPSO_LOGISTICO (P75-P50): ")
                .append(p.incrementoColapsoDiario()).append(" pedidos/día\n");
        sb.append("  instancias por combinación: ").append(p.instancias()).append('\n');
        sb.append("  repeticiones por instancia: ").append(p.repeticiones()).append('\n');
        sb.append("  semilla base: ").append(p.semillaBase()).append('\n');
        sb.append("  pedidos desde: ").append(p.desde()).append('\n');
        sb.append("  mantenimiento preventivo aplicado: ").append(p.aplicarMantenimiento()).append('\n');
        sb.append("  calentamiento JVM: ").append(p.calentamiento()).append('\n');
        sb.append("  total de ejecuciones experimentales: ").append(p.totalCorridas()).append('\n');
        sb.append("  N01/N02/N03 son instancias independientes, no niveles de presión.\n");
        sb.append("  En COLAPSO_LOGISTICO la presión aumenta dentro de cada corrida usando el incremento histórico P75-P50.\n");
        sb.append("  La corrida de colapso termina cuando aparece el primer pedido que el planificador no logra mantener dentro de su deadline.\n");

        for (Variante v : p.variantes()) {
            sb.append("  variante ").append(v.nombre()).append(": ")
                    .append(v.sobrescrituras().isEmpty() ? "(perfil tal cual)" : v.sobrescrituras())
                    .append('\n');
        }

        var a = r.archivos();
        sb.append("\nDATOS RECOPILADOS\n");
        sb.append("  pedidos leídos: ").append(a.pedidos().size());
        if (!a.pedidos().isEmpty()) {
            sb.append(" (del ").append(a.pedidos().get(0).llegada())
                    .append(" al ").append(a.pedidos().get(a.pedidos().size() - 1).llegada()).append(')');
        }
        sb.append('\n');
        Map<String, Long> porPrioridad = a.pedidos().stream()
                .collect(Collectors.groupingBy(x -> x.prioridad().name(), TreeMap::new, Collectors.counting()));
        sb.append("  por prioridad: ").append(porPrioridad).append('\n');
        sb.append("  bloqueos leídos: ").append(a.bloqueos().size()).append('\n');
        sb.append("  días con mantenimiento preventivo: ").append(a.mantenimiento().size()).append('\n');
        sb.append("  archivos leídos: ").append(a.archivosLeidos().size()).append('\n');

        sb.append("\nADVERTENCIAS Y LÍMITES DEL MODELO\n");
        if (r.advertencias().isEmpty()) sb.append("  (ninguna)\n");
        r.advertencias().forEach(w -> sb.append("  - ").append(w).append('\n'));
        return sb.toString();
    }

    private String parametros(ResultadoExperimento r) {
        var sb = new StringBuilder("variante,clave,valor\n");
        for (Variante v : r.plan().variantes()) {
            v.aplicar(r.base().configuracion()).valores()
                    .forEach((k, valor) -> sb.append(fila(v.nombre(), k, valor)));
        }
        return sb.toString();
    }

    private String flota(ResultadoExperimento r) {
        var sb = new StringBuilder("id_vehiculo,capacidad_qq,velocidad_kmh,costo_por_km,disponible_en_bd\n");
        r.base().vehiculos().forEach(v -> sb.append(fila(
                v.getIdVehiculo(), v.getCapacidadPaquetes(), v.getVelocidadKmh(),
                v.getCostoPorKm(), v.isDisponible())));
        return sb.toString();
    }

    private String almacenes(ResultadoExperimento r) {
        var sb = new StringBuilder("id_almacen,x,y,capacidad_maxima,stock_actual\n");
        r.base().almacenes().forEach(a -> sb.append(fila(
                a.getIdAlmacen(), a.getUbicacion().getX(), a.getUbicacion().getY(),
                a.getCapacidadMaxima(), a.getStockActual())));
        return sb.toString();
    }

    private String instancias(ResultadoExperimento r) {
        var sb = new StringBuilder("escenario,escenario_operativo,perfil_presion,instancia,tamanio_inicial,instante,"+
                "primer_pedido,ultimo_pedido,cantidad_total_qq,capacidad_disponible_qq,prioritarios,"+
                "vehiculos_disponibles,vehiculos_en_mantenimiento,vehiculos_en_baja_por_perfil,bloqueos_programados,bloqueos_activos_al_planificar\n");
        for (Escenario e : r.escenarios()) {
            long prioritarios = e.pedidos().stream().filter(p -> p.getPrioridad().esPriorizado()).count();
            long disponibles = e.vehiculos().stream().filter(v -> v.isDisponible()).count();
            sb.append(fila(
                    e.id(), e.escenarioOperativo(), e.perfilPresion(), e.instancia(), e.tamanio(), e.instante(),
                    e.pedidos().get(0).getFechaLlegada(),
                    e.pedidos().get(e.pedidos().size() - 1).getFechaLlegada(),
                    e.cantidadTotalQq(), e.capacidadDisponibleQq(), prioritarios, disponibles,
                    String.join(" ", e.vehiculosEnMantenimiento()),
                    String.join(" ", e.vehiculosEnBajaPorPerfil()),
                    e.bloqueosProgramados().size(),
                    e.bloqueosActivosEnInstante().size()));
        }
        return sb.toString();
    }

    private String pedidosInstancias(ResultadoExperimento r) {
        var sb = new StringBuilder("escenario,id_pedido,cliente,cantidad_qq,prioridad,horas_limite,llegada,fecha_limite,x,y\n");
        for (Escenario e : r.escenarios()) {
            for (var p : e.pedidos()) {
                sb.append(fila(
                        e.id(), p.getIdPedido(), p.getIdCliente(), p.getCantidadQq(), p.getPrioridad().name(),
                        p.getHorasLimite(), p.getFechaLlegada(), p.getFechaLimite(),
                        p.getUbicacion().getX(), p.getUbicacion().getY()));
            }
        }
        return sb.toString();
    }

    private String bloqueosProgramados(ResultadoExperimento r) {
        var sb = new StringBuilder("escenario,inicio,fin,activo_al_instante_planificacion,vertices\n");
        for (Escenario e : r.escenarios()) {
            for (var b : e.bloqueosProgramados()) {
                sb.append(fila(
                        e.id(), b.inicio(), b.fin(), b.activoEn(e.instante()),
                        b.vertices().stream()
                                .map(n -> "(" + n.getX() + "," + n.getY() + ")")
                                .collect(Collectors.joining(" "))));
            }
        }
        return sb.toString();
    }

    private static final String CABECERA_CORRIDAS =
            "variante,escenario,escenario_operativo,perfil_presion,instancia,repeticion,semilla,algoritmo," +
            "tamanio,tiempo_ms,evaluaciones,F,T_horas,R_horas,N,V,factible,verificada," +
            "cumplimiento_pct,cumplimiento_prioritarios_pct,costo,distancia_km,utilizacion_pct," +
            "temperatura_inicial,pedidos_afectados_incidencia,vehiculos_averia_incidente," +
            "tiempo_replanificacion_ms,replanificacion_exitosa,tamanio_al_colapso,colapso_alcanzado," +
            "tiempo_colapso_horas,tiempo_colapso_formato,instante_colapso\n";

    public void abrirCorridas() {
        crearCarpeta();
        escribir("corridas.csv", CABECERA_CORRIDAS);
    }

    public void agregarCorrida(CorridaRegistrada c) {
        try {
            Files.writeString(
                    carpeta.resolve("corridas.csv"),
                    filaCorrida(c),
                    StandardCharsets.UTF_8,
                    StandardOpenOption.APPEND);
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo escribir corridas.csv", e);
        }
    }

    private String corridas(ResultadoExperimento r) {
        var sb = new StringBuilder(CABECERA_CORRIDAS);
        r.corridas().forEach(c -> sb.append(filaCorrida(c)));
        return sb.toString();
    }

    private static String filaCorrida(CorridaRegistrada c) {
        var k = c.corrida();
        return fila(
                c.variante(), c.escenario(), k.escenarioOperativo(), k.perfilPresion(),
                c.instancia(), c.repeticion(), k.semilla(), k.algoritmo(), k.tamanio(),
                k.tiempoMs(), c.evaluaciones(), k.objetivo(), k.t(), k.r(), k.n(), k.v(),
                k.factible(), c.verificada(), k.cumplimiento(), k.cumplimientoPrioritarios(),
                k.costo(), k.distancia(), k.utilizacion(), k.temperaturaInicial(),
                k.pedidosAfectadosIncidencia(), k.vehiculosEnAveriaIncidente(),
                k.tiempoReplanificacionMs(), k.replanificacionExitosa(),
                k.tamanioAlColapso(), k.colapsoAlcanzado(), k.tiempoColapsoHoras(),
                formatoHoras(k.tiempoColapsoHoras()), k.instanteColapso());
    }

    private String resumen(ResultadoExperimento r) {
        var sb = new StringBuilder(
                "variante,escenario,escenario_operativo,perfil_presion,instancia,algoritmo,tamanio,corridas," +
                "factibles_pct,metrica,media,mediana,desviacion_muestral,mejor,peor\n");
        for (ResumenGrupo g : r.resumenes()) {
            for (var m : g.metricas().entrySet()) {
                Estadistica e = m.getValue();
                sb.append(fila(
                        g.variante(), g.escenario(), g.escenarioOperativo(), g.perfilPresion(),
                        g.instancia(), g.algoritmo(), g.tamanio(), g.corridas(), g.tasaFactibles(),
                        m.getKey(), e.media(), e.mediana(), e.desviacionMuestral(), e.mejor(), e.peor()));
            }
        }
        return sb.toString();
    }

    private String comparacion(ResultadoExperimento r) {
        var sb = new StringBuilder(
                "variante,escenario,escenario_operativo,perfil_presion,instancia,tamanio,algoritmo_A,algoritmo_B," +
                "pares,media_F_A,media_F_B,victorias_A,victorias_B,empates,p_wilcoxon,tamano_efecto_r," +
                "interpretacion_efecto,veredicto,media_evaluaciones_A,media_evaluaciones_B,media_tiempo_ms_A," +
                "media_tiempo_ms_B,p_wilcoxon_presupuesto_igual,veredicto_presupuesto_igual\n");
        for (Comparacion c : r.comparaciones()) {
            sb.append(fila(
                    c.variante(), c.escenario(), c.escenarioOperativo(), c.perfilPresion(), c.instancia(), c.tamanio(),
                    c.algoritmoA(), c.algoritmoB(), c.pares(), c.mediaObjetivoA(), c.mediaObjetivoB(),
                    c.victoriasA(), c.victoriasB(), c.empates(), c.pValor(), c.tamanoEfecto(),
                    c.interpretacionEfecto(), c.veredicto(), c.mediaEvaluacionesA(), c.mediaEvaluacionesB(),
                    c.mediaTiempoMsA(), c.mediaTiempoMsB(), c.pValorPresupuestoIgual(), c.veredictoPresupuestoIgual()));
        }
        return sb.toString();
    }

    private String comparacionMetricas(ResultadoExperimento r) {
        var sb = new StringBuilder(
                "variante,escenario,escenario_operativo,perfil_presion,instancia,tamanio,algoritmo_A,algoritmo_B," +
                "metrica,p_wilcoxon,p_decision_holm_si_aplica,tamano_efecto_r,veredicto\n");
        for (Comparacion c : r.comparaciones()) {
            for (Map.Entry<String, MetricaSecundaria> m : c.metricasSecundarias().entrySet()) {
                MetricaSecundaria v = m.getValue();
                sb.append(fila(
                        c.variante(), c.escenario(), c.escenarioOperativo(), c.perfilPresion(), c.instancia(), c.tamanio(),
                        c.algoritmoA(), c.algoritmoB(), m.getKey(), v.pValor(), v.pValorHolm(),
                        v.tamanoEfecto(), v.veredicto()));
            }
        }
        return sb.toString();
    }

    private String colapso(ResultadoExperimento r) {
        var sb = new StringBuilder(
                "variante,escenario,perfil_presion,instancia,repeticion,algoritmo,colapso_alcanzado," +
                "tiempo_colapso_horas,tiempo_colapso_formato,instante_colapso,tamanio_ultima_jornada\n");
        for (var c : r.corridas()) {
            var k = c.corrida();
            if (!EscenarioOperativo.COLAPSO_LOGISTICO.name().equals(k.escenarioOperativo())) continue;
            sb.append(fila(
                    c.variante(), c.escenario(), k.perfilPresion(), c.instancia(), c.repeticion(),
                    k.algoritmo(), k.colapsoAlcanzado(), k.tiempoColapsoHoras(),
                    formatoHoras(k.tiempoColapsoHoras()), k.instanteColapso(), k.tamanioAlColapso()));
        }
        return sb.toString();
    }

    private String convergencia(ResultadoExperimento r) {
        var sb = new StringBuilder("variante,escenario,algoritmo,repeticion,semilla,iteracion,evaluaciones,mejor_F\n");
        for (var c : r.corridas()) {
            for (var p : c.corrida().convergencia()) {
                sb.append(fila(
                        c.variante(), c.escenario(), c.corrida().algoritmo(), c.repeticion(),
                        c.corrida().semilla(), p.iteracion(), p.evaluaciones(), p.mejorObjetivo()));
            }
        }
        return sb.toString();
    }

    private String rutas(ResultadoExperimento r) {
        var sb = new StringBuilder("variante,escenario,algoritmo,repeticion,vehiculo,almacen_origen,orden,id_pedido\n");
        for (var c : r.corridas()) {
            var k = c.corrida();
            for (var ruta : k.rutas()) {
                int orden = 1;
                for (long pedido : ruta.pedidos()) {
                    sb.append(fila(
                            c.variante(), c.escenario(), k.algoritmo(), c.repeticion(),
                            ruta.vehiculo(), ruta.almacen(), orden++, pedido));
                }
            }
            for (long pedido : k.noAsignados()) {
                sb.append(fila(
                        c.variante(), c.escenario(), k.algoritmo(), c.repeticion(),
                        "NO_ASIGNADO", "", "", pedido));
            }
        }
        return sb.toString();
    }

    public static String resumenTexto(ResultadoExperimento r) {
        var sb = new StringBuilder();
        sb.append("RESUMEN ").append(r.plan().nombre())
                .append("  (").append(r.corridas().size()).append(" corridas)\n");
        long noVerificadas = r.corridas().stream().filter(c -> !c.verificada()).count();
        sb.append("Corridas con verificación fallida: ").append(noVerificadas).append('\n');

        for (Variante v : r.plan().variantes()) {
            sb.append("\nVariante ").append(v.nombre()).append('\n');
            var escenarios = r.resumenes().stream()
                    .filter(g -> g.variante().equals(v.nombre()))
                    .map(ResumenGrupo::escenario)
                    .distinct()
                    .sorted()
                    .toList();

            for (String escenario : escenarios) {
                var referencia = r.resumenes().stream()
                        .filter(g -> g.variante().equals(v.nombre()) && g.escenario().equals(escenario))
                        .findFirst().orElseThrow();

                sb.append(String.format(
                        "  %s | %s | %s | instancia N%02d | tamaño %d%n",
                        referencia.escenario(), referencia.escenarioOperativo(), referencia.perfilPresion(),
                        referencia.instancia(), referencia.tamanio()));

                for (ResumenGrupo g : r.resumenes()) {
                    if (!g.variante().equals(v.nombre()) || !g.escenario().equals(escenario)) continue;
                    var f = g.metricas().get("objetivo");
                    sb.append(String.format(java.util.Locale.ROOT,
                            "    %-18s F=%12.2f  t=%9.1f ms  SLA=%7.2f%%  noAsig=%8.2f  eval=%9.0f",
                            g.algoritmo(), f.media(), g.metricas().get("tiempoMs").media(),
                            g.metricas().get("cumplimientoPct").media(),
                            g.metricas().get("noAsignados").media(),
                            g.metricas().get("evaluaciones").media()));
                    var tc = g.metricas().get("tiempoColapsoHoras");
                    if (tc != null) {
                        sb.append("  colapso mediano=").append(formatoHoras(tc.mediana()));
                    }
                    sb.append('\n');
                }

                for (Comparacion c : r.comparaciones()) {
                    if (!c.variante().equals(v.nombre()) || !c.escenario().equals(escenario)) continue;
                    sb.append(String.format(java.util.Locale.ROOT,
                            "    %s vs %s: F p=%s r=%s (%s) -> %s%n",
                            c.algoritmoA(), c.algoritmoB(), p(c.pValor()), p(c.tamanoEfecto()),
                            c.interpretacionEfecto(), c.veredicto()));

                    var sla = c.metricasSecundarias().get("cumplimientoPlazosPct");
                    if (sla != null) {
                        sb.append(String.format(java.util.Locale.ROOT,
                                "      SLA: p=%s r=%s -> %s%n",
                                p(sla.pValor()), p(sla.tamanoEfecto()), sla.veredicto()));
                    }
                    var col = c.metricasSecundarias().get("tiempoColapsoHoras");
                    if (col != null) {
                        sb.append(String.format(java.util.Locale.ROOT,
                                "      tiempo hasta colapso: p=%s r=%s -> %s%n",
                                p(col.pValor()), p(col.tamanoEfecto()), col.veredicto()));
                    }
                }
            }
        }

        sb.append("\nGanadores por F con Wilcoxon al 5% (excluye colapso, donde F final no es comparable): ")
                .append(tableroFComparable(r.comparaciones())).append('\n');
        sb.append("SLA no usa ajuste Holm; tiempo hasta colapso tampoco. Las demás métricas secundarias sí.\n");
        return sb.toString();
    }


    private static Map<String, Integer> tableroFComparable(List<Comparacion> comparaciones) {
        Map<String, Integer> cuenta = new TreeMap<>();
        for (Comparacion c : comparaciones) {
            if (Double.isNaN(c.pValor())) continue;
            String v = c.veredicto();
            cuenta.merge(v.startsWith("GANA_") ? v : "SIN_GANADOR", 1, Integer::sum);
        }
        return cuenta;
    }

    private static Map<String, Integer> tablero(List<Comparacion> comparaciones, boolean presupuestoIgual) {
        Map<String, Integer> cuenta = new TreeMap<>();
        for (Comparacion c : comparaciones) {
            String v = presupuestoIgual ? c.veredictoPresupuestoIgual() : c.veredicto();
            cuenta.merge(v.startsWith("GANA_") ? v : "SIN_GANADOR", 1, Integer::sum);
        }
        return cuenta;
    }

    private static String p(double valor) {
        return Double.isNaN(valor) ? "n/d" : String.format(java.util.Locale.ROOT, "%.4f", valor);
    }

    private void crearCarpeta() {
        try {
            Files.createDirectories(carpeta);
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo crear " + carpeta, e);
        }
    }

    private void escribir(String nombre, String contenido) {
        try {
            Files.writeString(carpeta.resolve(nombre), contenido, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo escribir " + nombre, e);
        }
    }

    private static String fila(Object... campos) {
        List<String> salida = new ArrayList<>();
        for (Object campo : campos) salida.add(texto(campo));
        return String.join(",", salida) + "\n";
    }

    private static String texto(Object valor) {
        if (valor == null) return "";
        if (valor instanceof Double d) return numero(d);
        if (valor instanceof Float f) return numero(f.doubleValue());
        String s = valor.toString();
        return s.contains(",") || s.contains("\"") || s.contains("\n")
                ? "\"" + s.replace("\"", "\"\"") + "\""
                : s;
    }

    private static String numero(double d) {
        if (Double.isNaN(d) || Double.isInfinite(d)) return "";
        return BigDecimal.valueOf(d)
                .setScale(6, RoundingMode.HALF_UP)
                .stripTrailingZeros()
                .toPlainString();
    }

    private static String duracion(Duration d) {
        return String.format("%02d:%02d:%02d", d.toHours(), d.toMinutesPart(), d.toSecondsPart());
    }

    private static String formatoHoras(Double horas) {
        if (horas == null || !Double.isFinite(horas)) return "";
        long minutos = Math.round(horas * 60.0);
        long dias = minutos / (24 * 60);
        long resto = minutos % (24 * 60);
        long h = resto / 60;
        long m = resto % 60;
        return String.format("%dd%02dh%02dmin", dias, h, m);
    }
}