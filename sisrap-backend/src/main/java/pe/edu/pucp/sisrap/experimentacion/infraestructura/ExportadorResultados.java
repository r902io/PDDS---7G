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
import pe.edu.pucp.sisrap.experimentacion.dominio.InformeExperimento.Estadistica;
import pe.edu.pucp.sisrap.experimentacion.dominio.ResultadoExperimento;
import pe.edu.pucp.sisrap.experimentacion.dominio.ResultadoExperimento.Comparacion;
import pe.edu.pucp.sisrap.experimentacion.dominio.ResultadoExperimento.Comparacion.MetricaSecundaria;
import pe.edu.pucp.sisrap.experimentacion.dominio.ResultadoExperimento.CorridaRegistrada;
import pe.edu.pucp.sisrap.experimentacion.dominio.ResultadoExperimento.PuntoColapso;
import pe.edu.pucp.sisrap.experimentacion.dominio.ResultadoExperimento.ResumenGrupo;
import pe.edu.pucp.sisrap.experimentacion.dominio.Variante;

/**
 * Escribe TODO lo recopilado en una carpeta por experimento, en CSV (coma, punto decimal, UTF-8)
 * para abrirlo en Excel, pandas o R sin más trámite.
 */
public final class ExportadorResultados {
    private final Path carpeta;

    public ExportadorResultados(Path carpeta) {
        this.carpeta = carpeta;
    }

    /** Insumos del experimento (parámetros, flota, instancias, datos): existen aunque no se haya corrido nada. */
    public void escribirInsumos(ResultadoExperimento r) {
        crearCarpeta();
        escribir("manifiesto.txt", manifiesto(r));
        escribir("parametros.csv", parametros(r));
        escribir("flota.csv", flota(r));
        escribir("almacenes.csv", almacenes(r));
        escribir("instancias.csv", instancias(r));
        escribir("pedidos_instancias.csv", pedidosInstancias(r));
        escribir("bloqueos_activos.csv", bloqueosActivos(r));
    }

    public void escribirResultados(ResultadoExperimento r, boolean detalle) {
        escribirInsumos(r);
        escribir("corridas.csv", corridas(r));
        escribir("resumen.csv", resumen(r));
        escribir("comparacion.csv", comparacion(r));
        escribir("comparacion_metricas_secundarias.csv", comparacionMetricasSecundarias(r));
        escribir("colapso.csv", colapso(r));
        escribir("resumen.txt", resumenTexto(r));
        if (detalle) {
            escribir("convergencia.csv", convergencia(r));
            escribir("rutas.csv", rutas(r));
        }
    }

    // ---------------------------------------------------------------- insumos

