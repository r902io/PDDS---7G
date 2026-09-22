package pe.edu.pucp.sisrap.experimentacion.aplicacion;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import pe.edu.pucp.sisrap.experimentacion.dominio.BaseOperativa;
import pe.edu.pucp.sisrap.experimentacion.dominio.DatosArchivos;
import pe.edu.pucp.sisrap.experimentacion.dominio.Escenario;
import pe.edu.pucp.sisrap.experimentacion.dominio.EscenarioOperativo;
import pe.edu.pucp.sisrap.experimentacion.dominio.PerfilPresion;
import pe.edu.pucp.sisrap.experimentacion.dominio.PlanExperimento;
import pe.edu.pucp.sisrap.flota.dominio.Vehiculo;
import pe.edu.pucp.sisrap.geografia.dominio.Nodo;
import pe.edu.pucp.sisrap.pedido.dominio.Pedido;

/**
 * Convierte los datos recopilados de los .txt en instancias del problema, cruzando escenario operativo
 * x perfil de presión x nivel (instancias), tal como lo define el informe "Diseño de Experimento"
 * (secciones 3.4 y 4.3): cantidad de pedidos, disponibilidad de vehículos e incidencias varían de forma
 * conjunta y no una sola dimensión a la vez.
 * <p>
 * El tamaño de cada instancia se deriva de {@code tamanioBaseDiario} escalado por el factor del perfil
 * (sección 3.4) y del escenario ({@link EscenarioOperativo#factorHorizonte}); en COLAPSO_LOGISTICO el
 * tamaño además crece con cada nivel para poder detectar el punto de colapso (sección 4.2: "incrementar
 * gradualmente la presión sobre el sistema").
 */
public final class GeneradorEscenarios {
    private GeneradorEscenarios() {}

    public static List<Escenario> generar(PlanExperimento plan, BaseOperativa base, DatosArchivos datos,
                                          List<String> advertencias) {
        if (datos.pedidos().size() < plan.pedidosNecesarios())
            throw new IllegalArgumentException("Se necesitan hasta " + plan.pedidosNecesarios() + " pedidos desde "
                    + plan.desde() + " y solo hay " + datos.pedidos().size()
                    + ". Reduzca --instancias/--tamanio-base o adelante --desde.");
        List<Pedido> pedidos = crearPedidos(datos, base);
        validarMantenimiento(datos, base, advertencias);

        List<Escenario> escenarios = new ArrayList<>();
        for (EscenarioOperativo eo : plan.escenariosOperativos())
            for (PerfilPresion perfil : plan.perfiles())
                for (int nivel = 1; nivel <= plan.instancias(); nivel++)
                    escenarios.add(construirEscenario(plan, base, datos, pedidos, eo, perfil, nivel));
        return List.copyOf(escenarios);
    }

    private static Escenario construirEscenario(PlanExperimento plan, BaseOperativa base, DatosArchivos datos,
                                                List<Pedido> pedidos, EscenarioOperativo eo, PerfilPresion perfil, int nivel) {
        if (eo == EscenarioOperativo.SIMULACION_CINCO_DIAS)
            return construirCincoDias(plan, base, datos, pedidos, perfil, nivel);
        double factorColapso = eo == EscenarioOperativo.COLAPSO_LOGISTICO ? 1.0 + 0.5 * (nivel - 1) : 1.0;
        int tamanio = Math.min(pedidos.size(),
                (int) Math.round(plan.tamanioBaseDiario() * perfil.factorTamanio * eo.factorHorizonte * factorColapso));
        if (tamanio < 1) tamanio = 1;

        // Ventana de pedidos: se recorre round-robin sobre los pedidos disponibles para que distintos niveles
        // de una misma combinación tomen bloques distintos sin exigir más datos de los que hay.
        int hueco = Math.max(1, pedidos.size() - tamanio);
        int offset = ((nivel - 1) * tamanio) % hueco;
        List<Pedido> bloque = pedidos.subList(offset, offset + tamanio);
        var instante = bloque.get(tamanio - 1).getFechaLlegada();
        LocalDate dia = instante.toLocalDate();

        Set<String> enMantenimiento = plan.aplicarMantenimiento()
                ? datos.mantenimiento().getOrDefault(dia, Set.of()) : Set.of();
        // Reducción de flota propia del perfil (adicional al colapso progresivo): en COLAPSO_LOGISTICO se
        // retira un vehículo más por cada nivel, siguiendo "reducción de vehículos disponibles" (sección 4.2).
        int bajasColapso = eo == EscenarioOperativo.COLAPSO_LOGISTICO ? (nivel - 1) : 0;
        List<String> idsOrdenados = base.vehiculos().stream().map(Vehiculo::getIdVehiculo).sorted().toList();
        Set<String> enBajaPorPerfil = new HashSet<>(idsOrdenados.subList(0,
                Math.min(idsOrdenados.size(), perfil.vehiculosBajaBase + bajasColapso)));

        List<Vehiculo> flota = base.vehiculos().stream().map(v -> copiar(v, enMantenimiento, enBajaPorPerfil)).toList();
        var bloqueosActivos = datos.bloqueos().stream().filter(b -> b.activoEn(instante)).toList();

        String id = String.format("%s-%s-N%02d", eo, perfil, nivel);
        return new Escenario(id, eo, perfil, tamanio, nivel, instante, bloque, flota,
                enMantenimiento.stream().sorted().toList(), enBajaPorPerfil.stream().sorted().toList(), bloqueosActivos);
    }

