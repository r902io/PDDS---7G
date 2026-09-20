package pe.edu.pucp.sisrap.experimentacion.dominio;

import java.util.List;

import pe.edu.pucp.sisrap.almacen.dominio.Almacen;
import pe.edu.pucp.sisrap.flota.dominio.Vehiculo;
import pe.edu.pucp.sisrap.geografia.dominio.Nodo;
import pe.edu.pucp.sisrap.parametros.dominio.Configuracion;


//Todo lo que el experimento toma de la base de datos: parámetros del perfil, tamaño de la ciudad, almacenes y flota.
public record BaseOperativa(Configuracion configuracion, int anchoKm, int altoKm, Nodo central,
                            List<Almacen> almacenes, List<Vehiculo> vehiculos) {
    public BaseOperativa {
        almacenes = List.copyOf(almacenes);
        vehiculos = List.copyOf(vehiculos);
    }
}