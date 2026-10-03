package pe.edu.pucp.sisrap.planificador.dominio.modelo;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import pe.edu.pucp.sisrap.almacen.dominio.Almacen;
import pe.edu.pucp.sisrap.flota.dominio.Vehiculo;
import pe.edu.pucp.sisrap.geografia.dominio.Nodo;
import pe.edu.pucp.sisrap.parametros.dominio.ReglasPlanificacion;
import pe.edu.pucp.sisrap.pedido.dominio.Pedido;

/** Ruta de una unidad de transporte. */
public class Ruta {

    private final Vehiculo vehiculo;
    private Almacen almacenOrigen;
    private final Nodo posicionInicio;
    private final boolean pasarPorAlmacenOrigen;
    private final LocalDateTime inicio;
    private final ReglasPlanificacion reglas;

    private final List<Pedido> secuenciaPedidos = new ArrayList<>();

    private double distanciaTotalKm;
    private double costoTotal;
    private double tiempoTotalHoras;

    private Set<String> nodosBloqueados = Set.of();
    private List<Almacen> almacenesCandidatos = List.of();
    private boolean intransitable;

    /**
     * Constructor histórico: conserva exactamente el comportamiento usado por
     * la experimentación. La ruta se considera iniciada en el almacén origen.
     */
    public Ruta(
            Vehiculo vehiculo,
            Almacen origen,
            LocalDateTime inicio,
            ReglasPlanificacion reglas) {
        this(
                vehiculo,
                origen,
                origen.getUbicacion(),
                false,
                inicio,
                reglas);
    }

    /**
     * Constructor operativo. Cuando {@code pasarPorAlmacenOrigen=true}, el
     * vehículo parte desde su posición real y primero viaja al almacén elegido
     * por el planificador para recoger la carga.
     */
    public Ruta(
            Vehiculo vehiculo,
            Almacen origen,
            Nodo posicionInicio,
            boolean pasarPorAlmacenOrigen,
            LocalDateTime inicio,
            ReglasPlanificacion reglas) {

        if (vehiculo == null
                || origen == null
                || posicionInicio == null
                || inicio == null
                || reglas == null) {
            throw new IllegalArgumentException("Ruta incompleta");
        }

        this.vehiculo = vehiculo;
        this.almacenOrigen = origen;
        this.posicionInicio = posicionInicio;
        this.pasarPorAlmacenOrigen = pasarPorAlmacenOrigen;
        this.inicio = inicio;
        this.reglas = reglas;
    }

    public void setNodosBloqueados(Set<String> nodos) {
        this.nodosBloqueados = nodos == null ? Set.of() : nodos;
    }

    public Set<String> getNodosBloqueados() { return nodosBloqueados; }
    public boolean isIntransitable() { return intransitable; }

    public Ruta copiar() {
        Ruta copia = new Ruta(
                vehiculo,
                almacenOrigen,
                posicionInicio,
                pasarPorAlmacenOrigen,
                inicio,
                reglas);
        copia.nodosBloqueados = nodosBloqueados;
        copia.almacenesCandidatos = almacenesCandidatos;
        copia.secuenciaPedidos.addAll(secuenciaPedidos);
        copia.recalcular();
        return copia;
    }

    public int cargaTotal() {
        return secuenciaPedidos.stream()
                .mapToInt(Pedido::getCantidadQq)
                .sum();
    }

