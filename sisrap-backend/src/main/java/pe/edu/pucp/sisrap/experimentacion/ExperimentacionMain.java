package pe.edu.pucp.sisrap.experimentacion;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import pe.edu.pucp.sisrap.carga.dominio.ReglasCarga;
import pe.edu.pucp.sisrap.experimentacion.aplicacion.CorredorExperimento;
import pe.edu.pucp.sisrap.experimentacion.aplicacion.GeneradorEscenarios;
import pe.edu.pucp.sisrap.experimentacion.aplicacion.ModeloBloqueosHistoricos;
import pe.edu.pucp.sisrap.experimentacion.aplicacion.ModeloDemandaHistorica;
import pe.edu.pucp.sisrap.experimentacion.aplicacion.ValidadorPreExperimento;
import pe.edu.pucp.sisrap.experimentacion.dominio.BaseOperativa;
import pe.edu.pucp.sisrap.experimentacion.dominio.DatosArchivos;
import pe.edu.pucp.sisrap.experimentacion.dominio.Escenario;
import pe.edu.pucp.sisrap.experimentacion.dominio.EscenarioOperativo;
import pe.edu.pucp.sisrap.experimentacion.dominio.PerfilPresion;
import pe.edu.pucp.sisrap.experimentacion.dominio.PlanExperimento;
import pe.edu.pucp.sisrap.experimentacion.dominio.ResultadoExperimento;
import pe.edu.pucp.sisrap.experimentacion.dominio.Variante;
import pe.edu.pucp.sisrap.experimentacion.infraestructura.ConexionDirecta;
import pe.edu.pucp.sisrap.experimentacion.infraestructura.ExportadorResultados;
import pe.edu.pucp.sisrap.experimentacion.infraestructura.JdbcBaseOperativa;
import pe.edu.pucp.sisrap.experimentacion.infraestructura.LectorArchivosExperimento;
import pe.edu.pucp.sisrap.experimentacion.infraestructura.VariablesEntorno;
import pe.edu.pucp.sisrap.parametros.dominio.Configuracion;
import pe.edu.pucp.sisrap.pedido.dominio.TipoPrioridad;

/*
 - Para verificar  los datos de la corrida:
 .\mvnw.cmd spring-boot:run "-Dspring-boot.run.main-class=pe.edu.pucp.sisrap.experimentacion.ExperimentacionMain" "-Dspring-boot.run.arguments=--perfil=BASE --datos=datos --salida=resultados --solo-datos"

 - ejecutar la experimenación completa:
 .\mvnw.cmd spring-boot:run "-Dspring-boot.run.main-class=pe.edu.pucp.sisrap.experimentacion.ExperimentacionMain" "-Dspring-boot.run.arguments=--perfil=BASE --datos=datos --salida=resultados --instancias=3 --repeticiones=30"
 */
public final class ExperimentacionMain {
    static final String AYUDA = """
            Uso: ExperimentacionMain [opciones]
              --perfil=BASE            perfil de parámetros en la BD (tabla perfil_parametro)
              --nombre=<texto>         nombre del experimento (por defecto exp-AAAAMMDD-HHMMSS)
              --datos=datos            carpeta con ventas, bloqueos y mantenimiento (.txt)
              --salida=resultados      carpeta donde se crea resultados/<nombre>/
              --desde=AAAA-MM-DD       primer día del periodo histórico de calibración (por defecto, primer día disponible)
              --algoritmos=GENETICO,RECOCIDO_SIMULADO
              --instancias=3           instancias distintas N01/N02/N03 por escenario y perfil
              --repeticiones=30        por defecto experimento.repeticiones de la BD
              --semilla=42             por defecto experimento.semillaBase de la BD
              --variante=NOMBRE:clave=valor;clave=valor   (repetible) además de BASE, prueba parámetros sobrescritos
              --sin-mantenimiento      no marcar vehículos en mantenimiento preventivo
              --sin-calentamiento      no hacer la corrida de calentamiento de la JVM
              --sin-detalle            no escribir convergencia.csv ni rutas.csv
              --prueba                 corrida rápida de humo: menor tamaño, 1 instancia, 2 repeticiones
              --solo-datos             recopila y valida los insumos, escribe los CSV de insumos y NO corre algoritmos
              --ayuda
            Ejemplo: --desde=2026-09-01 --instancias=3 --variante=GA_POB100:ga.poblacion=100
            """;

