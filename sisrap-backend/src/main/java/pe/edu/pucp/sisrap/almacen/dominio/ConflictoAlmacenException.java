package pe.edu.pucp.sisrap.almacen.dominio;

public final class ConflictoAlmacenException extends RuntimeException {

    public ConflictoAlmacenException(String mensaje) {
        super(mensaje);
    }
}