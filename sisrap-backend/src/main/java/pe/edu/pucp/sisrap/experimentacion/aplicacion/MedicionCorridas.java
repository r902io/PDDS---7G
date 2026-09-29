package pe.edu.pucp.sisrap.experimentacion.aplicacion;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import pe.edu.pucp.sisrap.experimentacion.dominio.InformeExperimento.Asignacion;
import pe.edu.pucp.sisrap.experimentacion.dominio.InformeExperimento.Corrida;
import pe.edu.pucp.sisrap.experimentacion.dominio.InformeExperimento.Estadistica;
import pe.edu.pucp.sisrap.planificador.dominio.algoritmo.IAlgoritmoPlanificacion;
import pe.edu.pucp.sisrap.planificador.dominio.algoritmo.RecocidoSimulado;
import pe.edu.pucp.sisrap.planificador.dominio.modelo.ContextoPlanificacion;
import pe.edu.pucp.sisrap.planificador.dominio.modelo.Solucion;

/** Métricas de una corrida y estadística descriptiva. */
public final class MedicionCorridas {
    private MedicionCorridas() {}

    /** Compatibilidad con el flujo API estático. */
    public static Corrida medir(String algoritmo,
                                int n,
                                long semilla,
                                double ms,
                                Solucion s,
                                ContextoPlanificacion c,
                                IAlgoritmoPlanificacion motor) {
        return medir(algoritmo, n, semilla, ms, s, c, motor,
                null, null,
                null, null, null, null,
                null, null, null, null,
                null, null, null, null, null);
    }

    /** Corrida sin incidencia ni colapso. */
    public static Corrida medir(String algoritmo,
                                int n,
                                long semilla,
                                double ms,
                                Solucion s,
                                ContextoPlanificacion c,
                                IAlgoritmoPlanificacion motor,
                                String escenarioOperativo,
                                String perfilPresion) {
        return medir(algoritmo, n, semilla, ms, s, c, motor,
                escenarioOperativo, perfilPresion,
                null, null, null, null,
                null, null, null, null,
                null, null, null, null, null);
    }

    /** Corrida con incidencia/replanificación. */
    public static Corrida medir(String algoritmo,
                                int n,
                                long semilla,
                                double ms,
                                Solucion s,
                                ContextoPlanificacion c,
                                IAlgoritmoPlanificacion motor,
                                String escenarioOperativo,
                                String perfilPresion,
                                Integer pedidosAfectadosIncidencia,
                                Integer vehiculosEnAveriaIncidente,
                                Double tiempoReplanificacionMs,
                                Boolean replanificacionExitosa) {
        return medir(algoritmo, n, semilla, ms, s, c, motor,
                escenarioOperativo, perfilPresion,
                pedidosAfectadosIncidencia, vehiculosEnAveriaIncidente,
                tiempoReplanificacionMs, replanificacionExitosa,
                null, null, null, null,
                null, null, null, null, null);
    }