    private static final Set<String> CON_VALOR = Set.of("perfil", "nombre", "datos", "salida", "desde", "algoritmos",
            "instancias", "repeticiones", "semilla", "variante");
    private static final Set<String> BANDERAS = Set.of("sin-mantenimiento", "sin-calentamiento", "sin-detalle", "prueba", "solo-datos", "ayuda");

    private ExperimentacionMain() {}

    public static void main(String[] args) {
        try {
            ejecutar(args);
        } catch (IllegalArgumentException | IllegalStateException e) {
            System.err.println("ERROR: " + e.getMessage());
            if (e.getCause() != null) System.err.println("Causa: " + e.getCause());
            System.exit(1);
        }
    }

    static void ejecutar(String[] args) {
        Opciones opciones = Opciones.leer(args);
        if (opciones.bandera("ayuda")) {
            System.out.println(AYUDA);
            return;
        }

        // 1) Base de datos: parámetros + ciudad + almacenes + flota
        var entorno = new VariablesEntorno(Path.of(".env"));
        var fuente = new ConexionDirecta(entorno.requerida("DB_URL"), entorno.requerida("DB_USERNAME"), entorno.requerida("DB_PASSWORD"));
        String perfil = opciones.valor("perfil", "BASE");
        BaseOperativa base = new JdbcBaseOperativa(fuente).cargar(perfil);
        System.out.printf("BD: perfil %s (%d parámetros), ciudad %dx%d km, %d almacenes, %d vehículos%n", perfil,
                base.configuracion().valores().size(), base.anchoKm(), base.altoKm(), base.almacenes().size(), base.vehiculos().size());

        // 2) Archivos históricos y estimación de presión logística
        var lector = new LectorArchivosExperimento(Path.of(opciones.valor("datos", "datos")), reglasCarga(base));
        LocalDate desde = opciones.tieneValor("desde")
                ? LocalDate.parse(opciones.valor("desde", ""))
                : lector.primerDiaDisponible();
        DatosArchivos datos = lector.leer(desde, Integer.MAX_VALUE);
        var demanda = ModeloDemandaHistorica.analizar(datos, desde);
        var bloqueosHistoricos = ModeloBloqueosHistoricos.analizar(
                datos, demanda.desde(), demanda.hasta());

        System.out.printf("Archivos: %d pedidos, %d bloqueos, %d días con mantenimiento preventivo (%d archivos leídos)%n",
                datos.pedidos().size(), datos.bloqueos().size(), datos.mantenimiento().size(), datos.archivosLeidos().size());
        System.out.printf("Demanda histórica base %s a %s (%d días): P50=%d, P75=%d, P90=%d, incremento colapso=%d pedidos/día%n",
                demanda.desde(), demanda.hasta(), demanda.dias(), demanda.p50(), demanda.p75(), demanda.p90(), demanda.incrementoColapso());
        System.out.printf("Bloqueos históricos base %s a %s (%d días): P50=%d, P75=%d, P90=%d bloqueos/día, %d plantillas%n",
                bloqueosHistoricos.desde(), bloqueosHistoricos.hasta(), bloqueosHistoricos.dias(),
                bloqueosHistoricos.p50(), bloqueosHistoricos.p75(), bloqueosHistoricos.p90(),
                bloqueosHistoricos.plantillasDisponibles());

        // 3) Plan experimental. Los perfiles de demanda salen de los percentiles históricos.
        PlanExperimento plan = construirPlan(opciones, base.configuracion(), desde, demanda);
        System.out.printf("Plan %s: %d corridas (%d variantes x %d escenarios x %d perfiles x %d instancias x %d repeticiones x %d algoritmos)%n",
                plan.nombre(), plan.totalCorridas(), plan.variantes().size(), plan.escenariosOperativos().size(), plan.perfiles().size(),
                plan.instancias(), plan.repeticiones(), plan.algoritmos().size());

        // 4) Instancias
        List<String> advertencias = new ArrayList<>(datos.advertencias());
        advertencias.addAll(limitesDelModelo(datos));
        List<Escenario> escenarios = GeneradorEscenarios.generar(
                plan, base, datos, demanda, bloqueosHistoricos, advertencias);
        
        ValidadorPreExperimento.validar(plan, base, escenarios);

        System.out.println("Validación pre-experimento: OK");
        escenarios.forEach(e -> System.out.printf(
                "  %s: %d pedidos (%d qq) vs capacidad %d qq, %d vehículos en mantenimiento, %d bloqueos programados%n",
                e.id(),
                e.tamanio(),
                e.cantidadTotalQq(),
                e.capacidadDisponibleQq(),
                e.vehiculosEnMantenimiento().size(),
                e.bloqueosProgramados().size()));

        Path carpetaSalida = carpetaSalida(opciones, plan);
        var exportador = new ExportadorResultados(carpetaSalida);
        var ahora = LocalDateTime.now();
        // Los insumos (parámetros, flota, instancias, pedidos) se guardan antes de correr: quedan aunque el proceso se interrumpa.
        exportador.escribirInsumos(new ResultadoExperimento(plan, base, datos, escenarios, List.of(), List.of(), List.of(), advertencias, ahora, ahora));
        if (opciones.bandera("solo-datos")) {
            System.out.println("Insumos escritos en " + carpetaSalida.toAbsolutePath() + " (no se corrió ningún algoritmo)");
            return;
        }

        // 5) Corridas (cada una se agrega a corridas.csv al terminar) + exportación final
        exportador.abrirCorridas();
        var corredor = new CorredorExperimento(System.out::println, exportador::agregarCorrida);
        ResultadoExperimento resultado = corredor.correr(plan, base, datos, escenarios, advertencias);
        exportador.escribirResultados(resultado, !opciones.bandera("sin-detalle"));
        System.out.println();
        System.out.println(ExportadorResultados.resumenTexto(resultado));
        System.out.println("Resultados en " + carpetaSalida.toAbsolutePath());
    }

