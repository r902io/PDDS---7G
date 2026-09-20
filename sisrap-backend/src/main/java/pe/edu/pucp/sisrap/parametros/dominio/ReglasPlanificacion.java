package pe.edu.pucp.sisrap.parametros.dominio;
public record ReglasPlanificacion(double servicioHoras, double distanciaNodoKm,
                                  boolean servicioDentroPlazo, boolean incluirRetorno) {
    public ReglasPlanificacion {
        if(!Double.isFinite(servicioHoras) || servicioHoras<0 || !Double.isFinite(distanciaNodoKm) || distanciaNodoKm<=0)
            throw new IllegalArgumentException("Servicio o distancia inválidos");
    }
    public static ReglasPlanificacion desde(Configuracion c) {
        return new ReglasPlanificacion(c.numero("operacion.servicioHoras"),c.numero("operacion.distanciaNodoKm"),
            c.booleano("operacion.servicioDentroPlazo"),c.booleano("operacion.incluirRetorno"));
    }
}
