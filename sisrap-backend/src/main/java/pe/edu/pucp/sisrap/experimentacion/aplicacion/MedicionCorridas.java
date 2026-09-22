package pe.edu.pucp.sisrap.experimentacion.aplicacion;

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

/** Métricas de una corrida y estadística descriptiva. Compartido por la API y por el main de experimentación. */
public final class MedicionCorridas {
    private MedicionCorridas() {}

    /** Compatibilidad con el flujo API "ESTATICO_UN_VIAJE_SIN_INCIDENCIAS" (EjecutarExperimento), que no
     * conoce escenario operativo ni perfil de presión: quedan null. */
    public static Corrida medir(String algoritmo, int n, long semilla, double ms, Solucion s,
                                ContextoPlanificacion c, IAlgoritmoPlanificacion motor) {
        return medir(algoritmo, n, semilla, ms, s, c, motor, null, null, null, null, null, null);
    }

    /** Corrida sin incidencia registrada (escenario distinto de OPERACION_DIARIA, o perfil NORMAL). */
    public static Corrida medir(String algoritmo, int n, long semilla, double ms, Solucion s,
                                ContextoPlanificacion c, IAlgoritmoPlanificacion motor,
                                String escenarioOperativo, String perfilPresion) {
        return medir(algoritmo, n, semilla, ms, s, c, motor, escenarioOperativo, perfilPresion, null, null, null, null);
    }

    /** Corrida con la incidencia y su replanificación ya medidas (ver {@link SimuladorIncidencias}). */
    public static Corrida medir(String algoritmo, int n, long semilla, double ms, Solucion s,
                                ContextoPlanificacion c, IAlgoritmoPlanificacion motor,
                                String escenarioOperativo, String perfilPresion,
                                Integer pedidosAfectadosIncidencia, Integer vehiculosEnAveriaIncidente,
                                Double tiempoReplanificacionMs, Boolean replanificacionExitosa) {
        int aTiempo = 0, prioritariosATiempo = 0;
        int prioritarios = (int) c.getPedidos().stream().filter(p -> p.getPrioridad().esPriorizado()).count();
        double distancia = 0, carga = 0;
        List<Asignacion> rutas = new ArrayList<>();
        for (var ruta : s.getRutas()) {
            distancia += ruta.getDistanciaTotalKm();
            carga += ruta.cargaTotal();
            for (int i = 0; i < ruta.getSecuenciaPedidos().size(); i++) {
                if (ruta.retrasoDe(i) == 0) {
                    aTiempo++;
                    if (ruta.getSecuenciaPedidos().get(i).getPrioridad().esPriorizado()) prioritariosATiempo++;
                }
            }
            if (!ruta.getSecuenciaPedidos().isEmpty())
                rutas.add(new Asignacion(ruta.getVehiculo().getIdVehiculo(), ruta.getAlmacenOrigen().getIdAlmacen(),
                        ruta.getSecuenciaPedidos().stream().map(p -> p.getIdPedido()).toList()));
        }
        int capacidad = c.getVehiculos().stream().filter(v -> v.isDisponible()).mapToInt(v -> v.getCapacidadPaquetes()).sum();
        return new Corrida(algoritmo, n, semilla, ms, s.getValorFuncionObjetivo(), s.getValorT(), s.getValorR(),
                s.getValorN(), s.getValorV(), s.isEsFactible(), n == 0 ? 0.0 : 100.0 * aTiempo / n,
                prioritarios == 0 ? null : 100.0 * prioritariosATiempo / prioritarios,
                s.getCostoTransporte(), distancia, capacidad == 0 ? 0 : 100 * carga / capacidad,
                motor instanceof RecocidoSimulado sa ? sa.getTemperaturaUsada() : null, motor.getConvergencia(),
                List.copyOf(rutas), s.getPedidosNoAsignados().stream().map(p -> p.getIdPedido()).toList(),
                escenarioOperativo, perfilPresion, pedidosAfectadosIncidencia, vehiculosEnAveriaIncidente,
                tiempoReplanificacionMs, replanificacionExitosa);
    }

    /** Desviación muestral (n-1); null con menos de dos valores. */
    public static Estadistica estadistica(double[] valores, boolean maximizar) {
        if (valores.length == 0) throw new IllegalArgumentException("Sin valores para resumir");
        double[] v = valores.clone();
        Arrays.sort(v);
        double media = Arrays.stream(v).average().orElseThrow(), suma = 0;
        for (double x : v) suma += (x - media) * (x - media);
        return new Estadistica(media, (v[(v.length - 1) / 2] + v[v.length / 2]) / 2,
                v.length < 2 ? null : Math.sqrt(suma / (v.length - 1)),
                maximizar ? v[v.length - 1] : v[0], maximizar ? v[0] : v[v.length - 1]);
    }
}