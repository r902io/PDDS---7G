package pe.edu.pucp.sisrap.flota.dominio;

import pe.edu.pucp.sisrap.geografia.dominio.Nodo;

public class Vehiculo {
    private final String idVehiculo;
    private final int capacidadPaquetes;
    private final double velocidadKmh;
    private final double costoPorKm;
    private boolean disponible = true;
    private Nodo posicionActual;

    public Vehiculo(String idVehiculo, int capacidadPaquetes,
                     double velocidadKmh, double costoPorKm, Nodo posicionActual) {
        if(capacidadPaquetes<=0 || !Double.isFinite(velocidadKmh) || velocidadKmh<=0
            || !Double.isFinite(costoPorKm) || costoPorKm<0) throw new IllegalArgumentException("Vehículo inválido");
        this.idVehiculo = idVehiculo;
        this.capacidadPaquetes = capacidadPaquetes;
        this.velocidadKmh = velocidadKmh;
        this.costoPorKm = costoPorKm;
        this.posicionActual = posicionActual;
    }

    public String getIdVehiculo() { return idVehiculo; }
    public int getCapacidadPaquetes() { return capacidadPaquetes; }
    public double getVelocidadKmh() { return velocidadKmh; }
    public double getCostoPorKm() { return costoPorKm; }
    public boolean isDisponible() { return disponible; }
    public void setDisponible(boolean d) { this.disponible = d; }
    public Nodo getPosicionActual() { return posicionActual; }
    public void setPosicionActual(Nodo p) { this.posicionActual = p; }
}
