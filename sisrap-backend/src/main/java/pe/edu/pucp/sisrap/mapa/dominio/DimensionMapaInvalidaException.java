package pe.edu.pucp.sisrap.mapa.dominio;

public final class DimensionMapaInvalidaException
        extends RuntimeException {

    public DimensionMapaInvalidaException(
            String mensaje
    ) {
        super(mensaje);
    }
}