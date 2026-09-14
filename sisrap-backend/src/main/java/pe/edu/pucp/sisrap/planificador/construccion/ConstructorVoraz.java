package pe.edu.pucp.sisrap.planificador.construccion;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

import pe.edu.pucp.sisrap.dominio.Almacen;
import pe.edu.pucp.sisrap.dominio.Nodo;
import pe.edu.pucp.sisrap.dominio.Pedido;
import pe.edu.pucp.sisrap.dominio.Vehiculo;
import pe.edu.pucp.sisrap.planificador.modelo.ContextoPlanificacion;
import pe.edu.pucp.sisrap.planificador.modelo.Ruta;
import pe.edu.pucp.sisrap.planificador.modelo.Solucion;

public class ConstructorVoraz {
    private ConstructorVoraz() {
        // Clase utilitaria, no instanciable.
    }

    /**
     * Construye una solución completa (rutas vacías + inserción de pedidos).
     *
     * @param contexto          contexto de planificación vigente (pedidos, vehículos, almacenes).
     * @param random            generador aleatorio (debe compartir semilla con el algoritmo llamante).
     * @param ordenarPorPlazo   si es true, los pedidos se insertan primero por plazo más
     *                          exigente (orden voraz); si es false, se insertan en orden
     *                          aleatorio (preserva diversidad en la población del GA).
     */
    public static Solucion construir(ContextoPlanificacion contexto, Random random, boolean ordenarPorPlazo) {
        Solucion solucion = new Solucion();
        List<Ruta> rutas = crearRutasVacias(contexto);
        solucion.setRutas(rutas);

        List<Pedido> pedidosOrdenados = ordenarPedidos(contexto.getPedidos(), random, ordenarPorPlazo);

        List<Pedido> noAsignados = new ArrayList<>();
        insertarPedidos(rutas, pedidosOrdenados, noAsignados);

        solucion.setPedidosNoAsignados(noAsignados);
        for (Ruta r : rutas) r.recalcular();
        return solucion;
    }

    /** Crea una ruta vacía por cada vehículo, partiendo del almacén disponible más cercano. */
    public static List<Ruta> crearRutasVacias(ContextoPlanificacion contexto) {
        List<Ruta> rutas = new ArrayList<>();
        for (Vehiculo v : contexto.getVehiculos()) {
            Almacen origen = almacenMasCercano(v, contexto.getAlmacenes());
            rutas.add(new Ruta(v, origen));
        }
        return rutas;
    }

    /**
     * Determina el orden en que los pedidos serán insertados.
     * Con ordenarPorPlazo=true se prioriza el plazo más exigente (voraz);
     * dentro de un mismo nivel de prioridad, el orden se aleatoriza para
     * evitar sesgos sistemáticos de inserción.
     */
    public static List<Pedido> ordenarPedidos(List<Pedido> pedidos, Random random, boolean ordenarPorPlazo) {
        List<Pedido> copia = new ArrayList<>(pedidos);
        Collections.shuffle(copia, random);

        if (ordenarPorPlazo) {
            copia.sort(Comparator.comparingInt(p -> p.getPrioridad().getHorasLimite()));
        }
        return copia;
    }

    /**
     * Inserción voraz por costo mínimo: cada pedido se asigna a la ruta factible
     * (vehículo disponible + capacidad suficiente) cuyo último nodo visitado
     * queda más cerca de la ubicación del pedido. Si ninguna ruta puede recibirlo,
     * queda registrado como no asignado (penalizado luego como V(S)).
     */
    public static void insertarPedidos(List<Ruta> rutas, List<Pedido> pedidos, List<Pedido> noAsignados) {
        for (Pedido pedido : pedidos) {
            Ruta mejorRuta = null;
            int menorDistanciaInsercion = Integer.MAX_VALUE;

            for (Ruta ruta : rutas) {
                if (!ruta.getVehiculo().isDisponible()) continue;
                if (ruta.cargaTotal() + pedido.getCantidadQq() > ruta.getVehiculo().getCapacidadPaquetes()) continue;

                Nodo ultimoNodo = ruta.getSecuenciaPedidos().isEmpty()
                        ? ruta.getAlmacenOrigen().getUbicacion()
                        : ruta.getSecuenciaPedidos().get(ruta.getSecuenciaPedidos().size() - 1).getUbicacion();

                int distancia = ultimoNodo.distanciaManhattan(pedido.getUbicacion());
                if (distancia < menorDistanciaInsercion) {
                    menorDistanciaInsercion = distancia;
                    mejorRuta = ruta;
                }
            }

            if (mejorRuta != null) {
                mejorRuta.getSecuenciaPedidos().add(pedido);
            } else {
                noAsignados.add(pedido);
            }
        }
    }

    /** Almacén disponible (con stock) más cercano a la posición actual del vehículo. */
    public static Almacen almacenMasCercano(Vehiculo v, List<Almacen> almacenes) {
        if (v.getPosicionActual() == null) {
            return almacenes.get(0); // fallback: almacén central por defecto
        }

        Almacen masCercano = null;
        int menorDistancia = Integer.MAX_VALUE;

        for (Almacen almacen : almacenes) {
            if (!almacen.tieneStockDisponible()) {
                continue; // no puede abastecerse desde un almacén sin stock
            }
            int distancia = v.getPosicionActual().distanciaManhattan(almacen.getUbicacion());
            if (distancia < menorDistancia) {
                menorDistancia = distancia;
                masCercano = almacen;
            }
        }
        return (masCercano != null) ? masCercano : almacenes.get(0);
    }
}
