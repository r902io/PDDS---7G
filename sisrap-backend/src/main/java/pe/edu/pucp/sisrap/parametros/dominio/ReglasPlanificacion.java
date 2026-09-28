package pe.edu.pucp.sisrap.parametros.dominio;

public record ReglasPlanificacion(
        double servicioHoras,
        double distanciaNodoKm,
        boolean servicioDentroPlazo,
        boolean incluirRetorno,
        int anchoCiudad,
        int altoCiudad) {

    public ReglasPlanificacion {
        if (!Double.isFinite(servicioHoras)
                || servicioHoras < 0
                || !Double.isFinite(distanciaNodoKm)
                || distanciaNodoKm <= 0) {
            throw new IllegalArgumentException(
                    "Servicio o distancia inválidos");
        }

        if (anchoCiudad <= 0 || altoCiudad <= 0) {
            throw new IllegalArgumentException(
                    "Dimensiones de ciudad inválidas");
        }
    }

    public static ReglasPlanificacion desde(Configuracion c) {
        return desde(c, 70, 50);
    }

    public static ReglasPlanificacion desde(
            Configuracion c,
            int anchoCiudad,
            int altoCiudad) {

        return new ReglasPlanificacion(
                c.numero("operacion.servicioHoras"),
                c.numero("operacion.distanciaNodoKm"),
                c.booleano("operacion.servicioDentroPlazo"),
                c.booleano("operacion.incluirRetorno"),
                anchoCiudad,
                altoCiudad);
    }
}