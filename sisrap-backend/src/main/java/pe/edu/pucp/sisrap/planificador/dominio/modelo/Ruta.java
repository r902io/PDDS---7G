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

/*
  Ruta de una unidad de transporte.
 */
public class Ruta {

    private final Vehiculo vehiculo;
    private Almacen almacenOrigen;
    private final LocalDateTime inicio;
    private final ReglasPlanificacion reglas;

    private final List<Pedido> secuenciaPedidos = new ArrayList<>();

    private double distanciaTotalKm;
    private double costoTotal;
    private double tiempoTotalHoras;

    private Set<String> nodosBloqueados = Set.of();
    private List<Almacen> almacenesCandidatos = List.of();

    /**
     * Indica que al menos uno de los tramos de la ruta no posee camino válido.
     */
    private boolean intransitable;

    public Ruta(
            Vehiculo vehiculo,
            Almacen origen,
            LocalDateTime inicio,
            ReglasPlanificacion reglas) {

        this.vehiculo = vehiculo;
        this.almacenOrigen = origen;
        this.inicio = inicio;
        this.reglas = reglas;
    }

    public void setNodosBloqueados(Set<String> nodos) {
        this.nodosBloqueados = nodos == null
                ? Set.of()
                : Set.copyOf(nodos);
    }

    public Set<String> getNodosBloqueados() {
        return nodosBloqueados;
    }

    public boolean isIntransitable() {
        return intransitable;
    }

    public Ruta copiar() {
        Ruta copia = new Ruta(
                vehiculo,
                almacenOrigen,
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

        double distancia = 0.0;

        Nodo posicion = almacenOrigen.getUbicacion();

        for (Pedido pedido : secuenciaPedidos) {
            distancia += distanciaEntre(
                    posicion,
                    pedido.getUbicacion());

            posicion = pedido.getUbicacion();
        }

        if (reglas.incluirRetorno()
                && !secuenciaPedidos.isEmpty()) {

            distancia += distanciaEntre(
                    posicion,
                    almacenOrigen.getUbicacion());
        }

        distanciaTotalKm =
                distancia * reglas.distanciaNodoKm();

        costoTotal =
                distanciaTotalKm * vehiculo.getCostoPorKm();

        tiempoTotalHoras =
                distanciaTotalKm / vehiculo.getVelocidadKmh()
                        + secuenciaPedidos.size()
                        * reglas.servicioHoras();
    }

    public double horaLlegadaDe(int indice) {
        double distancia = 0.0;

        Nodo posicion = almacenOrigen.getUbicacion();

        for (int i = 0; i <= indice; i++) {
            Pedido pedido = secuenciaPedidos.get(i);

            distancia += distanciaEntre(
                    posicion,
                    pedido.getUbicacion());

            posicion = pedido.getUbicacion();
        }

        return distancia * reglas.distanciaNodoKm()
                / vehiculo.getVelocidadKmh()
                + indice * reglas.servicioHoras();
    }

    /**
     * Evalúa la hora relativa de llegada si se agregara un pedido al final de
     * la ruta usando un almacén candidato, sin modificar el estado de la ruta.
     * Devuelve infinito si alguno de los tramos no tiene camino válido.
     */
    public double horaLlegadaSiAgrega(Pedido nuevoPedido, Almacen origenCandidato) {
        if (nuevoPedido == null || origenCandidato == null) {
            throw new IllegalArgumentException("Pedido/origen candidato inválido");
        }

        double distancia = 0.0;
        Nodo posicion = origenCandidato.getUbicacion();

        for (Pedido pedido : secuenciaPedidos) {
            int pasos = DistanciaReticula.distancia(
                    posicion,
                    pedido.getUbicacion(),
                    nodosBloqueados,
                    reglas.anchoCiudad(),
                    reglas.altoCiudad());

            if (pasos < 0) {
                return Double.POSITIVE_INFINITY;
            }

            distancia += pasos;
            posicion = pedido.getUbicacion();
        }

        int pasosFinales = DistanciaReticula.distancia(
                posicion,
                nuevoPedido.getUbicacion(),
                nodosBloqueados,
                reglas.anchoCiudad(),
                reglas.altoCiudad());

        if (pasosFinales < 0) {
            return Double.POSITIVE_INFINITY;
        }

        distancia += pasosFinales;

        return distancia * reglas.distanciaNodoKm()
                / vehiculo.getVelocidadKmh()
                + secuenciaPedidos.size() * reglas.servicioHoras();
    }

    public double horaEntregaDe(int indice) {
        return horaLlegadaDe(indice)
                + reglas.servicioHoras();
    }

    public double tiempoAtencionDe(int indice) {
        return Duration.between(
                secuenciaPedidos.get(indice).getFechaLlegada(),
                inicio)
                .toNanos()
                / 3_600_000_000_000.0
                + horaEntregaDe(indice);
    }

    public double retrasoDe(int indice) {
        Pedido pedido = secuenciaPedidos.get(indice);

        double disponible =
                Duration.between(
                        inicio,
                        pedido.getFechaLimite())
                        .toNanos()
                        / 3_600_000_000_000.0;

        double momentoEntrega =
                reglas.servicioDentroPlazo()
                        ? horaEntregaDe(indice)
                        : horaLlegadaDe(indice);

        return Math.max(
                0.0,
                momentoEntrega - disponible);
    }

    private double distanciaEntre(
            Nodo origen,
            Nodo destino) {

        int pasos = DistanciaReticula.distancia(
                origen,
                destino,
                nodosBloqueados,
                reglas.anchoCiudad(),
                reglas.altoCiudad());

        if (pasos >= 0) {
            return pasos;
        }

        /*
         * La ruta queda marcada como infactible.
         * Se mantiene una distancia finita únicamente para evitar NaN/Infinity
         * en las métricas. FuncionObjetivo penalizará la infactibilidad mediante V.
         */
        intransitable = true;

        return origen.distanciaManhattan(destino);
    }

    public Vehiculo getVehiculo() {
        return vehiculo;
    }

    public Almacen getAlmacenOrigen() {
        return almacenOrigen;
    }

    public void setAlmacenOrigen(Almacen almacenOrigen) {
        if (almacenOrigen == null) {
            throw new IllegalArgumentException("El almacén de origen no puede ser nulo");
        }
        this.almacenOrigen = almacenOrigen;
    }

    public void setAlmacenesCandidatos(List<Almacen> almacenes) {
        this.almacenesCandidatos = almacenes == null ? List.of() : List.copyOf(almacenes);
    }

    public List<Almacen> getAlmacenesCandidatos() {
        return almacenesCandidatos;
    }

    public List<Pedido> getSecuenciaPedidos() {
        return secuenciaPedidos;
    }

    public double getDistanciaTotalKm() {
        return distanciaTotalKm;
    }

    public double getCostoTotal() {
        return costoTotal;
    }

    public double getTiempoTotalHoras() {
        return tiempoTotalHoras;
    }
}