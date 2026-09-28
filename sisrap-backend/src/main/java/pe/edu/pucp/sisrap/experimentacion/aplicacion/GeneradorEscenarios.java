package pe.edu.pucp.sisrap.experimentacion.aplicacion;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

import pe.edu.pucp.sisrap.experimentacion.dominio.BaseOperativa;
import pe.edu.pucp.sisrap.experimentacion.dominio.Bloqueo;
import pe.edu.pucp.sisrap.experimentacion.dominio.DatosArchivos;
import pe.edu.pucp.sisrap.experimentacion.dominio.Escenario;
import pe.edu.pucp.sisrap.experimentacion.dominio.EscenarioOperativo;
import pe.edu.pucp.sisrap.experimentacion.dominio.PerfilPresion;
import pe.edu.pucp.sisrap.experimentacion.dominio.PlanExperimento;
import pe.edu.pucp.sisrap.flota.dominio.Vehiculo;
import pe.edu.pucp.sisrap.pedido.dominio.Pedido;

/**
 * Genera N01, N02, N03, ... como realizaciones sintéticas reproducibles.
 *
 * Presión logística utilizada:
 * - demanda: P50/P75/P90 de pedidos diarios históricos;
 * - bloqueos: P50/P75/P90 de bloqueos diarios históricos;
 * - mantenimiento: calendario preventivo entregado, repetido bimestralmente;
 * - averías: deshabilitadas por ahora.
 */
public final class GeneradorEscenarios {
    private static final long SALTO_ESCENARIO = 10_000_019L;
    private static final long SALTO_INSTANCIA = 100_003L;
    private static final long SALTO_BLOQUEOS = 700_000_003L;
    private static final long SALTO_PERFIL_BLOQUEOS = 1_000_003L;
    private static final long ID_BASE_SINTETICO = 1_000_000_000_000L;

    private GeneradorEscenarios() {}

    public static List<Escenario> generar(PlanExperimento plan,
                                          BaseOperativa base,
                                          DatosArchivos datos,
                                          ModeloDemandaHistorica.Resumen demanda,
                                          ModeloBloqueosHistoricos.Resumen bloqueos,
                                          List<String> advertencias) {
        validarMantenimiento(datos, base, advertencias);

        advertencias.add(String.format(
                "Demanda histórica calibrada con %d días (%s a %s): P50=%d, P75=%d, P90=%d; incremento diario de colapso=%d.",
                demanda.dias(), demanda.desde(), demanda.hasta(),
                demanda.p50(), demanda.p75(), demanda.p90(), demanda.incrementoColapso()));

        advertencias.add(String.format(
                "Bloqueos históricos calibrados con %d días (%s a %s): P50=%d, P75=%d, P90=%d bloqueos/día; %d plantillas geométricas disponibles.",
                bloqueos.dias(), bloqueos.desde(), bloqueos.hasta(),
                bloqueos.p50(), bloqueos.p75(), bloqueos.p90(), bloqueos.plantillasDisponibles()));

        List<Escenario> escenarios = new ArrayList<>();
        for (EscenarioOperativo eo : plan.escenariosOperativos()) {
            for (PerfilPresion perfil : plan.perfiles()) {
                for (int instancia = 1; instancia <= plan.instancias(); instancia++) {
                    escenarios.add(switch (eo) {
                        case OPERACION_DIARIA -> construirOperacionDiaria(
                                plan, base, datos, demanda, bloqueos, perfil, instancia);
                        case SIMULACION_CINCO_DIAS -> construirCincoDias(
                                plan, base, datos, demanda, bloqueos, perfil, instancia);
                        case COLAPSO_LOGISTICO -> construirColapso(
                                plan, base, datos, demanda, bloqueos, perfil, instancia);
                    });
                }
            }
        }
        return List.copyOf(escenarios);
    }

    private static Escenario construirOperacionDiaria(PlanExperimento plan,
                                                       BaseOperativa base,
                                                       DatosArchivos datos,
                                                       ModeloDemandaHistorica.Resumen demanda,
                                                       ModeloBloqueosHistoricos.Resumen bloqueos,
                                                       PerfilPresion perfil,
                                                       int instancia) {
        LocalDate dia = demanda.hasta().plusDays(1);

        Random randomDemanda = new Random(
                semillaDemanda(plan, EscenarioOperativo.OPERACION_DIARIA, instancia));
        Random randomBloqueos = new Random(
                semillaBloqueos(plan, EscenarioOperativo.OPERACION_DIARIA, perfil, instancia));

        long idInicial = idInicial(
                EscenarioOperativo.OPERACION_DIARIA,
                perfil,
                instancia,
                0);

        List<Pedido> pedidos = ModeloDemandaHistorica.generarDia(
                demanda,
                perfil,
                dia,
                randomDemanda,
                idInicial);

        List<Bloqueo> bloqueosProgramados = ModeloBloqueosHistoricos.generarDia(
                bloqueos,
                perfil,
                dia,
                randomBloqueos,
                base.anchoKm(),
                base.altoKm());

        var instante = dia.atStartOfDay();

        Set<String> mantenimiento = mantenimientoEn(plan, datos, dia);
        Set<String> bajaPerfil = bajasPorPerfil(base, perfil);
        List<Vehiculo> flota = copiarFlota(base, mantenimiento, bajaPerfil);

        return new Escenario(
                String.format("%s-%s-N%02d", EscenarioOperativo.OPERACION_DIARIA, perfil, instancia),
                EscenarioOperativo.OPERACION_DIARIA,
                perfil,
                pedidos.size(),
                instancia,
                instante,
                pedidos,
                flota,
                mantenimiento.stream().sorted().toList(),
                bajaPerfil.stream().sorted().toList(),
                bloqueosProgramados);
    }

