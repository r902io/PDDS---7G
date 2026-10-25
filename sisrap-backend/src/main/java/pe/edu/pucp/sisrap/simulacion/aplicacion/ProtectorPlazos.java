package pe.edu.pucp.sisrap.simulacion.aplicacion;

import java.util.ArrayList;
import java.util.Comparator;
import pe.edu.pucp.sisrap.planificador.dominio.modelo.ContextoPlanificacion;
import pe.edu.pucp.sisrap.planificador.dominio.modelo.Ruta;
import pe.edu.pucp.sisrap.planificador.dominio.modelo.Solucion;
import pe.edu.pucp.sisrap.planificador.dominio.objetivo.FuncionObjetivo;

/** Puerta de validación del despacho; GA conserva la decisión de asignación. */
public final class ProtectorPlazos {
    private ProtectorPlazos() { }
    public static Solucion proteger(ContextoPlanificacion contexto, Solucion solucion,
                                    FuncionObjetivo objetivo, long semilla) {
        objetivo.actualizarContexto(contexto);
        objetivo.calcular(solucion);
        if (solucion.getValorV() == 0 && solucion.getPedidosTardios() == 0) return solucion;

        Solucion segura = new Solucion();
        var rutas = new ArrayList<>(solucion.getRutas().stream()
                .filter(r -> !r.getSecuenciaPedidos().isEmpty()).toList());
        rutas.sort(Comparator.comparing(r -> r.getSecuenciaPedidos().stream()
                .map(p -> p.getFechaLimite()).min(Comparator.naturalOrder()).orElseThrow()));
        for (Ruta ruta : rutas) {
            Solucion candidata = segura.copiar();
            candidata.getRutas().add(ruta.copiar());
            objetivo.calcular(candidata);
            if (candidata.getValorV() == 0 && candidata.getPedidosTardios() == 0)
                segura = candidata;
        }
        objetivo.calcular(segura);
        return segura;
    }
}
