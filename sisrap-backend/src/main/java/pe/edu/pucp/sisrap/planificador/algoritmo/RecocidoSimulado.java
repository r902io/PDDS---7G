package pe.edu.pucp.sisrap.planificador.algoritmo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import pe.edu.pucp.sisrap.dominio.Almacen;
import pe.edu.pucp.sisrap.dominio.Pedido;
import pe.edu.pucp.sisrap.dominio.Vehiculo;
import pe.edu.pucp.sisrap.planificador.config.ParametrosAlgoritmo;
import pe.edu.pucp.sisrap.planificador.modelo.ContextoPlanificacion;
import pe.edu.pucp.sisrap.planificador.modelo.Ruta;
import pe.edu.pucp.sisrap.planificador.modelo.Solucion;
import pe.edu.pucp.sisrap.planificador.objetivo.FuncionObjetivo;

/*
    Recocido Simulado.
    Usa la misma representación que el GA (lista de rutas)
*/
public class RecocidoSimulado implements IAlgoritmoPlanificacion{
    private final ParametrosAlgoritmo parametros;
    private final FuncionObjetivo funcionObjetivo;
    private final Random random;
    private Solucion solucionActual;

    public RecocidoSimulado(ParametrosAlgoritmo parametros, FuncionObjetivo funcionObjetivo) {
        this.parametros = parametros;
        this.funcionObjetivo = funcionObjetivo;
        this.random = (parametros.semillaAleatoria != null)
                ? new Random(parametros.semillaAleatoria)
                : new Random();
    }

    @Override
    public Solucion planificar(ContextoPlanificacion contexto) {
        solucionActual = construirSolucionInicial(contexto);
        funcionObjetivo.calcular(solucionActual);
        return enfriarDesde(parametros.temperaturaInicial);
    }

    @Override
    public Solucion replanificar(Solucion solucionAnterior, ContextoPlanificacion contextoActualizado) {

        solucionActual = repararSolucion(solucionAnterior, contextoActualizado);
        funcionObjetivo.calcular(solucionActual);

        double temperaturaReplanificacion = parametros.temperaturaInicial * 0.3;
        return enfriarDesde(temperaturaReplanificacion);
    }

    // Ciclo principal de enfriamiento
    private Solucion enfriarDesde(double temperaturaInicial) {
        Solucion mejorSolucion = solucionActual.copiar();
        double temperatura = temperaturaInicial;

        while (temperatura > parametros.temperaturaFinal) {
            for (int i = 0; i < parametros.iteracionesPorTemperatura; i++) {
                Solucion vecino = generarVecino(solucionActual);
                funcionObjetivo.calcular(vecino);

                double delta = vecino.getValorFuncionObjetivo()
                        - solucionActual.getValorFuncionObjetivo();

                if (aceptar(delta, temperatura)) {
                    solucionActual = vecino;
                    if (solucionActual.getValorFuncionObjetivo()
                            < mejorSolucion.getValorFuncionObjetivo()) {
                        mejorSolucion = solucionActual.copiar();
                    }
                }
            }
            temperatura = enfriar(temperatura);
        }
        return mejorSolucion;
    }

    // Construcción de solución inicial (greedy aleatorizado)
    private Solucion construirSolucionInicial(ContextoPlanificacion contexto) {
        Solucion solucion = new Solucion();

        List<Ruta> rutas = new ArrayList<>();
        for (Vehiculo v : contexto.getVehiculos()) {
            Almacen origen = contexto.getAlmacenes().get(0); // almacén central en la primera salida
            rutas.add(new Ruta(v, origen));
        }
        solucion.setRutas(rutas);

        List<Pedido> pedidosMezclados = new ArrayList<>(contexto.getPedidos());
        Collections.shuffle(pedidosMezclados, random);

        List<Pedido> noAsignados = new ArrayList<>();
        for (Pedido pedido : pedidosMezclados) {
            Ruta rutaConEspacio = rutas.stream()
                    .filter(r -> r.getVehiculo().isDisponible())
                    .filter(r -> r.cargaTotal() + pedido.getCantidadQq()
                            <= r.getVehiculo().getCapacidadPaquetes())
                    .findAny()
                    .orElse(null);

            if (rutaConEspacio != null) {
                rutaConEspacio.getSecuenciaPedidos().add(pedido);
            } else {
                noAsignados.add(pedido);
            }
        }
        solucion.setPedidosNoAsignados(noAsignados);
        for (Ruta r : rutas) r.recalcular();
        return solucion;
    }

    // Generación de vecinos: 2-opt, swap, relocate, cross-route relocate, vehicle change
    private Solucion generarVecino(Solucion base) {
        Solucion vecino = base.copiar();
        int movimiento = random.nextInt(5);

        switch (movimiento) {
            case 0 -> aplicarDosOpt(vecino);
            case 1 -> aplicarSwap(vecino);
            case 2 -> aplicarRelocate(vecino);
            case 3 -> aplicarCrossRouteRelocate(vecino);
            default -> aplicarVehicleChange(vecino);
        }

        for (Ruta r : vecino.getRutas()) r.recalcular();
        return vecino;
    }

    // 2-opt: invierte un segmento dentro de una misma ruta.
    private void aplicarDosOpt(Solucion solucion) {
        Ruta ruta = rutaConPedidosAlAzar(solucion, 2);
        if (ruta == null) return;

        List<Pedido> secuencia = ruta.getSecuenciaPedidos();
        int i = random.nextInt(secuencia.size());
        int j = random.nextInt(secuencia.size());
        if (i > j) { int tmp = i; i = j; j = tmp; }
        Collections.reverse(secuencia.subList(i, j + 1));
    }

