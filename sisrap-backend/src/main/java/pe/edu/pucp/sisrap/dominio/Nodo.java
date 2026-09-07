package pe.edu.pucp.sisrap.dominio;

public class Nodo {
    private final int x;
    private final int y;

    public Nodo(int x, int y) {
        this.x = x;
        this.y = y;
    }

    public int distanciaManhattan(Nodo otro) {
        return Math.abs(this.x - otro.x) + Math.abs(this.y - otro.y);
    }

    public int getX() { return x; }
    public int getY() { return y; }
}
