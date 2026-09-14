package pe.edu.pucp.sisrap.planificador.algoritmo;


import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import pe.edu.pucp.sisrap.dominio.Pedido;
import pe.edu.pucp.sisrap.planificador.config.ParametrosAlgoritmo;
import pe.edu.pucp.sisrap.planificador.construccion.ConstructorVoraz;
import pe.edu.pucp.sisrap.planificador.modelo.ContextoPlanificacion;
import pe.edu.pucp.sisrap.planificador.modelo.Individuo;
import pe.edu.pucp.sisrap.planificador.modelo.Ruta;
import pe.edu.pucp.sisrap.planificador.modelo.Solucion;
import pe.edu.pucp.sisrap.planificador.objetivo.FuncionObjetivo;

/*
   Algoritmo Genético
   un individuo = una lista de rutas
   cada ruta contiene la secuencia ordenada de pedidos que atiende.
*/
public class AlgoritmoGenetico implements IAlgoritmoPlanificacion{
    private final ParametrosAlgoritmo parametros;
    private final FuncionObjetivo funcionObjetivo;
    private final Random random;
    private List<Individuo> poblacion;

    public AlgoritmoGenetico(ParametrosAlgoritmo parametros, FuncionObjetivo funcionObjetivo) {
        this.parametros = parametros;
        this.funcionObjetivo = funcionObjetivo;
        this.random = (parametros.semillaAleatoria != null)
                ? new Random(parametros.semillaAleatoria)
                : new Random();
    }

    @Override
    public Solucion planificar(ContextoPlanificacion contexto) {
        poblacion = new ArrayList<>();
        for (int i = 0; i < parametros.tamanioPoblacion; i++) {
            boolean sesgoVoraz = (i % 2 == 0);
            Solucion base = ConstructorVoraz.construir(contexto, random, sesgoVoraz);
            poblacion.add(new Individuo(base));
        }

        return ejecutarEvolucion();
    }

    @Override
    public Solucion replanificar(Solucion solucionActual, ContextoPlanificacion contextoActualizado) {
        // Semilla la población con la solución actual más variantes perturbadas.
        poblacion = new ArrayList<>();
        Solucion base = repararSolucion(solucionActual, contextoActualizado);
        poblacion.add(new Individuo(base));

        for (int i = 1; i < parametros.tamanioPoblacion; i++) {
            Solucion variante = base.copiar();
            mutar(variante);
            poblacion.add(new Individuo(variante));
        }

        return ejecutarEvolucion();
    }

    private Solucion ejecutarEvolucion() {
        evaluarPoblacion();

        for (int gen = 0; gen < parametros.numGeneraciones; gen++) {
            poblacion.sort((a, b) -> Double.compare(a.getFitness(), b.getFitness()));

            List<Individuo> nuevaPoblacion = new ArrayList<>();
            for (int i = 0; i < parametros.elitismo; i++) {
                nuevaPoblacion.add(poblacion.get(i).copiar());
            }

            while (nuevaPoblacion.size() < parametros.tamanioPoblacion) {
                Individuo padre1 = seleccionTorneo();
                Individuo padre2 = seleccionTorneo();

                Solucion hijoSolucion = (random.nextDouble() < parametros.probCruzamiento)
                        ? cruzar(padre1.getCromosoma(), padre2.getCromosoma())
                        : padre1.getCromosoma().copiar();

                Individuo hijo = new Individuo(hijoSolucion);

                if (random.nextDouble() < parametros.probMutacion) {
                    mutar(hijo.getCromosoma());
                }

                nuevaPoblacion.add(hijo);
            }

            poblacion = nuevaPoblacion;
            evaluarPoblacion();
        }

        poblacion.sort((a, b) -> Double.compare(a.getFitness(), b.getFitness()));
        return poblacion.get(0).getCromosoma();
    }

    // Operadores genéticos
    private void evaluarPoblacion() {
        for (Individuo ind : poblacion) {
            double valor = funcionObjetivo.calcular(ind.getCromosoma());
            ind.setFitness(valor);
        }
    }

    private Individuo seleccionTorneo() {
        Individuo mejor = poblacion.get(random.nextInt(poblacion.size()));
        for (int i = 1; i < parametros.tamanioTorneo; i++) {
            Individuo candidato = poblacion.get(random.nextInt(poblacion.size()));
            if (candidato.getFitness() < mejor.getFitness()) {
                mejor = candidato;
            }
        }
        return mejor;
    }