    /** Corrida completa, incluyendo la métrica exclusiva del escenario de colapso. */
    public static Corrida medir(String algoritmo,
                                int n,
                                long semilla,
                                double ms,
                                Solucion s,
                                ContextoPlanificacion c,
                                IAlgoritmoPlanificacion motor,
                                String escenarioOperativo,
                                String perfilPresion,
                                Integer pedidosAfectadosIncidencia,
                                Integer vehiculosEnAveriaIncidente,
                                Double tiempoReplanificacionMs,
                                Boolean replanificacionExitosa,
                                Integer tamanioAlColapso,
                                Boolean colapsoAlcanzado,
                                Double tiempoColapsoHoras,
                                LocalDateTime instanteColapso,
                                Long pedidoCausaColapso,
                                String prioridadCausaColapso,
                                LocalDateTime deadlineCausaColapso,
                                Integer noAsignadosAlColapso,
                                Double lambdaAlColapso) {
        int aTiempo = 0;
        int prioritariosATiempo = 0;
        int prioritarios = (int) c.getPedidos().stream()
                .filter(p -> p.getPrioridad().esPriorizado())
                .count();

        double distancia = 0;
        double carga = 0;
        List<Asignacion> rutas = new ArrayList<>();

        for (var ruta : s.getRutas()) {
            distancia += ruta.getDistanciaTotalKm();
            carga += ruta.cargaTotal();

            for (int i = 0; i < ruta.getSecuenciaPedidos().size(); i++) {
                if (ruta.retrasoDe(i) == 0) {
                    aTiempo++;
                    if (ruta.getSecuenciaPedidos().get(i).getPrioridad().esPriorizado()) {
                        prioritariosATiempo++;
                    }
                }
            }

            if (!ruta.getSecuenciaPedidos().isEmpty()) {
                rutas.add(new Asignacion(
                        ruta.getVehiculo().getIdVehiculo(),
                        ruta.getAlmacenOrigen().getIdAlmacen(),
                        ruta.getSecuenciaPedidos().stream().map(p -> p.getIdPedido()).toList()));
            }
        }

        int capacidad = c.getVehiculos().stream()
                .filter(v -> v.isDisponible())
                .mapToInt(v -> v.getCapacidadPaquetes())
                .sum();

        return new Corrida(
                algoritmo,
                n,
                semilla,
                ms,
                s.getValorFuncionObjetivo(),
                s.getValorT(),
                s.getValorR(),
                s.getValorN(),
                s.getValorV(),
                s.isEsFactible(),
                n == 0 ? 0.0 : 100.0 * aTiempo / n,
                prioritarios == 0 ? null : 100.0 * prioritariosATiempo / prioritarios,
                s.getCostoTransporte(),
                distancia,
                capacidad == 0 ? 0.0 : 100.0 * carga / capacidad,
                motor instanceof RecocidoSimulado sa ? sa.getTemperaturaUsada() : null,
                motor.getConvergencia(),
                List.copyOf(rutas),
                s.getPedidosNoAsignados().stream().map(p -> p.getIdPedido()).toList(),
                escenarioOperativo,
                perfilPresion,
                pedidosAfectadosIncidencia,
                vehiculosEnAveriaIncidente,
                tiempoReplanificacionMs,
                replanificacionExitosa,
                tamanioAlColapso,
                colapsoAlcanzado,
                tiempoColapsoHoras,
                instanteColapso,
                pedidoCausaColapso,
                prioridadCausaColapso,
                deadlineCausaColapso,
                noAsignadosAlColapso,
                lambdaAlColapso);
    }

