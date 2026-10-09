package pe.edu.pucp.sisrap.planificador.dominio.modelo;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

public final class JornadaOperativa {
    private static final double NANOS_HORA = 3_600_000_000_000.0;

    private JornadaOperativa() { }

    public static LocalDateTime inicioTurno(LocalDateTime momento) {
        LocalDate dia = momento.toLocalDate();
        LocalTime hora = momento.toLocalTime();
        if (hora.isBefore(LocalTime.of(7, 0))) return dia.minusDays(1).atTime(23, 0);
        if (hora.isBefore(LocalTime.of(15, 0))) return dia.atTime(7, 0);
        if (hora.isBefore(LocalTime.of(23, 0))) return dia.atTime(15, 0);
        return dia.atTime(23, 0);
    }

    public static LocalDateTime finTurno(LocalDateTime momento) {
        return inicioTurno(momento).plusHours(8);
    }

    public static boolean refrigerio(LocalDateTime momento) {
        LocalDateTime inicio = inicioTurno(momento).plusHours(4);
        return !momento.isBefore(inicio) && momento.isBefore(inicio.plusHours(1));
    }

    public static LocalDateTime avanzar(LocalDateTime inicio, double horasEfectivas) {
        if (!Double.isFinite(horasEfectivas) || horasEfectivas < 0) {
            throw new IllegalArgumentException("Duración de desplazamiento inválida");
        }
        long restante = Math.round(horasEfectivas * NANOS_HORA);
        LocalDateTime momento = inicio;
        while (restante > 0 || refrigerio(momento)) {
            LocalDateTime comienzaRefrigerio = inicioTurno(momento).plusHours(4);
            LocalDateTime terminaRefrigerio = comienzaRefrigerio.plusHours(1);
            if (refrigerio(momento)) {
                momento = terminaRefrigerio;
                continue;
            }
            LocalDateTime proximo = momento.isBefore(comienzaRefrigerio)
                    ? comienzaRefrigerio : inicioTurno(momento).plusHours(12);
            long disponibles = Duration.between(momento, proximo).toNanos();
            if (restante <= disponibles) return momento.plusNanos(restante);
            momento = proximo;
            restante -= disponibles;
        }
        return momento;
    }

    public static double horasEfectivas(LocalDateTime desde, LocalDateTime hasta) {
        if (hasta.isBefore(desde)) throw new IllegalArgumentException("Intervalo de tiempo inválido");
        double total = Duration.between(desde, hasta).toNanos() / NANOS_HORA;
        LocalDateTime turno = inicioTurno(desde);
        while (turno.isBefore(hasta)) {
            LocalDateTime inicio = turno.plusHours(4);
            LocalDateTime fin = inicio.plusHours(1);
            LocalDateTime comunInicio = inicio.isAfter(desde) ? inicio : desde;
            LocalDateTime comunFin = fin.isBefore(hasta) ? fin : hasta;
            if (comunFin.isAfter(comunInicio)) {
                total -= Duration.between(comunInicio, comunFin).toNanos() / NANOS_HORA;
            }
            turno = turno.plusHours(8);
        }
        return Math.max(0.0, total);
    }
}
