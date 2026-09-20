package pe.edu.pucp.sisrap.planificador.dominio.modelo;

import java.util.ArrayList;
import java.util.List;

import pe.edu.pucp.sisrap.pedido.dominio.Pedido;

public class Solucion {
    private List<Ruta> rutas = new ArrayList<>();
    private List<Pedido> pedidosNoAsignados = new ArrayList<>();

    private double valorFuncionObjetivo;
    private double costoTransporte;
    private double valorR;
    private double valorT;
    private int valorN;
    private double valorV;
    private boolean esFactible;

    public Solucion copiar() {
        Solucion copia = new Solucion();
        for (Ruta r : this.rutas) copia.rutas.add(r.copiar());
        copia.pedidosNoAsignados = new ArrayList<>(this.pedidosNoAsignados);
        copia.valorFuncionObjetivo = this.valorFuncionObjetivo;
        copia.costoTransporte = this.costoTransporte;
        copia.valorR = this.valorR;
        copia.valorT = this.valorT;
        copia.valorN = this.valorN;
        copia.valorV = this.valorV;
        copia.esFactible = this.esFactible;
        return copia;
    }

    public List<Ruta> getRutas() { return rutas; }
    public void setRutas(List<Ruta> rutas) { this.rutas = rutas; }
    public List<Pedido> getPedidosNoAsignados() { return pedidosNoAsignados; }
    public void setPedidosNoAsignados(List<Pedido> p) { this.pedidosNoAsignados = p; }
    public double getValorFuncionObjetivo() { return valorFuncionObjetivo; }
    public void setValorFuncionObjetivo(double v) { this.valorFuncionObjetivo = v; }
    public double getCostoTransporte() { return costoTransporte; }
    public void setCostoTransporte(double c) { this.costoTransporte = c; }
    public double getValorR() { return valorR; }
    public void setValorR(double v) { this.valorR = v; }
    public double getValorT() { return valorT; }
    public void setValorT(double v) { this.valorT = v; }
    public int getValorN() { return valorN; }
    public void setValorN(int v) { this.valorN = v; }
    public double getValorV() { return valorV; }
    public void setValorV(double v) { this.valorV = v; }
    public boolean isEsFactible() { return esFactible; }
    public void setEsFactible(boolean f) { this.esFactible = f; }
}
