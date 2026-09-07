package pe.edu.pucp.sisrap.planificador.modelo;

import java.util.ArrayList;
import java.util.List;

import pe.edu.pucp.sisrap.dominio.Almacen;
import pe.edu.pucp.sisrap.dominio.Pedido;
import pe.edu.pucp.sisrap.dominio.Vehiculo;

public class Ruta {
    private final Vehiculo vehiculo;
    private final Almacen almacenOrigen;
    private List<Pedido> secuenciaPedidos = new ArrayList<>();
    private double distanciaTotalKm;
    private double costoTotal;
    private double tiempoTotalHoras;

    public Ruta(Vehiculo vehiculo, Almacen almacenOrigen) {
        this.vehiculo = vehiculo;
        this.almacenOrigen = almacenOrigen;
    }

    public Ruta copiar() {
        Ruta copia = new Ruta(this.vehiculo, this.almacenOrigen);
        copia.secuenciaPedidos = new ArrayList<>(this.secuenciaPedidos);
        copia.recalcular();
        return copia;
    }

    public int cargaTotal() {
        return secuenciaPedidos.stream().mapToInt(Pedido::getCantidadQq).sum();
    }

    /** Recalcula distancia, costo y tiempo recorriendo la retícula (Manhattan). */
    public void recalcular() {
        double distancia = 0;
        var posicionActual = almacenOrigen.getUbicacion();
        for (Pedido p : secuenciaPedidos) {
            distancia += posicionActual.distanciaManhattan(p.getUbicacion());
            posicionActual = p.getUbicacion();
        }
        this.distanciaTotalKm = distancia;
        this.costoTotal = distancia * vehiculo.getCostoPorKm();
        // tiempo de viaje + 1h de entrega por cada parada (regla del enunciado)
        this.tiempoTotalHoras = (distancia / vehiculo.getVelocidadKmh())
                + secuenciaPedidos.size();
    }

    /** Hora acumulada (en horas desde el inicio de ruta) en que se entrega el pedido en la posición dada. */
    public double horaEntregaDe(int posicion) {
        double distanciaAcumulada = 0;
        var posicionActual = almacenOrigen.getUbicacion();
        for (int i = 0; i <= posicion; i++) {
            var pedido = secuenciaPedidos.get(i);
            distanciaAcumulada += posicionActual.distanciaManhattan(pedido.getUbicacion());
            posicionActual = pedido.getUbicacion();
        }
        return (distanciaAcumulada / vehiculo.getVelocidadKmh()) + (posicion + 1); // +1h de entrega por parada
    }

    public Vehiculo getVehiculo() { return vehiculo; }
    public Almacen getAlmacenOrigen() { return almacenOrigen; }
    public List<Pedido> getSecuenciaPedidos() { return secuenciaPedidos; }
    public double getDistanciaTotalKm() { return distanciaTotalKm; }
    public double getCostoTotal() { return costoTotal; }
    public double getTiempoTotalHoras() { return tiempoTotalHoras; }
}