    private String manifiesto(ResultadoExperimento r) {
        var p = r.plan();
        var sb = new StringBuilder();
        sb.append("EXPERIMENTO ").append(p.nombre()).append('\n');
        sb.append("Inicio: ").append(r.inicio()).append("  Fin: ").append(r.fin())
                .append("  Duración: ").append(duracion(Duration.between(r.inicio(), r.fin()))).append('\n');
        sb.append("Entorno: Java ").append(System.getProperty("java.version")).append(", ").append(System.getProperty("os.name"))
                .append(", ").append(Runtime.getRuntime().availableProcessors()).append(" procesadores\n\n");
        sb.append("PLAN (informe \"Diseño de Experimento\", sección 3.4)\n");
        sb.append("  perfil de parámetros (BD): ").append(p.perfil()).append('\n');
        sb.append("  algoritmos: ").append(p.algoritmos()).append('\n');
        sb.append("  escenarios operativos: ").append(p.escenariosOperativos()).append('\n');
        sb.append("  perfiles de presión: ").append(p.perfiles()).append('\n');
        sb.append("  tamaño base diario: ").append(p.tamanioBaseDiario()).append("  niveles/instancias por combinación: ").append(p.instancias()).append('\n');
        sb.append("  repeticiones: ").append(p.repeticiones()).append("  semilla base: ").append(p.semillaBase())
                .append(" (semillas ").append(p.semillaBase()).append(" a ").append(p.semillaBase() + p.repeticiones() - 1).append(")\n");
        sb.append("  pedidos desde: ").append(p.desde()).append("  mantenimiento preventivo aplicado: ").append(p.aplicarMantenimiento())
                .append("  calentamiento JVM: ").append(p.calentamiento()).append('\n');
        sb.append("  total de corridas: ").append(p.totalCorridas()).append('\n');
        for (Variante v : p.variantes())
            sb.append("  variante ").append(v.nombre()).append(": ").append(v.sobrescrituras().isEmpty() ? "(perfil tal cual)" : v.sobrescrituras()).append('\n');
        var a = r.archivos();
        sb.append("\nDATOS RECOPILADOS\n");
        sb.append("  pedidos leídos: ").append(a.pedidos().size());
        if (!a.pedidos().isEmpty())
            sb.append(" (del ").append(a.pedidos().get(0).llegada()).append(" al ").append(a.pedidos().get(a.pedidos().size() - 1).llegada()).append(')');
        sb.append('\n');
        Map<String, Long> porPrioridad = a.pedidos().stream().collect(Collectors.groupingBy(x -> x.prioridad().name(), TreeMap::new, Collectors.counting()));
        sb.append("  por prioridad: ").append(porPrioridad).append('\n');
        sb.append("  bloqueos leídos: ").append(a.bloqueos().size()).append('\n');
        sb.append("  días con mantenimiento preventivo: ").append(a.mantenimiento().size());
        if (!a.mantenimiento().isEmpty())
            sb.append(" (").append(new TreeMap<>(a.mantenimiento()).firstKey()).append(" a ").append(new TreeMap<>(a.mantenimiento()).lastKey()).append(')');
        sb.append('\n');
        sb.append("  archivos leídos (").append(a.archivosLeidos().size()).append("):\n");
        a.archivosLeidos().forEach(f -> sb.append("    ").append(f).append('\n'));
        sb.append("\nSUPUESTO DE INCIDENCIAS (OPERACION_DIARIA, perfiles ALTA/CRITICA)\n");
        sb.append("  El modelo del planificador es estático (sin reloj de simulación dentro de una corrida). Los\n");
        sb.append("  \"pedidos aún no atendidos\" al momento de la incidencia se aproximan como un subconjunto\n");
        sb.append("  aleatorio de los pedidos ya asignados en la solución inicial (ver SimuladorIncidencias),\n");
        sb.append("  en la proporción y con las averías de vehículo que define el perfil de presión.\n");
        sb.append("\nADVERTENCIAS Y LÍMITES DEL MODELO\n");
        if (r.advertencias().isEmpty()) sb.append("  (ninguna)\n");
        r.advertencias().forEach(w -> sb.append("  - ").append(w).append('\n'));
        return sb.toString();
    }

    private String parametros(ResultadoExperimento r) {
        var sb = new StringBuilder("variante,clave,valor\n");
        for (Variante v : r.plan().variantes())
            v.aplicar(r.base().configuracion()).valores().forEach((k, valor) -> sb.append(fila(v.nombre(), k, valor)));
        return sb.toString();
    }

    private String flota(ResultadoExperimento r) {
        var sb = new StringBuilder("id_vehiculo,capacidad_qq,velocidad_kmh,costo_por_km,disponible_en_bd\n");
        r.base().vehiculos().forEach(v -> sb.append(fila(v.getIdVehiculo(), v.getCapacidadPaquetes(), v.getVelocidadKmh(), v.getCostoPorKm(), v.isDisponible())));
        return sb.toString();
    }

    private String almacenes(ResultadoExperimento r) {
        var sb = new StringBuilder("id_almacen,x,y,capacidad_maxima,stock_actual\n");
        r.base().almacenes().forEach(a -> sb.append(fila(a.getIdAlmacen(), a.getUbicacion().getX(), a.getUbicacion().getY(),
                a.getCapacidadMaxima(), a.getStockActual())));
        return sb.toString();
    }

    private String instancias(ResultadoExperimento r) {
        var sb = new StringBuilder("escenario,escenario_operativo,perfil_presion,tamanio,nivel,instante,primer_pedido,ultimo_pedido,"
                + "cantidad_total_qq,capacidad_disponible_qq,ratio_demanda_capacidad,prioritarios,vehiculos_disponibles,"
                + "vehiculos_en_mantenimiento,vehiculos_en_baja_por_perfil,bloqueos_activos\n");
        for (Escenario e : r.escenarios()) {
            long prioritarios = e.pedidos().stream().filter(p -> p.getPrioridad().esPriorizado()).count();
            long disponibles = e.vehiculos().stream().filter(v -> v.isDisponible()).count();
            double ratio = e.capacidadDisponibleQq() == 0 ? Double.NaN : (double) e.cantidadTotalQq() / e.capacidadDisponibleQq();
            sb.append(fila(e.id(), e.escenarioOperativo(), e.perfilPresion(), e.tamanio(), e.nivel(), e.instante(),
                    e.pedidos().get(0).getFechaLlegada(), e.pedidos().get(e.pedidos().size() - 1).getFechaLlegada(),
                    e.cantidadTotalQq(), e.capacidadDisponibleQq(), ratio, prioritarios, disponibles,
                    String.join(" ", e.vehiculosEnMantenimiento()), String.join(" ", e.vehiculosEnBajaPorPerfil()), e.bloqueosActivos().size()));
        }
        return sb.toString();
    }

