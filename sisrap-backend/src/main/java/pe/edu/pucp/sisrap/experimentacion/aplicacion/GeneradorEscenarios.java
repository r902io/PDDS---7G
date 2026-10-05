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
 * Diseño pareado mejorado:
 * - para una misma combinación escenario/instancia, NORMAL, ALTA y CRITICA comparten
 *   la misma realización base;
 * - demanda NORMAL ⊆ ALTA ⊆ CRITICA;
 * - bloqueos NORMAL ⊆ ALTA ⊆ CRITICA en operación diaria y cinco días;
 * - en COLAPSO los tres perfiles comienzan con la misma demanda P50 y los mismos
 *   bloqueos P50; solo cambia la tasa de crecimiento de demanda dentro de la corrida;
 * - mantenimiento: calendario preventivo entregado, repetido bimestralmente;
 * - averías: deshabilitadas por ahora.
 */
public final class GeneradorEscenarios {
    private static final long SALTO_ESCENARIO = 10_000_019L;
    private static final long SALTO_INSTANCIA = 100_003L;
    private static final long SALTO_BLOQUEOS = 700_000_003L;
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
                "Demanda histórica calibrada con %d días (%s a %s): P50=%d, P75=%d, P90=%d. Operación diaria y cinco días usan instancias anidadas NORMAL⊆ALTA⊆CRITICA.",
                demanda.dias(), demanda.desde(), demanda.hasta(),
                demanda.p50(), demanda.p75(), demanda.p90()));

        advertencias.add(String.format(
                "COLAPSO_LOGISTICO parte de P50=%d para los tres perfiles y usa crecimiento diario compuesto NORMAL=%.2f%%, ALTA=%.2f%%, CRITICA=%.2f%%.",
                plan.demandaP50Diaria(),
                100.0 * plan.crecimientoColapso(PerfilPresion.NORMAL),
                100.0 * plan.crecimientoColapso(PerfilPresion.ALTA),
                100.0 * plan.crecimientoColapso(PerfilPresion.CRITICA)));

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
                semillaBloqueos(plan, EscenarioOperativo.OPERACION_DIARIA, instancia));

        long idInicial = idInicial(
                EscenarioOperativo.OPERACION_DIARIA,
                instancia,
                0);

        List<Pedido> pedidos = ModeloDemandaHistorica.generarDiaPareado(
                demanda,
                perfil,
                dia,
                randomDemanda,
                idInicial);

        List<Bloqueo> bloqueosProgramados = ModeloBloqueosHistoricos.generarDiaPareado(
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
                semillaBloqueos(plan, EscenarioOperativo.SIMULACION_CINCO_DIAS, instancia));

        List<Pedido> pedidos = new ArrayList<>();
        List<Bloqueo> bloqueosProgramados = new ArrayList<>();

        for (int dia = 0; dia < 5; dia++) {
            LocalDate fecha = inicio.plusDays(dia);
            long idInicial = idInicial(
                    EscenarioOperativo.SIMULACION_CINCO_DIAS,
                    instancia,
                    dia);

            pedidos.addAll(ModeloDemandaHistorica.generarDiaPareado(
                    demanda,
                    perfil,
                    fecha,
                    randomDemanda,
                    idInicial));

            bloqueosProgramados.addAll(ModeloBloqueosHistoricos.generarDiaPareado(
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
        Set<String> mantenimiento = mantenimientoEn(plan, datos, inicio);
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
     * Para COLAPSO_LOGISTICO las tres presiones comienzan en exactamente la misma
     * primera jornada P50. SimuladorColapso continúa posteriormente con crecimiento
     * 5/10/15% (si g=0.05) y mantiene los mismos bloqueos externos entre perfiles.
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
                semillaBloqueos(plan, EscenarioOperativo.COLAPSO_LOGISTICO, instancia));

        long idInicial = idInicial(
                EscenarioOperativo.COLAPSO_LOGISTICO,
                instancia,
                0);

        // Día 0: todos los perfiles usan exactamente P50.
        List<Pedido> primeraJornada = ModeloDemandaHistorica.generarDiaAcoplado(
                demanda,
                plan.demandaP50Diaria(),
                plan.demandaP50Diaria(),
                inicio,
                randomDemanda,
                idInicial);

        // Los bloqueos de colapso se mantienen en presión vial NORMAL/P50 para
        // aislar el efecto del crecimiento de la demanda.
        List<Bloqueo> bloqueosProgramados = ModeloBloqueosHistoricos.generarDia(
                bloqueos,
                bloqueos.p50(),
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

    /** La semilla externa depende de escenario e instancia, no de perfil, algoritmo o repetición. */
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

    /** Semilla independiente de demanda, pero compartida por los tres perfiles. */
    static long semillaBloqueos(PlanExperimento plan,
                                EscenarioOperativo escenario,
                                int instancia) {
        return Math.addExact(
                semillaDemanda(plan, escenario, instancia),
                SALTO_BLOQUEOS);
    }

    /** Los IDs no contienen el perfil: un pedido compartido conserva el mismo ID. */
    static long idInicial(EscenarioOperativo escenario,
                          int instancia,
                          int dia) {
        return ID_BASE_SINTETICO
                + escenario.ordinal() * 100_000_000_000L
                + instancia * 100_000_000L
                + dia * 100_000L;
    }

    /** Aplica el mantenimiento preventivo como calendario bimestral. */
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

    /** No se reducen vehículos artificialmente por perfil. */
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