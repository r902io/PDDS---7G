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
import pe.edu.pucp.sisrap.experimentacion.dominio.BaseOperativa;
import pe.edu.pucp.sisrap.experimentacion.dominio.DatosArchivos;
import pe.edu.pucp.sisrap.experimentacion.dominio.Escenario;
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

/**
 * Punto de entrada de la experimentación numérica.
 * 1. Lee de la BD los parámetros (algoritmos, función objetivo, experimento), la ciudad, los almacenes y la flota.
 * 2. Lee de los .txt las ventas, los bloqueos y el mantenimiento preventivo.
 * 3. Arma las instancias, corre todos los algoritmos y exporta todo a resultados
 * 
 * Ejecutar desde la raíz del backend
 */
public final class ExperimentacionMain {
    static final String AYUDA = """
            Uso: ExperimentacionMain [opciones]
              --perfil=BASE            perfil de parámetros en la BD (tabla perfil_parametro)
              --nombre=<texto>         nombre del experimento (por defecto exp-AAAAMMDD-HHMMSS)
              --datos=datos            carpeta con ventas, bloqueos y mantenimiento (.txt)
              --salida=resultados      carpeta donde se crea resultados/<nombre>/
              --desde=AAAA-MM-DD       primer día de ventas a usar (por defecto, el primer día con archivo)
              --algoritmos=GENETICO,RECOCIDO_SIMULADO
              --tamanios=25,50,100     por defecto experimento.tamanios de la BD
              --instancias=1           bloques de pedidos distintos por cada tamaño
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
            "tamanios", "instancias", "repeticiones", "semilla", "variante");
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

        // 2) Plan (BD como valor por defecto, línea de comandos como sobrescritura)
        var lector = new LectorArchivosExperimento(Path.of(opciones.valor("datos", "datos")), reglasCarga(base));
        PlanExperimento plan = construirPlan(opciones, base.configuracion(), lector.primerDiaDisponible());
        System.out.printf("Plan %s: %d corridas (%d variantes x %d tamaños x %d instancias x %d repeticiones x %d algoritmos)%n",
                plan.nombre(), plan.totalCorridas(), plan.variantes().size(), plan.tamanios().size(), plan.instancias(),
                plan.repeticiones(), plan.algoritmos().size());

        // 3) Archivos .txt
        DatosArchivos datos = lector.leer(plan.desde(), plan.pedidosNecesarios());
        System.out.printf("Archivos: %d pedidos, %d bloqueos, %d días con mantenimiento preventivo (%d archivos leídos)%n",
                datos.pedidos().size(), datos.bloqueos().size(), datos.mantenimiento().size(), datos.archivosLeidos().size());

        // 4) Instancias
        List<String> advertencias = new ArrayList<>(datos.advertencias());
        advertencias.addAll(limitesDelModelo(datos));
        List<Escenario> escenarios = GeneradorEscenarios.generar(plan, base, datos, advertencias);
        escenarios.forEach(e -> System.out.printf("  %s: %d pedidos (%d qq) vs capacidad %d qq, %d vehículos en mantenimiento, %d bloqueos activos%n",
                e.id(), e.tamanio(), e.cantidadTotalQq(), e.capacidadDisponibleQq(), e.vehiculosEnMantenimiento().size(), e.bloqueosActivos().size()));

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

    private static PlanExperimento construirPlan(Opciones o, Configuracion cfg, LocalDate primerDia) {
        List<Integer> tamanios = o.tieneValor("tamanios") ? enteros(o.valor("tamanios", "")) : enteros(cfg.texto("experimento.tamanios"));
        int instancias = Integer.parseInt(o.valor("instancias", "1"));
        int repeticiones = o.tieneValor("repeticiones") ? Integer.parseInt(o.valor("repeticiones", "")) : cfg.entero("experimento.repeticiones");
        if (o.bandera("prueba")) {
            tamanios = List.of(tamanios.stream().min(Integer::compare).orElseThrow());
            instancias = 1;
            repeticiones = Math.min(repeticiones, 2);
        }
        List<Variante> variantes = new ArrayList<>(List.of(Variante.base()));
        o.valores("variante").forEach(v -> variantes.add(Variante.parsear(v)));
        String nombre = o.valor("nombre", "exp-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")));
        return new PlanExperimento(nombre, o.valor("perfil", "BASE"),
                Arrays.stream(o.valor("algoritmos", "GENETICO,RECOCIDO_SIMULADO").split(",")).map(String::trim).toList(),
                tamanios, instancias, repeticiones,
                o.tieneValor("semilla") ? Long.parseLong(o.valor("semilla", "")) : cfg.largo("experimento.semillaBase"),
                o.tieneValor("desde") ? LocalDate.parse(o.valor("desde", "")) : primerDia,
                !o.bandera("sin-mantenimiento"), !o.bandera("sin-calentamiento"), variantes);
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

    /** Lo que el modelo estático actual no representa: queda escrito en el manifiesto para no sobrevender resultados. */
    private static List<String> limitesDelModelo(DatosArchivos datos) {
        List<String> limites = new ArrayList<>();
        limites.add("Modelo estático: una salida por vehículo desde el almacén central, pedidos indivisibles, sin recargas, turnos, averías ni replanificación.");
        limites.add("Los bloqueos se recopilan y se reportan por instancia, pero la distancia es Manhattan sin grafo de vías: NO afectan a los algoritmos.");
        limites.add("El mantenimiento preventivo sí se aplica: el vehículo listado ese día queda no disponible para la instancia planificada ese día.");
        limites.add("Cada instancia se planifica en el instante en que llegó su último pedido; los anteriores acumulan espera hasta ese momento.");
        limites.add("Los pedidos de los .txt no tienen id: se numeran 1..N según el orden de llegada leído.");
        if (datos.mantenimiento().isEmpty()) limites.add("Sin datos de mantenimiento: todas las instancias usan la flota completa de la BD.");
        return limites;
    }

    private static List<Integer> enteros(String texto) {
        return Arrays.stream(texto.split(",")).map(String::trim).map(Integer::parseInt).toList();
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