    private String pedidosInstancias(ResultadoExperimento r) {
        var sb = new StringBuilder("escenario,id_pedido,cliente,cantidad_qq,prioridad,horas_limite,llegada,fecha_limite,x,y\n");
        for (Escenario e : r.escenarios())
            for (var p : e.pedidos())
                sb.append(fila(e.id(), p.getIdPedido(), p.getIdCliente(), p.getCantidadQq(), p.getPrioridad().name(), p.getHorasLimite(),
                        p.getFechaLlegada(), p.getFechaLimite(), p.getUbicacion().getX(), p.getUbicacion().getY()));
        return sb.toString();
    }

    private String bloqueosActivos(ResultadoExperimento r) {
        var sb = new StringBuilder("escenario,inicio,fin,vertices\n");
        for (Escenario e : r.escenarios())
            for (var b : e.bloqueosActivos())
                sb.append(fila(e.id(), b.inicio(), b.fin(),
                        b.vertices().stream().map(n -> "(" + n.getX() + "," + n.getY() + ")").collect(Collectors.joining(" "))));
        return sb.toString();
    }

    // ------------------------------------------------------------- resultados

    private static final String CABECERA_CORRIDAS = "variante,escenario,escenario_operativo,perfil_presion,tamanio,nivel,repeticion,semilla,algoritmo,"
            + "tiempo_ms,evaluaciones,F,T_horas,R_horas,N,V,factible,verificada,cumplimiento_pct,cumplimiento_prioritarios_pct,"
            + "costo,distancia_km,utilizacion_pct,temperatura_inicial,pedidos_afectados_incidencia,vehiculos_averia_incidente,"
            + "tiempo_replanificacion_ms,replanificacion_exitosa\n";

    /** Crea corridas.csv solo con la cabecera; luego cada corrida se agrega con {@link #agregarCorrida}. */
    public void abrirCorridas() {
        crearCarpeta();
        escribir("corridas.csv", CABECERA_CORRIDAS);
    }