    public void recalcular() {
        intransitable = false;
        double distanciaPasos = 0.0;
        Nodo posicion = posicionInicialPara(almacenOrigen);
        LocalDateTime reloj = inicio;

        if (pasarPorAlmacenOrigen && !secuenciaPedidos.isEmpty()) {
            int pasosAlmacen = pasosEntre(posicion, almacenOrigen.getUbicacion());
            if (pasosAlmacen < 0) {
                intransitable = true;
                pasosAlmacen = posicion.distanciaManhattan(almacenOrigen.getUbicacion());
            }
            distanciaPasos += pasosAlmacen;
            reloj = sumarHoras(
                    reloj,
                    pasosAlmacen * reglas.distanciaNodoKm() / vehiculo.getVelocidadKmh());
            posicion = almacenOrigen.getUbicacion();
        }

        for (Pedido pedido : secuenciaPedidos) {
            int pasos = pasosEntre(posicion, pedido.getUbicacion());
            if (pasos < 0) {
                intransitable = true;
                pasos = posicion.distanciaManhattan(pedido.getUbicacion());
            }

            distanciaPasos += pasos;
            reloj = sumarHoras(
                    reloj,
                    pasos * reglas.distanciaNodoKm() / vehiculo.getVelocidadKmh());

            if (reloj.isBefore(pedido.getFechaLlegada())) {
                reloj = pedido.getFechaLlegada();
            }

            reloj = sumarHoras(reloj, reglas.servicioHoras());
            posicion = pedido.getUbicacion();
        }

        if (reglas.incluirRetorno() && !secuenciaPedidos.isEmpty()) {
            int pasos = pasosEntre(posicion, almacenOrigen.getUbicacion());
            if (pasos < 0) {
                intransitable = true;
                pasos = posicion.distanciaManhattan(almacenOrigen.getUbicacion());
            }
            distanciaPasos += pasos;
            reloj = sumarHoras(
                    reloj,
                    pasos * reglas.distanciaNodoKm() / vehiculo.getVelocidadKmh());
        }

        distanciaTotalKm = distanciaPasos * reglas.distanciaNodoKm();
        costoTotal = distanciaTotalKm * vehiculo.getCostoPorKm();
        tiempoTotalHoras = horasEntre(inicio, reloj);
    }

    public double horaLlegadaDe(int indice) {
        return horasEntre(inicio, fechaLlegadaRutaDe(indice));
    }

    public double horaLlegadaSiAgrega(Pedido nuevoPedido, Almacen origenCandidato) {
        if (nuevoPedido == null || origenCandidato == null) {
            throw new IllegalArgumentException("Pedido/origen candidato inválido");
        }

        Nodo posicion = posicionInicialPara(origenCandidato);
        LocalDateTime reloj = inicio;

        if (pasarPorAlmacenOrigen) {
            int pasosAlmacen = pasosEntre(posicion, origenCandidato.getUbicacion());
            if (pasosAlmacen < 0) {
                return Double.POSITIVE_INFINITY;
            }
            reloj = sumarHoras(
                    reloj,
                    pasosAlmacen * reglas.distanciaNodoKm() / vehiculo.getVelocidadKmh());
            posicion = origenCandidato.getUbicacion();
        }

        for (Pedido pedido : secuenciaPedidos) {
            int pasos = pasosEntre(posicion, pedido.getUbicacion());
            if (pasos < 0) return Double.POSITIVE_INFINITY;

            reloj = sumarHoras(
                    reloj,
                    pasos * reglas.distanciaNodoKm() / vehiculo.getVelocidadKmh());
            if (reloj.isBefore(pedido.getFechaLlegada())) {
                reloj = pedido.getFechaLlegada();
            }
            reloj = sumarHoras(reloj, reglas.servicioHoras());
            posicion = pedido.getUbicacion();
        }

        int pasosFinales = pasosEntre(posicion, nuevoPedido.getUbicacion());
        if (pasosFinales < 0) return Double.POSITIVE_INFINITY;

        reloj = sumarHoras(
                reloj,
                pasosFinales * reglas.distanciaNodoKm() / vehiculo.getVelocidadKmh());
        if (reloj.isBefore(nuevoPedido.getFechaLlegada())) {
            reloj = nuevoPedido.getFechaLlegada();
        }

        return horasEntre(inicio, reloj);
    }

    public double horaEntregaDe(int indice) {
        LocalDateTime entrega = sumarHoras(
                fechaLlegadaRutaDe(indice),
                reglas.servicioHoras());
        return horasEntre(inicio, entrega);
    }

    public double tiempoAtencionDe(int indice) {
        Pedido pedido = secuenciaPedidos.get(indice);
        LocalDateTime entrega = sumarHoras(
                fechaLlegadaRutaDe(indice),
                reglas.servicioHoras());
        return Math.max(0.0, horasEntre(pedido.getFechaLlegada(), entrega));
    }