    /**
     * Construye el escenario de cinco días a partir de cinco fechas calendario consecutivas presentes en
     * los .txt. De este modo no se confunde un único lote artificialmente cinco veces mayor con cinco
     * jornadas reales de llegada de pedidos. Cada jornada aporta hasta el tamaño diario configurado para
     * el perfil; el corredor conserva las fechas de llegada de cada pedido al evaluar el SLA.
     */
    private static Escenario construirCincoDias(PlanExperimento plan, BaseOperativa base, DatosArchivos datos,
                                                List<Pedido> pedidos, PerfilPresion perfil, int nivel) {
        List<LocalDate> dias = pedidos.stream().map(p -> p.getFechaLlegada().toLocalDate()).distinct().sorted().toList();
        if (dias.size() < 5)
            throw new IllegalArgumentException("SIMULACION_CINCO_DIAS requiere pedidos de al menos cinco fechas distintas; se encontraron " + dias.size());
        int primerIndice = ((nivel - 1) * 5) % (dias.size() - 4);
        List<LocalDate> ventana = dias.subList(primerIndice, primerIndice + 5);
        int pedidosPorDia = Math.max(1, (int) Math.round(plan.tamanioBaseDiario() * perfil.factorTamanio));
        List<Pedido> bloque = new ArrayList<>();
        for (LocalDate dia : ventana) {
            List<Pedido> pedidosDia = pedidos.stream().filter(p -> p.getFechaLlegada().toLocalDate().equals(dia)).toList();
            if (pedidosDia.size() < pedidosPorDia)
                throw new IllegalArgumentException("El día " + dia + " solo tiene " + pedidosDia.size() + " pedidos; se requieren "
                        + pedidosPorDia + " para SIMULACION_CINCO_DIAS con perfil " + perfil);
            bloque.addAll(pedidosDia.subList(0, pedidosPorDia));
        }
        var instante = bloque.get(bloque.size() - 1).getFechaLlegada();
        LocalDate diaFinal = ventana.get(ventana.size() - 1);
        Set<String> enMantenimiento = plan.aplicarMantenimiento()
                ? datos.mantenimiento().getOrDefault(diaFinal, Set.of()) : Set.of();
        List<String> idsOrdenados = base.vehiculos().stream().map(Vehiculo::getIdVehiculo).sorted().toList();
        Set<String> enBajaPorPerfil = new HashSet<>(idsOrdenados.subList(0,
                Math.min(idsOrdenados.size(), perfil.vehiculosBajaBase)));
        List<Vehiculo> flota = base.vehiculos().stream().map(v -> copiar(v, enMantenimiento, enBajaPorPerfil)).toList();
        var bloqueosActivos = datos.bloqueos().stream().filter(b -> b.activoEn(instante)).toList();
        String id = String.format("%s-%s-N%02d", EscenarioOperativo.SIMULACION_CINCO_DIAS, perfil, nivel);
        return new Escenario(id, EscenarioOperativo.SIMULACION_CINCO_DIAS, perfil, bloque.size(), nivel, instante, bloque, flota,
                enMantenimiento.stream().sorted().toList(), enBajaPorPerfil.stream().sorted().toList(), bloqueosActivos);
    }

    /** Los archivos de ventas no traen id: se asigna la posición (1-based) en el orden de llegada leído. */
    private static List<Pedido> crearPedidos(DatosArchivos datos, BaseOperativa base) {
        List<Pedido> pedidos = new ArrayList<>();
        long id = 1;
        for (var p : datos.pedidos()) {
            if (p.x() > base.anchoKm() || p.y() > base.altoKm())
                throw new IllegalArgumentException("Pedido fuera de la ciudad: " + p.x() + "," + p.y());
            pedidos.add(new Pedido(id++, p.cliente(), p.cantidad(), p.prioridad(), new Nodo(p.x(), p.y()), p.llegada(), p.horas()));
        }
        return pedidos;
    }

    /** Copia la flota de la BD; un vehículo en mantenimiento o dado de baja por el perfil queda no disponible. */
    private static Vehiculo copiar(Vehiculo v, Set<String> enMantenimiento, Set<String> enBajaPorPerfil) {
        var copia = new Vehiculo(v.getIdVehiculo(), v.getCapacidadPaquetes(), v.getVelocidadKmh(), v.getCostoPorKm(), v.getPosicionActual());
        copia.setDisponible(v.isDisponible() && !enMantenimiento.contains(v.getIdVehiculo()) && !enBajaPorPerfil.contains(v.getIdVehiculo()));
        return copia;
    }

    private static void validarMantenimiento(DatosArchivos datos, BaseOperativa base, List<String> advertencias) {
        Set<String> conocidos = base.vehiculos().stream().map(Vehiculo::getIdVehiculo).collect(Collectors.toSet());
        Set<String> desconocidos = new HashSet<>();
        datos.mantenimiento().values().forEach(ids -> ids.stream().filter(id -> !conocidos.contains(id)).forEach(desconocidos::add));
        if (!desconocidos.isEmpty())
            advertencias.add("Mantenimiento menciona vehículos que no existen en la BD: " + desconocidos.stream().sorted().toList());
    }
}