    /*
        Cruzamiento: para cada vehículo, hereda la secuencia de uno de los dos padres. 
        Luego repara duplicados y pedidos faltantes para mantener la solución consistente.
    */
    private Solucion cruzar(Solucion padre1, Solucion padre2) {
        Solucion hijo = new Solucion();
        List<Ruta> rutasHijo = new ArrayList<>();

        for (int i = 0; i < padre1.getRutas().size(); i++) {
            Ruta origen = (random.nextBoolean() ? padre1 : padre2).getRutas().get(i);
            Ruta copia = origen.copiar();
            rutasHijo.add(copia);
        }
        hijo.setRutas(rutasHijo);

        repararDuplicadosYFaltantes(hijo, todosLosPedidos(padre1));
        return hijo;
    }

    private List<Pedido> todosLosPedidos(Solucion solucion) {
        List<Pedido> todos = new ArrayList<>();
        for (Ruta r : solucion.getRutas()) todos.addAll(r.getSecuenciaPedidos());
        todos.addAll(solucion.getPedidosNoAsignados());
        return todos;
    }

    // Elimina pedidos duplicados entre rutas y reinserta los que quedaron sin asignar
    private void repararDuplicadosYFaltantes(Solucion solucion, List<Pedido> universoPedidos) {
        Set<Long> vistos = new HashSet<>();
        for (Ruta ruta : solucion.getRutas()) {
            ruta.getSecuenciaPedidos().removeIf(p -> !vistos.add(p.getIdPedido()));
        }

        List<Pedido> faltantes = new ArrayList<>();
        for (Pedido p : universoPedidos) {
            if (!vistos.contains(p.getIdPedido())) {
                faltantes.add(p);
                vistos.add(p.getIdPedido());
            }
        }

        List<Pedido> faltantesOrdenados = ConstructorVoraz.ordenarPedidos(faltantes, random, true);

        List<Pedido> noAsignados = new ArrayList<>();
        ConstructorVoraz.insertarPedidos(solucion.getRutas(), faltantesOrdenados, noAsignados);

        solucion.setPedidosNoAsignados(noAsignados);
        for (Ruta r : solucion.getRutas()) r.recalcular();
    }

    // Mutación: intercambio de dos pedidos dentro de una ruta o reubicación entre rutas.
    private void mutar(Solucion solucion) {
        List<Ruta> rutasConPedidos = solucion.getRutas().stream()
                .filter(r -> !r.getSecuenciaPedidos().isEmpty())
                .toList();
        if (rutasConPedidos.isEmpty()) return;

        if (random.nextBoolean()) {
            // Swap dentro de la misma ruta
            Ruta ruta = rutasConPedidos.get(random.nextInt(rutasConPedidos.size()));
            List<Pedido> secuencia = ruta.getSecuenciaPedidos();
            if (secuencia.size() >= 2) {
                int i = random.nextInt(secuencia.size());
                int j = random.nextInt(secuencia.size());
                Collections.swap(secuencia, i, j);
            }
        } else {
            // Relocate: mover un pedido de una ruta a otra con espacio
            Ruta rutaOrigen = rutasConPedidos.get(random.nextInt(rutasConPedidos.size()));
            int idx = random.nextInt(rutaOrigen.getSecuenciaPedidos().size());
            Pedido pedido = rutaOrigen.getSecuenciaPedidos().get(idx);

            Ruta rutaDestino = solucion.getRutas().stream()
                    .filter(r -> r != rutaOrigen && r.getVehiculo().isDisponible())
                    .filter(r -> r.cargaTotal() + pedido.getCantidadQq()
                            <= r.getVehiculo().getCapacidadPaquetes())
                    .findAny()
                    .orElse(null);

            if (rutaDestino != null) {
                rutaOrigen.getSecuenciaPedidos().remove(idx);
                rutaDestino.getSecuenciaPedidos().add(pedido);
            }
        }
        for (Ruta r : solucion.getRutas()) r.recalcular();
    }

    private Solucion repararSolucion(Solucion solucionActual, ContextoPlanificacion contexto) {
        Solucion reparada = solucionActual.copiar();
        // Filtrar de cada ruta los pedidos que ya no forman parte del contexto
        Set<Long> pedidosVigentes = new HashSet<>();
        for (Pedido p : contexto.getPedidos()) pedidosVigentes.add(p.getIdPedido());

        for (Ruta ruta : reparada.getRutas()) {
            ruta.getSecuenciaPedidos().removeIf(p -> !pedidosVigentes.contains(p.getIdPedido()));
            ruta.recalcular();
        }
        repararDuplicadosYFaltantes(reparada, contexto.getPedidos());
        return reparada;
    }
}
