package pe.edu.pucp.sisrap.parametros.dominio;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import pe.edu.pucp.sisrap.pedido.dominio.TipoPrioridad;

public final class ValidarConfiguracion {
    private ValidarConfiguracion() {}

    public static void ejecutar(Configuracion c) {
        new ParametrosAlgoritmo(c, c.largo("experimento.semillaBase"));
        ReglasPlanificacion.desde(c);

        for (String clave : List.of("objetivo.beta1", "objetivo.beta2", "objetivo.beta3")) {
            if (c.numero(clave) <= 0) {
                throw new IllegalArgumentException("Peso inválido: " + clave);
            }
        }

        int repeticiones = c.entero("experimento.repeticiones");
        if (repeticiones < 1) {
            throw new IllegalArgumentException("Repeticiones inválidas");
        }

        int maxDiasColapso = c.entero("experimento.colapso.maxDias");
        if (maxDiasColapso < 1) {
            throw new IllegalArgumentException("Máximo de días de colapso inválido");
        }

        double crecimientoColapso = c.numero("experimento.colapso.crecimientoDiario");
        if (crecimientoColapso <= 0.0 || crecimientoColapso * 3.0 >= 1.0) {
            throw new IllegalArgumentException(
                    "Crecimiento diario base de colapso inválido; se requiere 0 < g y 3g < 1");
        }

        try {
            Math.addExact(c.largo("experimento.semillaBase"), repeticiones - 1L);
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException("Rango de semillas fuera de long", e);
        }

        Set<Integer> plazos = new HashSet<>();
        for (TipoPrioridad prioridad : TipoPrioridad.values()) {
            int plazo = c.entero("prioridad." + prioridad.name());
            if (plazo <= 0 || !plazos.add(plazo)) {
                throw new IllegalArgumentException("Plazos duplicados o inválidos");
            }
        }
    }
}