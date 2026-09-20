package pe.edu.pucp.sisrap.almacen.dominio;

import pe.edu.pucp.sisrap.geografia.dominio.Nodo;

public class Almacen {
    private final String idAlmacen;
    private final Nodo ubicacion;
    private final Integer capacidadMaxima;
    private Integer stockActual;

    public Almacen(String idAlmacen, Nodo ubicacion, Integer capacidadMaxima, Integer stockActual) {
        this.idAlmacen = idAlmacen;
        this.ubicacion = ubicacion;
        this.capacidadMaxima = capacidadMaxima;
        this.stockActual = stockActual;
    }

    public boolean tieneStockDisponible() {
        return capacidadMaxima == null || (stockActual != null && stockActual > 0);
    }

    public String getIdAlmacen() { return idAlmacen; }
    public Nodo getUbicacion() { return ubicacion; }
    public Integer getCapacidadMaxima() { return capacidadMaxima; }
    public Integer getStockActual() { return stockActual; }
    public void setStockActual(Integer s) { this.stockActual = s; }
}
