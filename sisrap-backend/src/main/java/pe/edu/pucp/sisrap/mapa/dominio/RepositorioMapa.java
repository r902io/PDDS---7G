package pe.edu.pucp.sisrap.mapa.dominio;

public interface RepositorioMapa {

    MapaCiudad obtener();

    MapaCiudad cambiarDimensiones(
            int ancho,
            int alto
    );
}