    private static PlanExperimento construirPlan(Opciones o,
                                                   Configuracion cfg,
                                                   LocalDate desde,
                                                   ModeloDemandaHistorica.Resumen demanda) {
        int instancias = Integer.parseInt(o.valor("instancias", "3"));
        int repeticiones = o.tieneValor("repeticiones")
                ? Integer.parseInt(o.valor("repeticiones", ""))
                : cfg.entero("experimento.repeticiones");
        if (o.bandera("prueba")) {
            instancias = 1;
            repeticiones = Math.min(repeticiones, 2);
        }

        List<Variante> variantes = new ArrayList<>(List.of(Variante.base()));
        o.valores("variante").forEach(v -> variantes.add(Variante.parsear(v)));
        String nombre = o.valor("nombre",
                "exp-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")));

        return new PlanExperimento(
                nombre,
                o.valor("perfil", "BASE"),
                Arrays.stream(o.valor("algoritmos", "GENETICO,RECOCIDO_SIMULADO").split(","))
                        .map(String::trim)
                        .toList(),
                List.of(EscenarioOperativo.values()),
                List.of(PerfilPresion.values()),
                demanda.p50(),
                demanda.p75(),
                demanda.p90(),
                demanda.incrementoColapso(),
                instancias,
                repeticiones,
                o.tieneValor("semilla")
                        ? Long.parseLong(o.valor("semilla", ""))
                        : cfg.largo("experimento.semillaBase"),
                desde,
                !o.bandera("sin-mantenimiento"),
                !o.bandera("sin-calentamiento"),
                variantes);
    }

    private static Path carpetaSalida(Opciones o, PlanExperimento plan) {
        Path carpeta = Path.of(o.valor("salida", "resultados")).resolve(plan.nombre());
        try {
            if (Files.isDirectory(carpeta)) {
                try (Stream<Path> contenido = Files.list(carpeta)) {
                    if (contenido.findAny().isPresent())
                        throw new IllegalArgumentException("La carpeta " + carpeta + " ya tiene resultados; use otro --nombre");
                }
            }
        } catch (java.io.IOException e) {
            throw new IllegalStateException("No se pudo revisar " + carpeta, e);
        }
        return carpeta;
    }

    private static ReglasCarga reglasCarga(BaseOperativa base) {
        Map<Integer, TipoPrioridad> prioridades = new HashMap<>();
        for (TipoPrioridad p : TipoPrioridad.values()) prioridades.put(base.configuracion().entero("prioridad." + p.name()), p);
        return new ReglasCarga(base.anchoKm(), base.altoKm(), prioridades);
    }

    /** Límites que todavía pertenecen al modelo del planificador y no al diseño estadístico. */
    private static List<String> limitesDelModelo(DatosArchivos datos) {
        List<String> limites = new ArrayList<>();
        limites.add("Los tres escenarios usan una aproximación batch diaria: una salida por vehículo por jornada, pedidos indivisibles y sin modelado explícito de turnos o alimentación.");
        limites.add("La experimentación usa planificación batch diaria con información completa de la demanda sintética del día; se conservan las horas reales de llegada y deadlines, y una ruta espera si alcanza un pedido antes de su llegada.");
        limites.add("La demanda de NORMAL, ALTA y CRITICA se deriva de P50, P75 y P90 del periodo histórico base; no se usan tamaños 25/38/55 fijados manualmente.");
        limites.add("COLAPSO_LOGISTICO aumenta la cantidad esperada de pedidos cada día usando el incremento histórico P75-P50 y termina en el primer deadline que el planificador no logra mantener.");
        limites.add("V penaliza restricciones duras representables por el modelo: capacidad, disponibilidad de vehículo, duplicidad, almacén/stock y rutas intransitables por bloqueos.");
        limites.add("La prioridad se expresa mediante deadlines 4/8/12/18h frente a 36h; no existe un beta adicional por prioridad para no cambiar la F documentada.");
        limites.add("Turnos y alimentación aún no forman parte de V porque Ruta/ContextoPlanificacion no modelan esas restricciones temporalmente.");
        limites.add("Los bloqueos sintéticos se generan con P50/P75/P90 de eventos diarios históricos y conservan duración y forma de poligonales históricas, trasladadas a posiciones válidas de la ciudad.");
        limites.add("Para la aproximación batch se usa, por jornada, el snapshot de máxima concurrencia de bloqueos; no se fusionan todos los bloqueos del día en una única topología permanente.");
        limites.add("El mantenimiento preventivo sigue el calendario entregado y se repite bimestralmente; no depende del perfil de presión.");
        limites.add("Las averías están deshabilitadas en esta versión de la experimentación hasta definir una regla de generación respaldada por el curso.");
        limites.add("Cada ruta puede utilizar el almacén central o uno intermedio como origen; el stock de los almacenes intermedios limita las asignaciones y se restablece en cada jornada batch.");
        limites.add("Los pedidos de los .txt no tienen id: se numeran 1..N según el orden de llegada leído.");
        if (datos.mantenimiento().isEmpty()) limites.add("Sin datos de mantenimiento: las instancias usan la disponibilidad de la flota registrada en BD.");
        return limites;
    }


    /** Parseo mínimo de --clave=valor y --bandera. */
    private record Opciones(Map<String, List<String>> valores, Set<String> banderas) {
        static Opciones leer(String[] args) {
            Map<String, List<String>> valores = new HashMap<>();
            Set<String> banderas = new java.util.HashSet<>();
            for (String arg : args) {
                if (!arg.startsWith("--")) throw new IllegalArgumentException("Argumento no reconocido: " + arg + "\n" + AYUDA);
                int igual = arg.indexOf('=');
                String clave = igual < 0 ? arg.substring(2) : arg.substring(2, igual);
                if (igual < 0 && BANDERAS.contains(clave)) banderas.add(clave);
                else if (igual > 0 && CON_VALOR.contains(clave)) valores.computeIfAbsent(clave, k -> new ArrayList<>()).add(arg.substring(igual + 1));
                else throw new IllegalArgumentException("Opción no reconocida o mal formada: " + arg + "\n" + AYUDA);
            }
            return new Opciones(valores, banderas);
        }

        boolean bandera(String nombre) { return banderas.contains(nombre); }
        boolean tieneValor(String nombre) { return valores.containsKey(nombre); }
        List<String> valores(String nombre) { return valores.getOrDefault(nombre, List.of()); }
        String valor(String nombre, String defecto) {
            List<String> v = valores.get(nombre);
            return v == null ? defecto : v.get(v.size() - 1);
        }
    }
}