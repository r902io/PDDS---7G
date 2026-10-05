package pe.edu.pucp.sisrap.pedido.aplicacion;

import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import pe.edu.pucp.sisrap.pedido.dominio.PedidoHistoricoImportado;
import pe.edu.pucp.sisrap.pedido.dominio.TipoPrioridad;

@Component
public final class LectorPedidosHistoricos {

    private static final Pattern FORMATO_TXT =
            Pattern.compile(
                    "(\\d{2})d(\\d{2})h(\\d{2})m:"
                            + "(\\d+),(\\d+),([^,]+),(\\d+),(\\d+)"
            );

    public List<PedidoHistoricoImportado> leer(
            String nombreArchivo,
            String contenido,
            YearMonth periodo,
            int ancho,
            int alto
    ) {

        if (nombreArchivo == null
                || nombreArchivo.isBlank()) {

            throw new IllegalArgumentException(
                    "El archivo debe tener un nombre"
            );
        }

        String nombre =
                nombreArchivo.toLowerCase(
                        Locale.ROOT
                );

        String normalizado =
                contenido
                        .replace("\uFEFF", "")
                        .replace("\r\n", "\n")
                        .strip();

        if (normalizado.isBlank()) {

            throw new IllegalArgumentException(
                    "El archivo está vacío"
            );
        }

        if (nombre.endsWith(".txt")) {

            return leerTxt(
                    normalizado,
                    periodo,
                    ancho,
                    alto
            );
        }

        if (nombre.endsWith(".csv")) {

            return leerCsv(
                    normalizado,
                    periodo,
                    ancho,
                    alto
            );
        }

        throw new IllegalArgumentException(
                "Solo se admiten archivos .txt o .csv"
        );
    }

    private List<PedidoHistoricoImportado> leerTxt(
            String contenido,
            YearMonth periodo,
            int ancho,
            int alto
    ) {

        List<PedidoHistoricoImportado> salida =
                new ArrayList<>();

        int numeroLinea = 0;

        for (String linea :
                contenido.split("\\R")) {

            numeroLinea++;

            if (linea.isBlank()) {
                continue;
            }

            Matcher matcher =
                    FORMATO_TXT.matcher(
                            linea.strip()
                    );

            if (!matcher.matches()) {

                throw new IllegalArgumentException(
                        "Formato inválido en línea "
                                + numeroLinea
                );
            }

            try {

                int dia =
                        Integer.parseInt(
                                matcher.group(1)
                        );

                int hora =
                        Integer.parseInt(
                                matcher.group(2)
                        );

                int minuto =
                        Integer.parseInt(
                                matcher.group(3)
                        );

                int x =
                        Integer.parseInt(
                                matcher.group(4)
                        );

                int y =
                        Integer.parseInt(
                                matcher.group(5)
                        );

                String cliente =
                        matcher.group(6).trim();

                int cantidad =
                        Integer.parseInt(
                                matcher.group(7)
                        );

                int horas =
                        Integer.parseInt(
                                matcher.group(8)
                        );

                salida.add(
                        construir(
                                periodo,
                                dia,
                                hora,
                                minuto,
                                x,
                                y,
                                cliente,
                                cantidad,
                                horas,
                                ancho,
                                alto,
                                numeroLinea
                        )
                );

            } catch (RuntimeException e) {

                throw new IllegalArgumentException(
                        "Datos inválidos en línea "
                                + numeroLinea
                                + ": "
                                + e.getMessage(),
                        e
                );
            }
        }

        validarNoVacio(salida);

        return List.copyOf(salida);
    }

    private List<PedidoHistoricoImportado> leerCsv(
            String contenido,
            YearMonth periodo,
            int ancho,
            int alto
    ) {

        List<PedidoHistoricoImportado> salida =
                new ArrayList<>();

        String[] lineas =
                contenido.split("\\R");

        int inicio = 0;

        if (lineas.length > 0
                && lineas[0]
                .toLowerCase(Locale.ROOT)
                .contains("dia")) {

            inicio = 1;
        }

        for (int i = inicio;
             i < lineas.length;
             i++) {

            String linea =
                    lineas[i].strip();

            if (linea.isBlank()) {
                continue;
            }

            int numeroLinea = i + 1;

            String[] columnas =
                    linea.split(
                            ",",
                            -1
                    );

            if (columnas.length != 8) {

                throw new IllegalArgumentException(
                        "El CSV debe contener 8 columnas. "
                                + "Error en línea "
                                + numeroLinea
                );
            }

            try {

                int dia =
                        Integer.parseInt(
                                columnas[0].trim()
                        );

                int hora =
                        Integer.parseInt(
                                columnas[1].trim()
                        );

                int minuto =
                        Integer.parseInt(
                                columnas[2].trim()
                        );

                int x =
                        Integer.parseInt(
                                columnas[3].trim()
                        );

                int y =
                        Integer.parseInt(
                                columnas[4].trim()
                        );

                String cliente =
                        columnas[5].trim();

                int cantidad =
                        Integer.parseInt(
                                columnas[6].trim()
                        );

                int horas =
                        Integer.parseInt(
                                columnas[7].trim()
                        );

                salida.add(
                        construir(
                                periodo,
                                dia,
                                hora,
                                minuto,
                                x,
                                y,
                                cliente,
                                cantidad,
                                horas,
                                ancho,
                                alto,
                                numeroLinea
                        )
                );

            } catch (RuntimeException e) {

                throw new IllegalArgumentException(
                        "Datos inválidos en línea "
                                + numeroLinea
                                + ": "
                                + e.getMessage(),
                        e
                );
            }
        }

        validarNoVacio(salida);

        return List.copyOf(salida);
    }

    private PedidoHistoricoImportado construir(
            YearMonth periodo,
            int dia,
            int hora,
            int minuto,
            int x,
            int y,
            String cliente,
            int cantidad,
            int horas,
            int ancho,
            int alto,
            int linea
    ) {

        if (cliente.isBlank()
                || cliente.length() > 20) {

            throw new IllegalArgumentException(
                    "Cliente inválido en línea "
                            + linea
            );
        }

        if (cantidad <= 0) {

            throw new IllegalArgumentException(
                    "Cantidad inválida"
            );
        }

        if (x < 0
                || x > ancho
                || y < 0
                || y > alto) {

            throw new IllegalArgumentException(
                    "Coordenadas fuera del mapa"
            );
        }

        TipoPrioridad prioridad =
                prioridadDe(horas);

        return new PedidoHistoricoImportado(
                cliente,
                cantidad,
                prioridad,
                horas,
                periodo
                        .atDay(dia)
                        .atTime(
                                hora,
                                minuto
                        ),
                x,
                y
        );
    }

    private TipoPrioridad prioridadDe(
            int horas
    ) {

        return switch (horas) {

            case 36 ->
                    TipoPrioridad.REGULAR_36H;

            case 18 ->
                    TipoPrioridad.PRIORIZADO_18H;

            case 12 ->
                    TipoPrioridad.PRIORIZADO_12H;

            case 8 ->
                    TipoPrioridad.PRIORIZADO_8H;

            case 4 ->
                    TipoPrioridad.PRIORIZADO_4H;

            default ->
                    throw new IllegalArgumentException(
                            "Plazo no permitido: "
                                    + horas
                    );
        };
    }

    private void validarNoVacio(
            List<PedidoHistoricoImportado> pedidos
    ) {

        if (pedidos.isEmpty()) {

            throw new IllegalArgumentException(
                    "El archivo no contiene pedidos"
            );
        }
    }
}