    /**
     * Agrega las cinco planificaciones diarias en una única corrida experimental.
     *
     * F, T, R, N, V, costo y distancia son aditivos entre jornadas independientes.
     * El SLA se calcula sobre el total de pedidos de los cinco días y la utilización
     * se calcula como carga total / capacidad simultánea disponible acumulada por día.
     *
     * La convergencia queda vacía porque no existe una única trayectoria de búsqueda:
     * son cinco optimizaciones consecutivas. Por ello, la comparación a presupuesto
     * igual basada en una traza única no se aplica a este escenario.
     */
    public static Corrida medirCincoDias(
            String algoritmo,
            int tamanioTotal,
            long semilla,
            SimuladorCincoDias.Resultado resultado,
            String escenarioOperativo,
            String perfilPresion) {

        int entregadosATiempo = 0;
        int prioritarios = 0;
        int prioritariosATiempo = 0;
        int noAsignados = 0;
        int capacidadAcumulada = 0;
        int cargaAcumulada = 0;

        double objetivo = 0.0;
        double t = 0.0;
        double r = 0.0;
        double v = 0.0;
        double costo = 0.0;
        double distancia = 0.0;
        boolean factible = true;

        List<Asignacion> rutas = new ArrayList<>();
        List<Long> idsNoAsignados = new ArrayList<>();
        List<Double> temperaturas = new ArrayList<>();

        for (SimuladorCincoDias.Jornada jornada : resultado.jornadas()) {
            Solucion solucion = jornada.solucion();
            ContextoPlanificacion contexto = jornada.contexto();

            objetivo += solucion.getValorFuncionObjetivo();
            t += solucion.getValorT();
            r += solucion.getValorR();
            noAsignados += solucion.getValorN();
            v += solucion.getValorV();
            costo += solucion.getCostoTransporte();
            factible &= solucion.isEsFactible();

            capacidadAcumulada += contexto.getVehiculos().stream()
                    .filter(x -> x.isDisponible())
                    .mapToInt(x -> x.getCapacidadPaquetes())
                    .sum();

            prioritarios += (int) contexto.getPedidos().stream()
                    .filter(p -> p.getPrioridad().esPriorizado())
                    .count();

            for (var ruta : solucion.getRutas()) {
                distancia += ruta.getDistanciaTotalKm();
                cargaAcumulada += ruta.cargaTotal();

                for (int i = 0; i < ruta.getSecuenciaPedidos().size(); i++) {
                    var pedido = ruta.getSecuenciaPedidos().get(i);
                    if (ruta.retrasoDe(i) == 0) {
                        entregadosATiempo++;
                        if (pedido.getPrioridad().esPriorizado()) {
                            prioritariosATiempo++;
                        }
                    }
                }

                if (!ruta.getSecuenciaPedidos().isEmpty()) {
                    rutas.add(new Asignacion(
                            ruta.getVehiculo().getIdVehiculo(),
                            ruta.getAlmacenOrigen().getIdAlmacen(),
                            ruta.getSecuenciaPedidos().stream()
                                    .map(p -> p.getIdPedido())
                                    .toList()));
                }
            }

            idsNoAsignados.addAll(
                    solucion.getPedidosNoAsignados().stream()
                            .map(p -> p.getIdPedido())
                            .toList());

            if (jornada.motor() instanceof RecocidoSimulado sa) {
                temperaturas.add(sa.getTemperaturaUsada());
            }
        }

        Double temperaturaPromedio = temperaturas.isEmpty()
                ? null
                : temperaturas.stream().mapToDouble(Double::doubleValue).average().orElseThrow();

        return new Corrida(
                algoritmo,
                tamanioTotal,
                semilla,
                resultado.tiempoEjecucionTotalMs(),
                objetivo,
                t,
                r,
                noAsignados,
                v,
                factible,
                tamanioTotal == 0 ? 0.0 : 100.0 * entregadosATiempo / tamanioTotal,
                prioritarios == 0 ? null : 100.0 * prioritariosATiempo / prioritarios,
                costo,
                distancia,
                capacidadAcumulada == 0 ? 0.0 : 100.0 * cargaAcumulada / capacidadAcumulada,
                temperaturaPromedio,
                List.of(),
                List.copyOf(rutas),
                List.copyOf(idsNoAsignados),
                escenarioOperativo,
                perfilPresion,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null);
    }

    /** Desviación muestral (n-1); null con menos de dos valores. */
    public static Estadistica estadistica(double[] valores, boolean maximizar) {
        double[] limpios = Arrays.stream(valores).filter(Double::isFinite).toArray();
        if (limpios.length == 0) throw new IllegalArgumentException("Sin valores para resumir");

        double[] v = limpios.clone();
        Arrays.sort(v);
        double media = Arrays.stream(v).average().orElseThrow();
        double suma = 0;
        for (double x : v) suma += (x - media) * (x - media);

        return new Estadistica(
                media,
                (v[(v.length - 1) / 2] + v[v.length / 2]) / 2,
                v.length < 2 ? null : Math.sqrt(suma / (v.length - 1)),
                maximizar ? v[v.length - 1] : v[0],
                maximizar ? v[0] : v[v.length - 1]);
    }
}