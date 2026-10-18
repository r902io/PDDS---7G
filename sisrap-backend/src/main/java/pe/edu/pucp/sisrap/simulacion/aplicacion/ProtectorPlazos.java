package pe.edu.pucp.sisrap.simulacion.aplicacion;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import pe.edu.pucp.sisrap.pedido.dominio.Pedido;
import pe.edu.pucp.sisrap.planificador.dominio.construccion.ConstructorVoraz;
import pe.edu.pucp.sisrap.planificador.dominio.modelo.ContextoPlanificacion;
import pe.edu.pucp.sisrap.planificador.dominio.modelo.Solucion;
import pe.edu.pucp.sisrap.planificador.dominio.objetivo.FuncionObjetivo;

/**
 * Salvaguarda operativa: GA sigue siendo el optimizador y mantiene intacta su
 * funcion objetivo. Si GA deja un pedido urgente sin ruta y una construccion
 * EDF consigue cubrirlo fisicamente sin retrasos, se usa esa alternativa.
 * Nunca se falsean plazos, sobrecargan vehiculos ni se omiten bloqueos.
 */
public final class ProtectorPlazos {

    private ProtectorPlazos() { }

    public static Solucion proteger(ContextoPlanificacion contexto,
                                    Solucion solucionGa,
                                    FuncionObjetivo objetivo,
                                    long semilla) {
        objetivo.calcular(solucionGa);
        if (solucionGa.getPedidosNoAsignados().isEmpty()) return solucionGa;

        Solucion alternativa = ConstructorVoraz.construir(
                contexto, new Random(semilla ^ 0x51A51A5L), true);
        objetivo.calcular(alternativa);
        if (!alternativa.isEsFactible() || alternativa.getValorR() > 1e-9) {
            return solucionGa;
        }
        if (!solucionGa.isEsFactible() || solucionGa.getValorR() > 1e-9) {
            return alternativa;
        }

        List<Pedido> criticos = contexto.getPedidos().stream()
                .sorted(Comparator.comparing(Pedido::getFechaLimite)
                        .thenComparing(Pedido::getIdPedido))
                .toList();
        Set<Long> ga = asignados(solucionGa);
        Set<Long> edf = asignados(alternativa);
        for (Pedido pedido : criticos) {
            boolean cubreGa = ga.contains(pedido.getIdPedido());
            boolean cubreEdf = edf.contains(pedido.getIdPedido());
            if (cubreGa != cubreEdf) {
                return cubreEdf ? alternativa : solucionGa;
            }
        }
        return solucionGa;
    }

    private static Set<Long> asignados(Solucion solucion) {
        Set<Long> ids = new HashSet<>();
        solucion.getRutas().forEach(ruta -> ruta.getSecuenciaPedidos()
                .forEach(pedido -> ids.add(pedido.getIdPedido())));
        return ids;
    }
}