    /** Guarda la corrida de inmediato: si el proceso se interrumpe, lo ya corrido no se pierde. */
    public void agregarCorrida(CorridaRegistrada c) {
        try {
            Files.writeString(carpeta.resolve("corridas.csv"), filaCorrida(c), StandardCharsets.UTF_8, StandardOpenOption.APPEND);
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
        return fila(c.variante(), c.escenario(), k.escenarioOperativo(), k.perfilPresion(), k.tamanio(), c.instancia(), c.repeticion(),
                k.semilla(), k.algoritmo(), k.tiempoMs(), c.evaluaciones(), k.objetivo(), k.t(), k.r(), k.n(), k.v(), k.factible(),
                c.verificada(), k.cumplimiento(), k.cumplimientoPrioritarios(), k.costo(), k.distancia(), k.utilizacion(),
                k.temperaturaInicial(), k.pedidosAfectadosIncidencia(), k.vehiculosEnAveriaIncidente(),
                k.tiempoReplanificacionMs(), k.replanificacionExitosa());
    }

    private String resumen(ResultadoExperimento r) {
        var sb = new StringBuilder("variante,escenario,escenario_operativo,perfil_presion,nivel,algoritmo,tamanio,corridas,factibles_pct,metrica,media,mediana,desviacion_muestral,mejor,peor\n");
        for (ResumenGrupo g : r.resumenes())
            for (var m : g.metricas().entrySet()) {
                Estadistica e = m.getValue();
                sb.append(fila(g.variante(), g.escenario(), g.escenarioOperativo(), g.perfilPresion(), g.nivel(),
                        g.algoritmo(), g.tamanio(), g.corridas(), g.tasaFactibles(), m.getKey(),
                        e.media(), e.mediana(), e.desviacionMuestral(), e.mejor(), e.peor()));
            }
        return sb.toString();
    }

    private String comparacion(ResultadoExperimento r) {
        var sb = new StringBuilder("variante,escenario,escenario_operativo,perfil_presion,nivel,tamanio,algoritmo_A,algoritmo_B,pares,media_F_A,media_F_B,victorias_A,victorias_B,empates,"
                + "p_wilcoxon,tamano_efecto_r,interpretacion_efecto,veredicto,media_evaluaciones_A,media_evaluaciones_B,media_tiempo_ms_A,"
                + "media_tiempo_ms_B,p_wilcoxon_presupuesto_igual,veredicto_presupuesto_igual\n");
        for (Comparacion c : r.comparaciones())
            sb.append(fila(c.variante(), c.escenario(), c.escenarioOperativo(), c.perfilPresion(), c.nivel(), c.tamanio(),
                    c.algoritmoA(), c.algoritmoB(), c.pares(), c.mediaObjetivoA(), c.mediaObjetivoB(),
                    c.victoriasA(), c.victoriasB(), c.empates(), c.pValor(), c.tamanoEfecto(), c.interpretacionEfecto(), c.veredicto(),
                    c.mediaEvaluacionesA(), c.mediaEvaluacionesB(), c.mediaTiempoMsA(), c.mediaTiempoMsB(),
                    c.pValorPresupuestoIgual(), c.veredictoPresupuestoIgual()));
        return sb.toString();
    }

    /** Comparación pareada por métrica secundaria (cumplimiento de plazos, retraso, costo, tiempo, no asignados), con p ajustado por Holm. */
    private String comparacionMetricasSecundarias(ResultadoExperimento r) {
        var sb = new StringBuilder("variante,escenario,escenario_operativo,perfil_presion,nivel,tamanio,algoritmo_A,algoritmo_B,metrica,p_wilcoxon,p_wilcoxon_holm,tamano_efecto_r,veredicto\n");
        for (Comparacion c : r.comparaciones())
            for (Map.Entry<String, MetricaSecundaria> m : c.metricasSecundarias().entrySet()) {
                MetricaSecundaria v = m.getValue();
                sb.append(fila(c.variante(), c.escenario(), c.escenarioOperativo(), c.perfilPresion(), c.nivel(), c.tamanio(),
                        c.algoritmoA(), c.algoritmoB(), m.getKey(),
                        v.pValor(), v.pValorHolm(), v.tamanoEfecto(), v.veredicto()));
            }
        return sb.toString();
    }

    private String colapso(ResultadoExperimento r) {
        var sb = new StringBuilder("variante,algoritmo,nivel_colapso,tasa_no_atendidos_en_ese_nivel_pct\n");
        for (PuntoColapso c : r.puntosColapso())
            sb.append(fila(c.variante(), c.algoritmo(), c.nivelColapso(), c.tasaNoAtendidosPct()));
        return sb.toString();
    }

    private String convergencia(ResultadoExperimento r) {
        var sb = new StringBuilder("variante,escenario,algoritmo,repeticion,semilla,iteracion,evaluaciones,mejor_F\n");
        for (var c : r.corridas())
            for (var p : c.corrida().convergencia())
                sb.append(fila(c.variante(), c.escenario(), c.corrida().algoritmo(), c.repeticion(), c.corrida().semilla(),
                        p.iteracion(), p.evaluaciones(), p.mejorObjetivo()));
        return sb.toString();
    }

    private String rutas(ResultadoExperimento r) {
        var sb = new StringBuilder("variante,escenario,algoritmo,repeticion,vehiculo,almacen_origen,orden,id_pedido\n");
        for (var c : r.corridas()) {
            var k = c.corrida();
            for (var ruta : k.rutas()) {
                int orden = 1;
                for (long pedido : ruta.pedidos())
                    sb.append(fila(c.variante(), c.escenario(), k.algoritmo(), c.repeticion(), ruta.vehiculo(), ruta.almacen(), orden++, pedido));
            }
            for (long pedido : k.noAsignados())
                sb.append(fila(c.variante(), c.escenario(), k.algoritmo(), c.repeticion(), "NO_ASIGNADO", "", "", pedido));
        }
        return sb.toString();
    }

    /** Resumen legible: se escribe en resumen.txt y se imprime en consola. */
    public static String resumenTexto(ResultadoExperimento r) {
        var sb = new StringBuilder();
        sb.append("RESUMEN ").append(r.plan().nombre()).append("  (").append(r.corridas().size()).append(" corridas)\n");
        long noVerificadas = r.corridas().stream().filter(c -> !c.verificada()).count();
        sb.append("Corridas con verificación fallida: ").append(noVerificadas).append('\n');
        for (Variante v : r.plan().variantes()) {
            sb.append("\nVariante ").append(v.nombre()).append('\n');
            var escenarios = r.resumenes().stream().filter(g -> g.variante().equals(v.nombre()))
                    .map(ResumenGrupo::escenario).distinct().sorted().toList();
            for (String escenario : escenarios) {
                var referencia = r.resumenes().stream().filter(g -> g.variante().equals(v.nombre()) && g.escenario().equals(escenario))
                        .findFirst().orElseThrow();
                sb.append(String.format("  %s | %s | %s | nivel %d | tamaño %d%n    %-18s %12s %12s %10s %10s %10s %10s%n",
                        referencia.escenario(), referencia.escenarioOperativo(), referencia.perfilPresion(), referencia.nivel(),
                        referencia.tamanio(), "algoritmo", "F media", "F desv.", "t ms", "cumpl. %", "no asig.", "eval."));
                for (ResumenGrupo g : r.resumenes()) {
                    if (!g.variante().equals(v.nombre()) || !g.escenario().equals(escenario)) continue;
                    var f = g.metricas().get("objetivo");
                    sb.append(String.format(java.util.Locale.ROOT, "    %-18s %12.2f %12s %10.1f %10.2f %10.2f %10.0f%n", g.algoritmo(), f.media(),
                            f.desviacionMuestral() == null ? "-" : String.format(java.util.Locale.ROOT, "%.2f", f.desviacionMuestral()),
                            g.metricas().get("tiempoMs").media(), g.metricas().get("cumplimientoPct").media(),
                            g.metricas().get("noAsignados").media(), g.metricas().get("evaluaciones").media()));
                }
                for (Comparacion c : r.comparaciones()) {
                    if (!c.variante().equals(v.nombre()) || !c.escenario().equals(escenario)) continue;
                    sb.append(String.format(java.util.Locale.ROOT, "    %s vs %s: %d pares, victorias %d-%d (empates %d), p=%s r=%s (%s) -> %s | a presupuesto igual: p=%s -> %s%n",
                            c.algoritmoA(), c.algoritmoB(), c.pares(), c.victoriasA(), c.victoriasB(), c.empates(), p(c.pValor()),
                            p(c.tamanoEfecto()), c.interpretacionEfecto(), c.veredicto(), p(c.pValorPresupuestoIgual()), c.veredictoPresupuestoIgual()));
                    var cumplimiento = c.metricasSecundarias().get("cumplimientoPlazosPct");
                    if (cumplimiento != null)
                        sb.append(String.format(java.util.Locale.ROOT, "      cumplimiento de plazos (métrica primaria, sección 3.3): p=%s p_holm=%s r=%s -> %s%n",
                                p(cumplimiento.pValor()), p(cumplimiento.pValorHolm()), p(cumplimiento.tamanoEfecto()), cumplimiento.veredicto()));
                }
            }
        }
        if (!r.puntosColapso().isEmpty()) {
            sb.append("\nPunto de colapso (>50% de repeticiones con pedidos no atendidos, sección 3.4):\n");
            for (PuntoColapso c : r.puntosColapso())
                sb.append(String.format("  %s / %s: nivel %s (tasa %.1f%%)%n", c.variante(), c.algoritmo(), c.nivelColapso(), c.tasaNoAtendidosPct()));
        }
        sb.append("\nGanadores por condición experimental (variante, escenario, perfil y nivel) según F, con Wilcoxon al 5%: ").append(tablero(r.comparaciones(), false)).append('\n');
        sb.append("Ganadores a presupuesto igual de evaluaciones: ").append(tablero(r.comparaciones(), true)).append('\n');
        sb.append("\nLa comparación entre variantes que cambian objetivo.* no es válida sobre F.\n");
        return sb.toString();
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

    // ------------------------------------------------------------------ util

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
        return s.contains(",") || s.contains("\"") || s.contains("\n") ? "\"" + s.replace("\"", "\"\"") + "\"" : s;
    }

    private static String numero(double d) {
        if (Double.isNaN(d) || Double.isInfinite(d)) return "";
        return BigDecimal.valueOf(d).setScale(6, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }

    private static String duracion(Duration d) {
        return String.format("%02d:%02d:%02d", d.toHours(), d.toMinutesPart(), d.toSecondsPart());
    }
}
