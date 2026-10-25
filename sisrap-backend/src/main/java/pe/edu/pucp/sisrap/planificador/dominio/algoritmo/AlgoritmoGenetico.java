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
    private ContextoPlanificacion contextoOperativo;
    private final long presupuestoOperativoMs;
    private long finBusqueda;
    private Individuo mejorOperativo;

    private boolean quedaTiempo() {
        return !Thread.currentThread().isInterrupted() && System.nanoTime() < finBusqueda;
    }
    private final java.util.Map<Solucion, Calidad> calidades = new java.util.IdentityHashMap<>();
    private List<Pedido> urgentes = List.of();
    private final java.util.Map<Long, Integer> ordenUrgente = new java.util.HashMap<>();

    private record Calidad(int violaciones, int tardios, int noAsignados,
                           List<Boolean> cobertura, double retraso, double margen) { }

    /** El flujo experimental conserva su selección y construcción originales. */
    private void prepararContexto(ContextoPlanificacion contexto) {
        contextoOperativo = contexto.respetarPosicionVehiculo() ? contexto : null;
        calidades.clear();
        ordenUrgente.clear();
        if (contextoOperativo == null) return;
        urgentes = ConstructorVoraz.ordenarPorUrgencia(contexto);
        for (int i = 0; i < urgentes.size(); i++) ordenUrgente.put(urgentes.get(i).getIdPedido(), i);
    }

    private double horasHastaLimite(Pedido pedido) {
        return java.time.Duration.between(contextoOperativo.instante(), pedido.getFechaLimite())
                .toNanos() / 3_600_000_000_000.0;
    }

    /** Inserción en cualquier posición, validando todos los plazos del recorrido. */
    private boolean insertarSeguro(Solucion solucion, Pedido pedido) {
        Ruta mejorRuta = null;
        int indiceMejor = -1;
        double mejorMargen = Double.NEGATIVE_INFINITY;
        double mejorLlegada = Double.POSITIVE_INFINITY;
        double mejorDuracion = Double.POSITIVE_INFINITY;
        double menorIncrementoAtencion = Double.POSITIVE_INFINITY;
        List<Ruta> rutas = solucion.getRutas();
        List<Integer> indices = new ArrayList<>();
        for (int i=0;i<rutas.size();i++) indices.add(i);
        indices.sort(java.util.Comparator.comparingDouble(i -> {
            Ruta r = rutas.get(i);
            if (!r.getVehiculo().isDisponible()
                    || r.cargaTotal() + pedido.getCantidadQq() > r.getVehiculo().getCapacidadPaquetes())
                return Double.POSITIVE_INFINITY;
            return contextoOperativo.getAlmacenes().stream()
                    .filter(a -> a.tieneStockDisponible())
                    .mapToDouble(a -> (r.getVehiculo().getPosicionActual().distanciaManhattan(a.getUbicacion())
                            + a.getUbicacion().distanciaManhattan(pedido.getUbicacion()))
                            * contextoOperativo.reglas().distanciaNodoKm() / r.getVehiculo().getVelocidadKmh())
                    .min().orElse(Double.POSITIVE_INFINITY);
        }));
        Set<String> vaciasEquivalentes = new HashSet<>();
        for (int ri : indices) {
            if (!quedaTiempo()) break;
            Ruta actual = rutas.get(ri);
            if (actual.getSecuenciaPedidos().isEmpty()) {
                var v=actual.getVehiculo();
                String clave=v.getCapacidadPaquetes()+":"+v.getVelocidadKmh()+":"+v.isDisponible()+":"
                        +v.getPosicionActual().getX()+","+v.getPosicionActual().getY()+":"
                        +contextoOperativo.disponibleHasta().get(v.getIdVehiculo());
                if (!vaciasEquivalentes.add(clave)) continue;
            }
            if (!actual.getVehiculo().isDisponible()
                    || actual.cargaTotal() + pedido.getCantidadQq() > actual.getVehiculo().getCapacidadPaquetes()) continue;
            double atencionActual = tiempoAtencion(actual);
            var almacenesOrdenados = contextoOperativo.getAlmacenes().stream().sorted(
                    java.util.Comparator.comparingDouble(a -> actual.getVehiculo().getPosicionActual()
                            .distanciaManhattan(a.getUbicacion()) + a.getUbicacion().distanciaManhattan(pedido.getUbicacion()))).toList();
            for (var almacen : almacenesOrdenados) {
                if (!quedaTiempo()) break;
                if (!almacen.tieneStockDisponible()) continue;
                int consumo = actual.cargaTotal() + pedido.getCantidadQq();
                for (int j = 0; j < rutas.size(); j++) {
                    if (j != ri && rutas.get(j).getAlmacenOrigen().getIdAlmacen().equals(almacen.getIdAlmacen()))
                        consumo += rutas.get(j).cargaTotal();
                }
                if (almacen.getCapacidadMaxima() != null
                        && (almacen.getStockActual() == null || consumo > almacen.getStockActual())) continue;
                for (int posicion = 0; posicion <= actual.getSecuenciaPedidos().size() && quedaTiempo(); posicion++) {
                    Ruta candidata = actual.copiar();
                    candidata.setAlmacenOrigen(almacen);
                    candidata.setNodosBloqueados(contextoOperativo.nodosBloqueados());
                    candidata.setCalendarioBloqueos(contextoOperativo.calendarioBloqueos());
                    candidata.setDisponibleHasta(contextoOperativo.disponibleHasta().get(candidata.getVehiculo().getIdVehiculo()));
                    candidata.getSecuenciaPedidos().add(posicion, pedido);
                    candidata.recalcular();
                    Ruta retornoMejor = contextoOperativo.reglas().incluirRetorno() ? null : candidata;
                    double duracionRetorno = Double.POSITIVE_INFINITY;
                    for (var retorno : contextoOperativo.getAlmacenes()) {
                        if (!contextoOperativo.reglas().incluirRetorno()) break;
                        if (!retorno.tieneStockDisponible()) continue;
                        if (retorno.getCapacidadMaxima() != null) {
                            int usado = candidata.getAlmacenOrigen().getIdAlmacen().equals(retorno.getIdAlmacen())
                                    ? candidata.cargaTotal() : 0;
                            for (int j=0;j<rutas.size();j++) if (j!=ri
                                    && rutas.get(j).getAlmacenOrigen().getIdAlmacen().equals(retorno.getIdAlmacen()))
                                usado += rutas.get(j).cargaTotal();
                            if (retorno.getStockActual() == null || retorno.getStockActual() <= usado) continue;
                        }
                        Ruta pruebaRetorno = candidata.copiar();
                        pruebaRetorno.setAlmacenRetorno(retorno); pruebaRetorno.recalcular();
                        if (!pruebaRetorno.isIntransitable() && pruebaRetorno.getTiempoTotalHoras() < duracionRetorno) {
                            retornoMejor = pruebaRetorno; duracionRetorno = pruebaRetorno.getTiempoTotalHoras();
                        }
                    }
                    if (retornoMejor == null) continue;
                    candidata = retornoMejor;
                    if (candidata.isIntransitable() || candidata.isFueraDeTurno()) continue;
                    double margen = Double.POSITIVE_INFINITY;
                    boolean puntual = true;
                    for (int i = 0; i < candidata.getSecuenciaPedidos().size(); i++) {
                        double holgura = horasHastaLimite(candidata.getSecuenciaPedidos().get(i))
                                - candidata.horaLlegadaDe(i);
                        if (!Double.isFinite(holgura) || holgura < 0) { puntual = false; break; }
                        margen = Math.min(margen, holgura);
                    }
                    if (!puntual) continue;
                    double llegada = candidata.horaLlegadaDe(posicion);
                    double duracion = candidata.getTiempoTotalHoras();
                    double incrementoAtencion = tiempoAtencion(candidata) - atencionActual;
                    int comparacion = Double.compare(incrementoAtencion, menorIncrementoAtencion);
                    if (comparacion < 0 || (comparacion == 0 && (llegada < mejorLlegada
                            || (llegada == mejorLlegada && (duracion < mejorDuracion
                            || (duracion == mejorDuracion && margen > mejorMargen)))))) {
                        mejorRuta = candidata; indiceMejor = ri;
                        menorIncrementoAtencion = incrementoAtencion;
                        mejorMargen = margen; mejorLlegada = llegada; mejorDuracion = duracion;
                    }
                }
            }
        }
        if (mejorRuta == null) return false;
        rutas.set(indiceMejor, mejorRuta);
        solucion.getPedidosNoAsignados().removeIf(p -> p.getIdPedido().equals(pedido.getIdPedido()));
        calidades.remove(solucion);
        return true;
    }

    private static double tiempoAtencion(Ruta ruta) {
        double total = 0;
        for (int i = 0; i < ruta.getSecuenciaPedidos().size(); i++)
            total += ruta.tiempoAtencionDe(i);
        return total;
    }

    private Solucion construirSeguro(List<Pedido> orden) {
        Solucion solucion = new Solucion();
        solucion.setRutas(ConstructorVoraz.crearRutasVacias(contextoOperativo));
        solucion.setPedidosNoAsignados(new ArrayList<>(contextoOperativo.getPedidos()));
        Set<Long> insertados = new HashSet<>();
        for (Pedido pedido : orden) {
            if (!quedaTiempo()) break;
            if (insertados.add(pedido.getIdPedido())) insertarSeguro(solucion, pedido);
        }
        return solucion;
    }

    private Solucion construirUrgente() { return construirSeguro(urgentes); }

    /** Cambia la asignación de un pedido para liberar una opción para otro sin ruta. */
    private Solucion recuperarPendientes(Solucion base) {
        Solucion mejor = base;
        for (Pedido pendiente : urgentes) {
            if (!quedaTiempo()) break;
            if (mejor.getPedidosNoAsignados().stream().noneMatch(p -> p.getIdPedido().equals(pendiente.getIdPedido())))
                continue;
            Solucion directa = mejor.copiar();
            if (insertarSeguro(directa, pendiente)) { mejor = directa; continue; }
            List<Pedido> asignados = mejor.getRutas().stream()
                    .flatMap(r -> r.getSecuenciaPedidos().stream()).toList();
            for (Pedido desplazado : asignados) {
                if (!quedaTiempo()) break;
                Solucion candidata = mejor.copiar();
                for (Ruta ruta : candidata.getRutas())
                    ruta.getSecuenciaPedidos().removeIf(p -> p.getIdPedido().equals(desplazado.getIdPedido()));
                candidata.getPedidosNoAsignados().add(desplazado);
                if (insertarSeguro(candidata, pendiente) && insertarSeguro(candidata, desplazado)) {
                    mejor = candidata;
                    break;
                }
            }
        }
        return mejor;
    }

    private Individuo evaluarOperativo(Solucion solucion) {
        double valor = funcionObjetivo.calcular(solucion);
        evaluaciones++;
        calidades.remove(solucion);
        calidad(solucion);
        Individuo individuo = new Individuo(solucion);
        individuo.setFitness(valor);
        if (mejorOperativo == null || comparar(individuo, mejorOperativo) < 0)
            mejorOperativo = individuo.copiar();
        return individuo;
    }

    private List<Pedido> ordenHeredado(Solucion solucion) {
        java.util.Map<Long, Pedido> vigentes = new java.util.LinkedHashMap<>();
        urgentes.forEach(p -> vigentes.put(p.getIdPedido(), p));
        List<Pedido> orden = new ArrayList<>();
        for (Ruta ruta : solucion.getRutas()) for (Pedido previo : ruta.getSecuenciaPedidos()) {
            Pedido actual = vigentes.remove(previo.getIdPedido());
            if (actual != null) orden.add(actual);
        }
        orden.addAll(vigentes.values());
        return orden;
    }

    private Solucion planificarOperacion(ContextoPlanificacion contexto, Solucion anterior) {
        finBusqueda = System.nanoTime() + presupuestoOperativoMs * 1_000_000L;
        prepararContexto(contexto);
        funcionObjetivo.actualizarContexto(contexto);
        evaluaciones = 0; traza.clear(); mejorOperativo = null;
        poblacion = new ArrayList<>();
        // Incluso sin tiempo disponible se conserva un resultado seguro y la lista completa de pendientes.
        Solucion inicial = construirUrgente();
        poblacion.add(evaluarOperativo(inicial));
        if (quedaTiempo()) poblacion.add(evaluarOperativo(recuperarPendientes(inicial.copiar())));
        if (anterior != null && quedaTiempo())
            poblacion.add(evaluarOperativo(construirSeguro(ordenHeredado(anterior))));
        while (poblacion.size() < parametros.tamanioPoblacion && quedaTiempo()) {
            List<Pedido> orden = new ArrayList<>(urgentes);
            if (poblacion.size() == 2) orden.sort(java.util.Comparator.comparing(Pedido::getFechaLimite)
                    .thenComparing(Pedido::getIdPedido));
            else java.util.Collections.shuffle(orden, random);
            poblacion.add(evaluarOperativo(construirSeguro(orden)));
        }
        traza.add(new PuntoConvergencia(0, evaluaciones, mejorOperativo.getFitness()));
        for (int generacion = 1; generacion <= parametros.numGeneraciones && quedaTiempo(); generacion++) {
            poblacion.sort(this::comparar);
            List<Individuo> nueva = new ArrayList<>();
            for (int i = 0; i < Math.min(parametros.elitismo, poblacion.size()); i++)
                nueva.add(poblacion.get(i).copiar());
            while (nueva.size() < parametros.tamanioPoblacion && quedaTiempo()) {
                List<Pedido> primero = ordenHeredado(seleccionTorneo().getCromosoma());
                List<Pedido> segundo = ordenHeredado(seleccionTorneo().getCromosoma());
                List<Pedido> orden = new ArrayList<>();
                Set<Long> ids = new HashSet<>();
                int corte = random.nextDouble() < parametros.probCruzamiento
                        ? random.nextInt(primero.size() + 1) : primero.size();
                for (int i = 0; i < corte; i++) { orden.add(primero.get(i)); ids.add(primero.get(i).getIdPedido()); }
                for (Pedido pedido : segundo) if (ids.add(pedido.getIdPedido())) orden.add(pedido);
                if (orden.size() > 1 && random.nextDouble() < parametros.probMutacion)
                    java.util.Collections.swap(orden, random.nextInt(orden.size()), random.nextInt(orden.size()));
                Solucion hijo = construirSeguro(orden);
                // Un candidato incompleto por agotamiento del tiempo no sustituye al mejor conservado.
                if (quedaTiempo()) hijo = recuperarPendientes(hijo);
                nueva.add(evaluarOperativo(hijo));
            }
            poblacion = nueva;
            calidades.clear();
            traza.add(new PuntoConvergencia(generacion, evaluaciones, mejorOperativo.getFitness()));
        }
        return mejorOperativo.getCromosoma().copiar();
    }

    private Calidad calidad(Solucion solucion) {
        return calidades.computeIfAbsent(solucion, s -> {
            int violaciones = 0, tardios = 0;
            double retraso = 0, margen = Double.POSITIVE_INFINITY;
            Set<Long> vistos = new HashSet<>(), puntuales = new HashSet<>();
            Set<String> usados = new HashSet<>();
            java.util.Map<String, Integer> consumo = new java.util.HashMap<>();
            Set<Long> universo = new HashSet<>();
            contextoOperativo.getPedidos().forEach(p -> universo.add(p.getIdPedido()));
            for (Ruta ruta : s.getRutas()) {
                ruta.setNodosBloqueados(contextoOperativo.nodosBloqueados());
                ruta.setCalendarioBloqueos(contextoOperativo.calendarioBloqueos());
                ruta.setDisponibleHasta(contextoOperativo.disponibleHasta().get(ruta.getVehiculo().getIdVehiculo()));
                ruta.recalcular();
                if (ruta.getSecuenciaPedidos().isEmpty()) continue;
                if (ruta.isIntransitable() || ruta.isFueraDeTurno()) violaciones++;
                String id = ruta.getVehiculo().getIdVehiculo();
                var vehiculo = contextoOperativo.getVehiculos().stream()
                        .filter(v -> v.getIdVehiculo().equals(id)).findFirst().orElse(null);
                if (!usados.add(id)) violaciones++;
                if (vehiculo == null || !vehiculo.isDisponible()
                        || ruta.cargaTotal() > vehiculo.getCapacidadPaquetes()) violaciones++;
                String almacen = ruta.getAlmacenOrigen().getIdAlmacen();
                if (contextoOperativo.getAlmacenes().stream().noneMatch(a -> a.getIdAlmacen().equals(almacen)))
                    violaciones++;
                consumo.merge(almacen, ruta.cargaTotal(), Integer::sum);
                for (int i = 0; i < ruta.getSecuenciaPedidos().size(); i++) {
                    Pedido pedido = ruta.getSecuenciaPedidos().get(i);
                    if (!universo.contains(pedido.getIdPedido()) || !vistos.add(pedido.getIdPedido())) violaciones++;
                    double holgura = horasHastaLimite(pedido) - ruta.horaLlegadaDe(i);
                    margen = Math.min(margen, holgura);
                    if (holgura < 0 || !Double.isFinite(holgura)) {
                        tardios++;
                        retraso += Double.isFinite(holgura) ? -holgura : 1e12;
                    } else puntuales.add(pedido.getIdPedido());
                }
            }
            for (var almacen : contextoOperativo.getAlmacenes()) {
                if (almacen.getCapacidadMaxima() == null) continue;
                Integer stock = almacen.getStockActual();
                if (stock == null || stock < 0 || stock > almacen.getCapacidadMaxima()
                        || consumo.getOrDefault(almacen.getIdAlmacen(), 0) > stock) violaciones++;
            }
            List<Pedido> faltantes = contextoOperativo.getPedidos().stream()
                    .filter(p -> !vistos.contains(p.getIdPedido())).toList();
            s.setPedidosNoAsignados(new ArrayList<>(faltantes));
            s.setValorV(violaciones); s.setValorN(faltantes.size()); s.setValorR(retraso);
            s.setEsFactible(violaciones == 0 && tardios == 0);
            s.setPedidosTardios(tardios); s.setHolguraMinimaHoras(margen);
            s.setCoberturaUrgente(urgentes.stream().map(p -> puntuales.contains(p.getIdPedido())).toList());
            return new Calidad(violaciones, tardios, faltantes.size(),
                    urgentes.stream().map(p -> puntuales.contains(p.getIdPedido())).toList(), retraso, margen);
        });
    }

    private int comparar(Individuo a, Individuo b) {
        if (contextoOperativo == null) return Double.compare(a.getFitness(), b.getFitness());
        Calidad x = calidad(a.getCromosoma()), y = calidad(b.getCromosoma());
        int c = Integer.compare(x.violaciones(), y.violaciones());
        if (c != 0) return c;
        c = Integer.compare(x.tardios(), y.tardios());
        if (c != 0) return c;
        c = Integer.compare(x.noAsignados(), y.noAsignados());
        if (c != 0) return c;
        for (int i = 0; i < x.cobertura().size(); i++) {
            c = Boolean.compare(y.cobertura().get(i), x.cobertura().get(i));
            if (c != 0) return c;
        }
        c = Double.compare(x.retraso(), y.retraso());
        if (c != 0) return c;
        c = Double.compare(a.getCromosoma().getValorT(), b.getCromosoma().getValorT());
        return c != 0 ? c : Double.compare(y.margen(), x.margen());
    }
    public List<PuntoConvergencia> getConvergencia(){ return List.copyOf(traza); }

    public AlgoritmoGenetico(ParametrosAlgoritmo parametros, FuncionObjetivo funcionObjetivo) {
        this(parametros, funcionObjetivo, 1000L);
    }

    /** Presupuesto cooperativo exclusivo del modo operativo; el experimento conserva sus iteraciones. */
    public AlgoritmoGenetico(ParametrosAlgoritmo parametros, FuncionObjetivo funcionObjetivo,
                             long presupuestoOperativoMs) {
        if (presupuestoOperativoMs < 1 || presupuestoOperativoMs > 60000)
            throw new IllegalArgumentException("El presupuesto operativo debe estar entre 1 y 60000 ms");
        this.presupuestoOperativoMs = presupuestoOperativoMs;
        this.parametros = parametros;
        this.funcionObjetivo = funcionObjetivo;
        this.random = (parametros.semillaAleatoria != null)
                ? new Random(parametros.semillaAleatoria)
                : new Random();
    }

    @Override
    public Solucion planificar(ContextoPlanificacion contexto) {
        if (contexto.respetarPosicionVehiculo()) return planificarOperacion(contexto, null);
        prepararContexto(contexto);
        poblacion = new ArrayList<>();
        for (int i = 0; i < parametros.tamanioPoblacion; i++) {
            boolean sesgoVoraz = (i < Math.ceil(parametros.tamanioPoblacion * parametros.proporcionVoraz));
            Solucion base = contextoOperativo != null && (i == 0 || sesgoVoraz)
                    ? construirUrgente() : ConstructorVoraz.construir(contexto, random, sesgoVoraz);
            poblacion.add(new Individuo(base));
        }

        return ejecutarEvolucion();
    }

    @Override
    public Solucion replanificar(Solucion solucionActual, ContextoPlanificacion contextoActualizado) {
        if (contextoActualizado.respetarPosicionVehiculo()) return planificarOperacion(contextoActualizado, solucionActual);
        prepararContexto(contextoActualizado);
        // Semilla la población con la solución actual más variantes perturbadas.
        poblacion = new ArrayList<>();
        Solucion base = repararSolucion(solucionActual, contextoActualizado);
        poblacion.add(new Individuo(base));

        for (int i = 1; i < parametros.tamanioPoblacion; i++) {
            Solucion variante = contextoOperativo != null && i == 1 ? construirUrgente() : base.copiar();
            if (contextoOperativo == null || i != 1) mutar(variante);
            poblacion.add(new Individuo(variante));
        }

        return ejecutarEvolucion();
    }

    private Solucion ejecutarEvolucion() {
        evaluaciones=0; traza.clear();
        evaluarPoblacion();
        registrar(0);

        for (int gen = 0; gen < parametros.numGeneraciones; gen++) {
            poblacion.sort(this::comparar);

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

        poblacion.sort(this::comparar);
        return poblacion.get(0).getCromosoma();
    }

    // Operadores genéticos
    private void evaluarPoblacion() {
        calidades.clear();
        for (Individuo ind : poblacion) {
            evaluaciones++;
            double valor = funcionObjetivo.calcular(ind.getCromosoma());
            ind.setFitness(valor);
            if (contextoOperativo != null) calidad(ind.getCromosoma());
        }
    }

    private Individuo seleccionTorneo() {
        Individuo mejor = poblacion.get(random.nextInt(poblacion.size()));
        for (int i = 1; i < parametros.tamanioTorneo; i++) {
            Individuo candidato = poblacion.get(random.nextInt(poblacion.size()));
            if (comparar(candidato, mejor) < 0) {
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

        List<Pedido> faltantesOrdenados = contextoOperativo == null
                ? ConstructorVoraz.ordenarPedidos(faltantes, random, true)
                : faltantes.stream().sorted(java.util.Comparator.comparingInt(
                        p -> ordenUrgente.getOrDefault(p.getIdPedido(), Integer.MAX_VALUE))).toList();

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
        double mejor = contextoOperativo == null
                ? poblacion.stream().mapToDouble(Individuo::getFitness).min().orElseThrow()
                : poblacion.stream().min(this::comparar).orElseThrow().getFitness();
        traza.add(new PuntoConvergencia(iteracion,evaluaciones,mejor));
    }
}
