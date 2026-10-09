package pe.edu.pucp.sisrap.configuracionoperativa.dominio;

import java.util.List;

/** Plantilla editable ANTES de la ejecución; no son recursos operativos de una corrida. */
public record ConfiguracionOperativa(
        List<AlmacenConfigurado> almacenes,
        List<TipoFlotaConfigurado> flota) {

    public ConfiguracionOperativa {
        almacenes = almacenes == null ? null : List.copyOf(almacenes);
        flota = flota == null ? null : List.copyOf(flota);
    }

    public record AlmacenConfigurado(
            String idAlmacen, String nombre, String tipo,
            int ubicacionX, int ubicacionY, Integer capacidadMaxima) { }

    public record TipoFlotaConfigurado(
            String tipo, int cantidad, int capacidadPaquetes,
            double velocidadKmh, double costoPorKm) { }
}
