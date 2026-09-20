package pe.edu.pucp.sisrap.planificador.dominio.algoritmo;


import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import pe.edu.pucp.sisrap.parametros.dominio.ParametrosAlgoritmo;
import pe.edu.pucp.sisrap.pedido.dominio.Pedido;
import pe.edu.pucp.sisrap.planificador.dominio.construccion.ConstructorVoraz;
import pe.edu.pucp.sisrap.planificador.dominio.modelo.ContextoPlanificacion;
import pe.edu.pucp.sisrap.planificador.dominio.modelo.Individuo;
import pe.edu.pucp.sisrap.planificador.dominio.modelo.PuntoConvergencia;
import pe.edu.pucp.sisrap.planificador.dominio.modelo.Ruta;
import pe.edu.pucp.sisrap.planificador.dominio.modelo.Solucion;
import pe.edu.pucp.sisrap.planificador.dominio.objetivo.FuncionObjetivo;

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
    private final List<PuntoConvergencia> traza = new ArrayList<>();
    private int evaluaciones;
    public List<PuntoConvergencia> getConvergencia(){ return List.copyOf(traza); }

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
            boolean sesgoVoraz = (i < Math.ceil(parametros.tamanioPoblacion * parametros.proporcionVoraz));
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
        evaluaciones=0; traza.clear();
        evaluarPoblacion();
        registrar(0);

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
            registrar(gen + 1);
        }

        poblacion.sort((a, b) -> Double.compare(a.getFitness(), b.getFitness()));
        return poblacion.get(0).getCromosoma();
    }

    // Operadores genéticos
    private void evaluarPoblacion() {
        for (Individuo ind : poblacion) {
            evaluaciones++;
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

    private void mutar(Solucion s) {
        Solucion vecino=new Movimientos(random,parametros.movimientos).vecino(s);
        s.setRutas(vecino.getRutas());s.setPedidosNoAsignados(vecino.getPedidosNoAsignados());
    }
    private Solucion repararSolucion(Solucion s,ContextoPlanificacion c){
        return ConstructorVoraz.reparar(s,c,random);
    }
    private void registrar(int iteracion){
        double mejor=poblacion.stream().mapToDouble(Individuo::getFitness).min().orElseThrow();
        traza.add(new PuntoConvergencia(iteracion,evaluaciones,mejor));
    }
}