    private static Escenario construirCincoDias(PlanExperimento plan,
                                                 BaseOperativa base,
                                                 DatosArchivos datos,
                                                 ModeloDemandaHistorica.Resumen demanda,
                                                 ModeloBloqueosHistoricos.Resumen bloqueos,
                                                 PerfilPresion perfil,
                                                 int instancia) {
        LocalDate inicio = demanda.hasta().plusDays(1);

        Random randomDemanda = new Random(
                semillaDemanda(plan, EscenarioOperativo.SIMULACION_CINCO_DIAS, instancia));
        Random randomBloqueos = new Random(
                semillaBloqueos(plan, EscenarioOperativo.SIMULACION_CINCO_DIAS, perfil, instancia));

        List<Pedido> pedidos = new ArrayList<>();
        List<Bloqueo> bloqueosProgramados = new ArrayList<>();

        for (int dia = 0; dia < 5; dia++) {
            LocalDate fecha = inicio.plusDays(dia);
            long idInicial = idInicial(
                    EscenarioOperativo.SIMULACION_CINCO_DIAS,
                    perfil,
                    instancia,
                    dia);

            pedidos.addAll(ModeloDemandaHistorica.generarDia(
                    demanda,
                    perfil,
                    fecha,
                    randomDemanda,
                    idInicial));

            bloqueosProgramados.addAll(ModeloBloqueosHistoricos.generarDia(
                    bloqueos,
                    perfil,
                    fecha,
                    randomBloqueos,
                    base.anchoKm(),
                    base.altoKm()));
        }

        pedidos.sort(java.util.Comparator
                .comparing(Pedido::getFechaLlegada)
                .thenComparing(Pedido::getIdPedido));
        bloqueosProgramados.sort(java.util.Comparator.comparing(Bloqueo::inicio));

        var instante = inicio.atStartOfDay();
        LocalDate diaPlanificacion = inicio;

        Set<String> mantenimiento = mantenimientoEn(plan, datos, diaPlanificacion);
        Set<String> bajaPerfil = bajasPorPerfil(base, perfil);
        List<Vehiculo> flota = copiarFlota(base, mantenimiento, bajaPerfil);

        return new Escenario(
                String.format("%s-%s-N%02d", EscenarioOperativo.SIMULACION_CINCO_DIAS, perfil, instancia),
                EscenarioOperativo.SIMULACION_CINCO_DIAS,
                perfil,
                pedidos.size(),
                instancia,
                instante,
                List.copyOf(pedidos),
                flota,
                mantenimiento.stream().sorted().toList(),
                bajaPerfil.stream().sorted().toList(),
                List.copyOf(bloqueosProgramados));
    }

    /**
     * Para COLAPSO_LOGISTICO se exporta la primera jornada sintética.
     * SimuladorColapso vuelve a generarla con las mismas semillas y continúa día a día.
     */
    private static Escenario construirColapso(PlanExperimento plan,
                                              BaseOperativa base,
                                              DatosArchivos datos,
                                              ModeloDemandaHistorica.Resumen demanda,
                                              ModeloBloqueosHistoricos.Resumen bloqueos,
                                              PerfilPresion perfil,
                                              int instancia) {
        LocalDate inicio = demanda.hasta().plusDays(1);

        Random randomDemanda = new Random(
                semillaDemanda(plan, EscenarioOperativo.COLAPSO_LOGISTICO, instancia));
        Random randomBloqueos = new Random(
                semillaBloqueos(plan, EscenarioOperativo.COLAPSO_LOGISTICO, perfil, instancia));

        long idInicial = idInicial(
                EscenarioOperativo.COLAPSO_LOGISTICO,
                perfil,
                instancia,
                0);

        List<Pedido> primeraJornada = ModeloDemandaHistorica.generarDia(
                demanda,
                plan.lambdaPedidos(perfil),
                inicio,
                randomDemanda,
                idInicial);

        List<Bloqueo> bloqueosProgramados = ModeloBloqueosHistoricos.generarDia(
                bloqueos,
                perfil,
                inicio,
                randomBloqueos,
                base.anchoKm(),
                base.altoKm());

        var instante = inicio.atStartOfDay();
        Set<String> mantenimiento = mantenimientoEn(plan, datos, inicio);
        Set<String> bajaPerfil = bajasPorPerfil(base, perfil);
        List<Vehiculo> flota = copiarFlota(base, mantenimiento, bajaPerfil);

        return new Escenario(
                String.format("%s-%s-N%02d", EscenarioOperativo.COLAPSO_LOGISTICO, perfil, instancia),
                EscenarioOperativo.COLAPSO_LOGISTICO,
                perfil,
                primeraJornada.size(),
                instancia,
                instante,
                primeraJornada,
                flota,
                mantenimiento.stream().sorted().toList(),
                bajaPerfil.stream().sorted().toList(),
                bloqueosProgramados);
    }

