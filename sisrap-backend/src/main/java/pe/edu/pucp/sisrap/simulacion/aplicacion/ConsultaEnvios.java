package pe.edu.pucp.sisrap.simulacion.aplicacion;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import pe.edu.pucp.sisrap.simulacion.dominio.EnvioSimulacion;
import pe.edu.pucp.sisrap.simulacion.dominio.EnvioSimulacion.Asignacion;
import pe.edu.pucp.sisrap.simulacion.dominio.EnvioSimulacion.EstadoAsignacion;
import pe.edu.pucp.sisrap.simulacion.dominio.PaginaEnvios;
import pe.edu.pucp.sisrap.simulacion.dominio.VentanaEnvios;
import pe.edu.pucp.sisrap.simulacion.dominio.VistaEnvios;

/** Filtra y pagina una vista de envíos: los filtros de tiempo usan el reloj simulado. */
final class ConsultaEnvios {

    static final int TAMANIO_MAXIMO = 2_000;
    static final int HORAS_MAXIMAS = 720;
    private static final int HORAS_POR_DEFECTO = 4;

    private ConsultaEnvios() { }

    static PaginaEnvios consultar(VistaEnvios vista, VentanaEnvios ventana, int horas,
                                  String busqueda, int pagina, int tamanio) {
        if (ventana == null) throw new IllegalArgumentException("La ventana es obligatoria");
        if (pagina < 0) throw new IllegalArgumentException("La página no puede ser negativa");
        if (tamanio <= 0 || tamanio > TAMANIO_MAXIMO) {
            throw new IllegalArgumentException("El tamaño debe estar entre 1 y " + TAMANIO_MAXIMO);
        }
        if (horas <= 0 || horas > HORAS_MAXIMAS) {
            throw new IllegalArgumentException("Las horas deben estar entre 1 y " + HORAS_MAXIMAS);
        }

        List<EnvioSimulacion> filtrados = filtrarPorVentana(vista, ventana, horas);
        String texto = busqueda == null ? "" : busqueda.trim().toLowerCase(Locale.ROOT);
        if (!texto.isEmpty()) {
            filtrados = filtrados.stream().filter(e -> coincide(e, texto)).toList();
        }

        int totalProductos = 0;
        for (EnvioSimulacion envio : filtrados) totalProductos += productos(envio, ventana);

        long inicio = Math.min((long) pagina * tamanio, filtrados.size());
        long fin = Math.min(inicio + tamanio, filtrados.size());
        return new PaginaEnvios(vista.idSimulacion(), vista.relojSimulado(), ventana, horas,
                filtrados.size(), totalProductos, pagina, tamanio,
                filtrados.subList((int) inicio, (int) fin));
    }

    /** Un envío completo, con todas sus unidades, aunque no esté en la página actual. */
    static PaginaEnvios consultarPedido(VistaEnvios vista, long idPedido) {
        List<EnvioSimulacion> envio = vista.envios().stream()
                .filter(e -> e.idPedido() == idPedido).toList();
        int productos = envio.stream().mapToInt(EnvioSimulacion::cantidad).sum();
        return new PaginaEnvios(vista.idSimulacion(), vista.relojSimulado(), VentanaEnvios.TODOS,
                HORAS_POR_DEFECTO, envio.size(), productos, 0, 1, envio);
    }

    private static List<EnvioSimulacion> filtrarPorVentana(VistaEnvios vista, VentanaEnvios ventana, int horas) {
        LocalDateTime reloj = vista.relojSimulado();
        if (ventana == VentanaEnvios.TODOS) return vista.envios();
        if (reloj == null) return List.of();
        List<EnvioSimulacion> filtrados = new ArrayList<>();
        for (EnvioSimulacion envio : vista.envios()) {
            List<Asignacion> lineas = ventana == VentanaEnvios.ENTREGADOS
                    ? entregadasDesde(envio, reloj.minusHours(horas), reloj)
                    : porEntregarHasta(envio, reloj.plusHours(horas));
            if (lineas.isEmpty()) continue;
            filtrados.add(new EnvioSimulacion(envio.idPedido(), envio.idCliente(), envio.destino(),
                    envio.horasLimite(), envio.fechaRegistro(), envio.fechaLimite(), envio.estado(),
                    envio.cantidad(), envio.cantidadEntregada(), envio.entregaParcial(), lineas));
        }
        filtrados.sort(ventana == VentanaEnvios.ENTREGADOS
                ? Comparator.comparing(ConsultaEnvios::ultimaEntrega).reversed()
                        .thenComparingLong(EnvioSimulacion::idPedido)
                : Comparator.comparing(ConsultaEnvios::primeraLlegada)
                        .thenComparingLong(EnvioSimulacion::idPedido));
        return filtrados;
    }

    private static List<Asignacion> entregadasDesde(EnvioSimulacion envio, LocalDateTime desde, LocalDateTime hasta) {
        return envio.asignaciones().stream()
                .filter(a -> a.estado() == EstadoAsignacion.ENTREGADA && a.horaEntrega() != null)
                .filter(a -> !a.horaEntrega().isBefore(desde) && !a.horaEntrega().isAfter(hasta))
                .toList();
    }

    private static List<Asignacion> porEntregarHasta(EnvioSimulacion envio, LocalDateTime hasta) {
        return envio.asignaciones().stream()
                .filter(a -> {
                    if (a.estado() == EstadoAsignacion.SIN_ASIGNAR) {
                        return !envio.fechaLimite().isAfter(hasta);
                    }
                    boolean pendiente = a.estado() == EstadoAsignacion.PROGRAMADA
                            || a.estado() == EstadoAsignacion.EN_RUTA
                            || a.estado() == EstadoAsignacion.EN_ATENCION;
                    return pendiente && a.horaLlegada() != null && !a.horaLlegada().isAfter(hasta);
                })
                .toList();
    }

    private static LocalDateTime ultimaEntrega(EnvioSimulacion envio) {
        return envio.asignaciones().stream().map(Asignacion::horaEntrega)
                .max(Comparator.naturalOrder()).orElseThrow();
    }

    private static LocalDateTime primeraLlegada(EnvioSimulacion envio) {
        return envio.asignaciones().stream()
                .map(a -> a.horaLlegada() != null ? a.horaLlegada() : envio.fechaLimite())
                .min(Comparator.naturalOrder()).orElseThrow();
    }

    /** En una ventana solo cuentan los productos de sus líneas, no todo el pedido. */
    private static int productos(EnvioSimulacion envio, VentanaEnvios ventana) {
        if (ventana == VentanaEnvios.TODOS) return envio.cantidad();
        return envio.asignaciones().stream().mapToInt(Asignacion::cantidad).sum();
    }

    private static boolean coincide(EnvioSimulacion envio, String texto) {
        if (Long.toString(envio.idPedido()).contains(texto)) return true;
        if (envio.idCliente() != null && envio.idCliente().toLowerCase(Locale.ROOT).contains(texto)) return true;
        return envio.asignaciones().stream().anyMatch(a -> a.idVehiculo() != null
                && a.idVehiculo().toLowerCase(Locale.ROOT).contains(texto));
    }
}