    // Swap: intercambia dos pedidos dentro de la misma ruta.
    private void aplicarSwap(Solucion solucion) {
        Ruta ruta = rutaConPedidosAlAzar(solucion, 2);
        if (ruta == null) return;

        List<Pedido> secuencia = ruta.getSecuenciaPedidos();
        int i = random.nextInt(secuencia.size());
        int j = random.nextInt(secuencia.size());
        Collections.swap(secuencia, i, j);
    }

    //Relocate: retira un pedido y lo reinserta en otra posición de la misma ruta.
    private void aplicarRelocate(Solucion solucion) {
        Ruta ruta = rutaConPedidosAlAzar(solucion, 2);
        if (ruta == null) return;

        List<Pedido> secuencia = ruta.getSecuenciaPedidos();
        int origen = random.nextInt(secuencia.size());
        Pedido pedido = secuencia.remove(origen);
        int destino = random.nextInt(secuencia.size() + 1);
        secuencia.add(destino, pedido);
    }

    // Cross-route relocate: mueve un pedido de una ruta a otra con capacidad disponible.
    private void aplicarCrossRouteRelocate(Solucion solucion) {
        List<Ruta> conPedidos = solucion.getRutas().stream()
                .filter(r -> !r.getSecuenciaPedidos().isEmpty())
                .toList();
        if (conPedidos.isEmpty()) return;

        Ruta rutaOrigen = conPedidos.get(random.nextInt(conPedidos.size()));
        int idx = random.nextInt(rutaOrigen.getSecuenciaPedidos().size());
        Pedido pedido = rutaOrigen.getSecuenciaPedidos().get(idx);

        List<Ruta> destinosValidos = solucion.getRutas().stream()
                .filter(r -> r != rutaOrigen && r.getVehiculo().isDisponible())
                .filter(r -> r.cargaTotal() + pedido.getCantidadQq()
                        <= r.getVehiculo().getCapacidadPaquetes())
                .toList();
        if (destinosValidos.isEmpty()) return;

        Ruta rutaDestino = destinosValidos.get(random.nextInt(destinosValidos.size()));
        rutaOrigen.getSecuenciaPedidos().remove(idx);
        rutaDestino.getSecuenciaPedidos().add(pedido);
    }

    
    // Vehicle change: intercambia completamente el vehículo asignado a una ruta por otro disponible con capacidad suficiente para la carga actual.
    private void aplicarVehicleChange(Solucion solucion) {
        List<Ruta> conPedidos = solucion.getRutas().stream()
                .filter(r -> !r.getSecuenciaPedidos().isEmpty())
                .toList();
        if (conPedidos.isEmpty()) return;

        Ruta ruta = conPedidos.get(random.nextInt(conPedidos.size()));
        List<Ruta> candidatas = solucion.getRutas().stream()
                .filter(r -> r != ruta)
                .filter(r -> r.getVehiculo().isDisponible())
                .filter(r -> r.getVehiculo().getCapacidadPaquetes() >= ruta.cargaTotal())
                .toList();
        if (candidatas.isEmpty()) return;

        Ruta candidata = candidatas.get(random.nextInt(candidatas.size()));
        List<Pedido> temp = new ArrayList<>(ruta.getSecuenciaPedidos());
        ruta.getSecuenciaPedidos().clear();
        candidata.getSecuenciaPedidos().addAll(temp);
    }

    private Ruta rutaConPedidosAlAzar(Solucion solucion, int minimoPedidos) {
        List<Ruta> candidatas = solucion.getRutas().stream()
                .filter(r -> r.getSecuenciaPedidos().size() >= minimoPedidos)
                .toList();
        if (candidatas.isEmpty()) return null;
        return candidatas.get(random.nextInt(candidatas.size()));
    }

    // Criterio de aceptación y enfriamiento
    private boolean aceptar(double delta, double temperatura) {
        if (delta < 0) return true;
        double probabilidad = Math.exp(-delta / temperatura);
        return random.nextDouble() < probabilidad;
    }

    private double enfriar(double temperatura) {
        return temperatura * parametros.factorEnfriamiento;
    }

    // Replanificación: filtra pedidos ya entregados y repara duplicados/faltantes
    private Solucion repararSolucion(Solucion solucionAnterior, ContextoPlanificacion contexto) {
        Solucion reparada = solucionAnterior.copiar();
        Set<Long> pedidosVigentes = new HashSet<>();
        for (Pedido p : contexto.getPedidos()) pedidosVigentes.add(p.getIdPedido());

        for (Ruta ruta : reparada.getRutas()) {
            ruta.getSecuenciaPedidos().removeIf(p -> !pedidosVigentes.contains(p.getIdPedido()));
            ruta.recalcular();
        }

        Set<Long> vistos = new HashSet<>();
        for (Ruta ruta : reparada.getRutas()) {
            for (Pedido p : ruta.getSecuenciaPedidos()) vistos.add(p.getIdPedido());
        }

        List<Pedido> faltantes = new ArrayList<>();
        for (Pedido p : contexto.getPedidos()) {
            if (!vistos.contains(p.getIdPedido())) faltantes.add(p);
        }

        List<Pedido> noAsignados = new ArrayList<>();
        for (Pedido pedido : faltantes) {
            Ruta rutaConEspacio = reparada.getRutas().stream()
                    .filter(r -> r.getVehiculo().isDisponible())
                    .filter(r -> r.cargaTotal() + pedido.getCantidadQq()
                            <= r.getVehiculo().getCapacidadPaquetes())
                    .findAny()
                    .orElse(null);

            if (rutaConEspacio != null) {
                rutaConEspacio.getSecuenciaPedidos().add(pedido);
            } else {
                noAsignados.add(pedido);
            }
        }
        reparada.setPedidosNoAsignados(noAsignados);
        for (Ruta r : reparada.getRutas()) r.recalcular();
        return reparada;
    }
}