    /**
     * La semilla de demanda depende de escenario e instancia, no de algoritmo ni repetición.
     * GA y SA reciben exactamente la misma demanda de una instancia.
     */
    static long semillaDemanda(PlanExperimento plan,
                               EscenarioOperativo escenario,
                               int instancia) {
        long desplazamientoEscenario = Math.multiplyExact(
                (long) escenario.ordinal(),
                SALTO_ESCENARIO);
        long desplazamientoInstancia = Math.multiplyExact(
                (long) instancia,
                SALTO_INSTANCIA);
        return Math.addExact(
                plan.semillaBase(),
                Math.addExact(desplazamientoEscenario, desplazamientoInstancia));
    }

    /** Semilla independiente para no correlacionar la demanda con los bloqueos. */
    static long semillaBloqueos(PlanExperimento plan,
                                EscenarioOperativo escenario,
                                PerfilPresion perfil,
                                int instancia) {
        long base = semillaDemanda(plan, escenario, instancia);
        long desplazamientoPerfil = Math.multiplyExact(
                (long) perfil.ordinal(),
                SALTO_PERFIL_BLOQUEOS);
        return Math.addExact(
                base,
                Math.addExact(SALTO_BLOQUEOS, desplazamientoPerfil));
    }

    static long idInicial(EscenarioOperativo escenario,
                          PerfilPresion perfil,
                          int instancia,
                          int dia) {
        return ID_BASE_SINTETICO
                + escenario.ordinal() * 100_000_000_000L
                + perfil.ordinal() * 10_000_000_000L
                + instancia * 100_000_000L
                + dia * 100_000L;
    }

    /**
     * Aplica el mantenimiento preventivo como calendario bimestral.
     * El archivo entregado 09.10 sirve como plantilla del ciclo de dos meses;
     * la misma asignación se repite cada dos meses hacia adelante o atrás.
     *
     * El lector ya extiende un día adicional a los autos (TA), por lo que esa
     * duración también se replica automáticamente.
     */
    static Set<String> mantenimientoEn(PlanExperimento plan,
                                       DatosArchivos datos,
                                       LocalDate dia) {
        if (!plan.aplicarMantenimiento()) return Set.of();

        Set<String> resultado = new HashSet<>();
        YearMonth mesConsulta = YearMonth.from(dia);

        datos.mantenimiento().forEach((fechaPlantilla, ids) -> {
            if (fechaPlantilla.getDayOfMonth() != dia.getDayOfMonth()) return;

            long diferenciaMeses = ChronoUnit.MONTHS.between(
                    YearMonth.from(fechaPlantilla),
                    mesConsulta);

            if (Math.floorMod(diferenciaMeses, 2L) == 0L) {
                resultado.addAll(ids);
            }
        });

        return Set.copyOf(resultado);
    }

    /**
     * No se reducen vehículos artificialmente por perfil. La única indisponibilidad
     * automática de esta versión proviene del mantenimiento preventivo.
     */
    static Set<String> bajasPorPerfil(BaseOperativa base, PerfilPresion perfil) {
        return Set.of();
    }

    static List<Vehiculo> copiarFlota(BaseOperativa base,
                                      Set<String> enMantenimiento,
                                      Set<String> enBajaPorPerfil) {
        return base.vehiculos().stream()
                .map(v -> copiar(v, enMantenimiento, enBajaPorPerfil))
                .toList();
    }

    private static Vehiculo copiar(Vehiculo v,
                                   Set<String> enMantenimiento,
                                   Set<String> enBajaPorPerfil) {
        var copia = new Vehiculo(
                v.getIdVehiculo(),
                v.getCapacidadPaquetes(),
                v.getVelocidadKmh(),
                v.getCostoPorKm(),
                v.getPosicionActual());

        copia.setDisponible(
                v.isDisponible()
                        && !enMantenimiento.contains(v.getIdVehiculo())
                        && !enBajaPorPerfil.contains(v.getIdVehiculo()));
        return copia;
    }

    private static void validarMantenimiento(DatosArchivos datos,
                                             BaseOperativa base,
                                             List<String> advertencias) {
        Set<String> conocidos = base.vehiculos().stream()
                .map(Vehiculo::getIdVehiculo)
                .collect(Collectors.toSet());

        Set<String> desconocidos = new HashSet<>();
        datos.mantenimiento().values().forEach(ids -> ids.stream()
                .filter(id -> !conocidos.contains(id))
                .forEach(desconocidos::add));

        if (!desconocidos.isEmpty()) {
            advertencias.add(
                    "Mantenimiento menciona vehículos que no existen en la BD: "
                            + desconocidos.stream().sorted().toList());
        }
    }
}