    public double retrasoDe(int indice) {
        Pedido pedido = secuenciaPedidos.get(indice);
        LocalDateTime momento = reglas.servicioDentroPlazo()
                ? sumarHoras(fechaLlegadaRutaDe(indice), reglas.servicioHoras())
                : fechaLlegadaRutaDe(indice);

        if (!momento.isAfter(pedido.getFechaLimite())) return 0.0;
        return horasEntre(pedido.getFechaLimite(), momento);
    }

    private LocalDateTime fechaLlegadaRutaDe(int indice) {
        if (indice < 0 || indice >= secuenciaPedidos.size()) {
            throw new IndexOutOfBoundsException("Índice de pedido fuera de ruta");
        }

        Nodo posicion = posicionInicialPara(almacenOrigen);
        LocalDateTime reloj = inicio;

        if (pasarPorAlmacenOrigen) {
            int pasosAlmacen = pasosEntre(posicion, almacenOrigen.getUbicacion());
            if (pasosAlmacen < 0) {
                pasosAlmacen = posicion.distanciaManhattan(almacenOrigen.getUbicacion());
            }
            reloj = sumarHoras(
                    reloj,
                    pasosAlmacen * reglas.distanciaNodoKm() / vehiculo.getVelocidadKmh());
            posicion = almacenOrigen.getUbicacion();
        }

        for (int i = 0; i <= indice; i++) {
            Pedido pedido = secuenciaPedidos.get(i);
            int pasos = pasosEntre(posicion, pedido.getUbicacion());
            if (pasos < 0) {
                pasos = posicion.distanciaManhattan(pedido.getUbicacion());
            }

            reloj = sumarHoras(
                    reloj,
                    pasos * reglas.distanciaNodoKm() / vehiculo.getVelocidadKmh());

            if (reloj.isBefore(pedido.getFechaLlegada())) {
                reloj = pedido.getFechaLlegada();
            }

            if (i == indice) return reloj;

            reloj = sumarHoras(reloj, reglas.servicioHoras());
            posicion = pedido.getUbicacion();
        }

        throw new IllegalStateException("No se pudo calcular la llegada");
    }

    private Nodo posicionInicialPara(Almacen origen) {
        return pasarPorAlmacenOrigen
                ? posicionInicio
                : origen.getUbicacion();
    }

    private int pasosEntre(Nodo origen, Nodo destino) {
        return DistanciaReticula.distancia(
                origen,
                destino,
                nodosBloqueados,
                reglas.anchoCiudad(),
                reglas.altoCiudad());
    }

    private static LocalDateTime sumarHoras(LocalDateTime base, double horas) {
        long nanos = Math.round(horas * 3_600_000_000_000.0);
        return base.plusNanos(nanos);
    }

    private static double horasEntre(LocalDateTime desde, LocalDateTime hasta) {
        return Duration.between(desde, hasta).toNanos()
                / 3_600_000_000_000.0;
    }

    public Vehiculo getVehiculo() { return vehiculo; }
    public Almacen getAlmacenOrigen() { return almacenOrigen; }
    public Nodo getPosicionInicio() { return posicionInicio; }
    public boolean isPasarPorAlmacenOrigen() { return pasarPorAlmacenOrigen; }

    public void setAlmacenOrigen(Almacen almacenOrigen) {
        if (almacenOrigen == null) {
            throw new IllegalArgumentException("El almacén de origen no puede ser nulo");
        }
        this.almacenOrigen = almacenOrigen;
    }

    public void setAlmacenesCandidatos(List<Almacen> almacenes) {
        this.almacenesCandidatos = almacenes == null
                ? List.of()
                : List.copyOf(almacenes);
    }

    public List<Almacen> getAlmacenesCandidatos() { return almacenesCandidatos; }
    public List<Pedido> getSecuenciaPedidos() { return secuenciaPedidos; }
    public double getDistanciaTotalKm() { return distanciaTotalKm; }
    public double getCostoTotal() { return costoTotal; }
    public double getTiempoTotalHoras() { return tiempoTotalHoras; }
}
