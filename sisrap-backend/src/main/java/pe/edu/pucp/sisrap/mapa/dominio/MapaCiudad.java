package pe.edu.pucp.sisrap.mapa.dominio;

public record MapaCiudad(
        int idCiudad,
        String nombre,
        int anchoKm,
        int altoKm
) {
}