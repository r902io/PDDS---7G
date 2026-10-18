package pe.edu.pucp.sisrap.pedido.aplicacion;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import pe.edu.pucp.sisrap.mapa.dominio.MapaCiudad;
import pe.edu.pucp.sisrap.mapa.dominio.RepositorioMapa;
import pe.edu.pucp.sisrap.pedido.dominio.CargaHistoricaPedidos;
import pe.edu.pucp.sisrap.pedido.dominio.CargaHistoricaPreparada;
import pe.edu.pucp.sisrap.pedido.dominio.EstadoPedido;
import pe.edu.pucp.sisrap.pedido.dominio.PaginaPedidos;
import pe.edu.pucp.sisrap.pedido.dominio.PedidoHistoricoImportado;
import pe.edu.pucp.sisrap.pedido.dominio.PedidoOperativo;
import pe.edu.pucp.sisrap.pedido.dominio.RepositorioPedidos;
import pe.edu.pucp.sisrap.pedido.dominio.ResultadoCargaHistorica;
import pe.edu.pucp.sisrap.sesion.aplicacion.GestionarSesiones;
import pe.edu.pucp.sisrap.simulacion.aplicacion.MotorSimulacion;
import pe.edu.pucp.sisrap.simulacion.dominio.EstadoSimulacion;

@Service
public class GestionarPedidos {

    private static final long MAX_ARCHIVO_BYTES =
            10L * 1024L * 1024L;

    private static final long MAX_TOTAL_BYTES =
            50L * 1024L * 1024L;

    private static final int MAX_ARCHIVOS_POR_CARGA = 60;

    /**
     * Formatos admitidos, alineados con los archivos mensuales del curso:
     * ventas202601.txt
     * ventas.202601.txt
     * ventas_202601.csv
     * ventas-202601.txt
     */
    private static final Pattern PERIODO_EN_NOMBRE =
            Pattern.compile(
                    "(?i)^ventas[._-]?(\\d{4})(0[1-9]|1[0-2])\\.(txt|csv)$"
            );

    private final RepositorioPedidos repositorio;
    private final RepositorioMapa mapa;
    private final LectorPedidosHistoricos lector;
    private final GestionarSesiones sesiones;
    private final MotorSimulacion motorSimulacion;

    public GestionarPedidos(
            RepositorioPedidos repositorio,
            RepositorioMapa mapa,
            LectorPedidosHistoricos lector,
            GestionarSesiones sesiones,
            MotorSimulacion motorSimulacion
    ) {
        this.repositorio = repositorio;
        this.mapa = mapa;
        this.lector = lector;
        this.sesiones = sesiones;
        this.motorSimulacion = motorSimulacion;
    }

    public PaginaPedidos listar(
            Integer anio,
            Integer mes,
            EstadoPedido estado,
            int pagina,
            int tamanio
    ) {

        if (pagina < 0) {
            throw new IllegalArgumentException(
                    "La página no puede ser negativa"
            );
        }

        if (tamanio <= 0 || tamanio > 500) {
            throw new IllegalArgumentException(
                    "El tamaño debe estar entre 1 y 500"
            );
        }

        LocalDateTime desde = null;
        LocalDateTime hasta = null;

        if (mes != null && anio == null) {
            throw new IllegalArgumentException(
                    "Debe indicar el año si filtra por mes"
            );
        }

        if (anio != null) {

            if (mes != null) {

                YearMonth periodo =
                        YearMonth.of(anio, mes);

                desde = periodo.atDay(1).atStartOfDay();
                hasta = periodo
                        .plusMonths(1)
                        .atDay(1)
                        .atStartOfDay();

            } else {

                desde = LocalDateTime.of(
                        anio,
                        1,
                        1,
                        0,
                        0
                );

                hasta = LocalDateTime.of(
                        anio + 1,
                        1,
                        1,
                        0,
                        0
                );
            }
        }

        int offset = Math.multiplyExact(
                pagina,
                tamanio
        );

        List<PedidoOperativo> pedidos =
                repositorio.listar(
                        desde,
                        hasta,
                        estado,
                        offset,
                        tamanio
                );

        long total =
                repositorio.contar(
                        desde,
                        hasta,
                        estado
                );

        return new PaginaPedidos(
                pedidos,
                total,
                pagina,
                tamanio
        );
    }

    public PedidoOperativo registrarManual(
            String token, String cliente, int cantidad,
            pe.edu.pucp.sisrap.pedido.dominio.TipoPrioridad prioridad,
            int x, int y) {
        validarSesion(token);
        return motorSimulacion.registrarPedidoManual(cliente, cantidad, prioridad, x, y);
    }

    public PedidoOperativo buscar(
            long idPedido
    ) {

        return repositorio.buscar(idPedido)
                .orElseThrow(() ->
                        new NoSuchElementException(
                                "No existe el pedido "
                                        + idPedido
                        )
                );
    }

    public List<CargaHistoricaPedidos>
    listarCargasHistoricas() {

        return repositorio.listarCargasHistoricas();
    }

    public void eliminarCargaHistorica(String token, String huella) {
        validarSesion(token);
        if (huella == null || !huella.matches("[0-9a-fA-F]{64}")) {
            throw new IllegalArgumentException("La identificación de la carga es inválida");
        }
        synchronized (motorSimulacion) {
            verificarSimulacionNoActiva();
            repositorio.eliminarCargaHistorica(huella);
        }
    }

    public synchronized ResultadoCargaHistorica importarHistoricos(
            String token,
            List<MultipartFile> archivos
    ) {

        validarSesion(token);
        verificarSimulacionNoActiva();
        validarListaArchivos(archivos);

        MapaCiudad ciudad = mapa.obtener();

        List<CargaHistoricaPreparada> preparadas =
                new ArrayList<>();

        Set<YearMonth> periodosSolicitud =
                new HashSet<>();

        Set<String> huellasSolicitud =
                new HashSet<>();

        long totalBytes = 0L;

        for (MultipartFile archivo : archivos) {

            validarArchivoBasico(archivo);

            totalBytes = Math.addExact(
                    totalBytes,
                    archivo.getSize()
            );

            if (totalBytes > MAX_TOTAL_BYTES) {
                throw new IllegalArgumentException(
                        "La carga completa supera el límite de 50 MB"
                );
            }

            String nombreArchivo =
                    archivo.getOriginalFilename();

            YearMonth periodo =
                    obtenerPeriodoDesdeNombre(nombreArchivo);

            if (!periodosSolicitud.add(periodo)) {
                throw new PeriodoYaCargadoException(
                        "Se adjuntaron dos archivos para el mismo periodo: "
                                + periodo
                );
            }

            if (repositorio.existeCargaPeriodo(
                    periodo.getYear(),
                    periodo.getMonthValue()
            )) {
                throw new PeriodoYaCargadoException(
                        "Ya se cargaron pedidos para "
                                + periodo
                );
            }

            try {

                byte[] bytes = archivo.getBytes();

                String contenido =
                        new String(
                                bytes,
                                StandardCharsets.UTF_8
                        );

                List<PedidoHistoricoImportado> pedidos =
                        lector.leer(
                                nombreArchivo,
                                contenido,
                                periodo,
                                ciudad.anchoKm(),
                                ciudad.altoKm()
                        );

                String huella =
                        calcularHuella(
                                periodo,
                                contenido
                        );

                if (!huellasSolicitud.add(huella)) {
                    throw new IllegalArgumentException(
                            "Se adjuntó el mismo archivo más de una vez"
                    );
                }

                preparadas.add(
                        new CargaHistoricaPreparada(
                                nombreArchivo,
                                huella,
                                periodo.getYear(),
                                periodo.getMonthValue(),
                                pedidos
                        )
                );

            } catch (IllegalArgumentException e) {
                throw e;

            } catch (Exception e) {
                throw new IllegalStateException(
                        "No se pudo leer el archivo "
                                + nombreArchivo,
                        e
                );
            }
        }

        List<CargaHistoricaPedidos> cargas =
                repositorio.guardarCargasHistoricas(
                        preparadas
                );

        int pedidosInsertados =
                preparadas.stream()
                        .mapToInt(carga ->
                                carga.pedidos().size()
                        )
                        .sum();

        return new ResultadoCargaHistorica(
                preparadas.size(),
                pedidosInsertados,
                cargas
        );
    }

    private void validarSesion(
            String token
    ) {

        try {
            sesiones.validar(token);

        } catch (IllegalArgumentException e) {
            throw new SesionNoValidaException(
                    e.getMessage()
            );
        }
    }

    private void verificarSimulacionNoActiva() {

        EstadoSimulacion estado =
                motorSimulacion
                        .estadoActual()
                        .estado();

        if (estado == EstadoSimulacion.EJECUTANDO
                || estado == EstadoSimulacion.PAUSADA) {

            throw new CargaDuranteSimulacionException(
                    "Los archivos de ventas deben cargarse antes de iniciar la simulación"
            );
        }
    }

    private void validarListaArchivos(
            List<MultipartFile> archivos
    ) {

        if (archivos == null || archivos.isEmpty()) {
            throw new IllegalArgumentException(
                    "Debe adjuntar al menos un archivo de ventas"
            );
        }

        if (archivos.size() > MAX_ARCHIVOS_POR_CARGA) {
            throw new IllegalArgumentException(
                    "Se permiten como máximo "
                            + MAX_ARCHIVOS_POR_CARGA
                            + " archivos por carga"
            );
        }
    }

    private void validarArchivoBasico(
            MultipartFile archivo
    ) {

        if (archivo == null || archivo.isEmpty()) {
            throw new IllegalArgumentException(
                    "Todos los archivos adjuntos deben contener datos"
            );
        }

        if (archivo.getSize() > MAX_ARCHIVO_BYTES) {
            throw new IllegalArgumentException(
                    "El archivo "
                            + archivo.getOriginalFilename()
                            + " supera el límite de 10 MB"
            );
        }
    }

    private YearMonth obtenerPeriodoDesdeNombre(
            String nombreArchivo
    ) {

        if (nombreArchivo == null
                || nombreArchivo.isBlank()) {
            throw new IllegalArgumentException(
                    "Todos los archivos deben tener nombre"
            );
        }

        String nombre = nombreArchivo
                .trim()
                .toLowerCase(Locale.ROOT);

        Matcher matcher =
                PERIODO_EN_NOMBRE.matcher(nombre);

        if (!matcher.matches()) {
            throw new IllegalArgumentException(
                    "Nombre de archivo inválido: "
                            + nombreArchivo
                            + ". Use por ejemplo ventas202601.txt "
                            + "o ventas.202601.txt"
            );
        }

        int anio = Integer.parseInt(
                matcher.group(1)
        );

        int mes = Integer.parseInt(
                matcher.group(2)
        );

        return YearMonth.of(
                anio,
                mes
        );
    }

    private String calcularHuella(
            YearMonth periodo,
            String contenido
    ) {

        try {

            String normalizado =
                    contenido
                            .replace("\uFEFF", "")
                            .replace("\r\n", "\n")
                            .strip();

            MessageDigest digest =
                    MessageDigest.getInstance(
                            "SHA-256"
                    );

            byte[] resultado =
                    digest.digest(
                            (
                                    periodo
                                            + "\n"
                                            + normalizado
                            ).getBytes(
                                    StandardCharsets.UTF_8
                            )
                    );

            return HexFormat.of()
                    .formatHex(resultado);

        } catch (NoSuchAlgorithmException e) {

            throw new IllegalStateException(
                    "SHA-256 no disponible",
                    e
            );
        }
    }

    public static final class PeriodoYaCargadoException
            extends RuntimeException {

        public PeriodoYaCargadoException(
                String mensaje
        ) {
            super(mensaje);
        }
    }

    public static final class SesionNoValidaException
            extends RuntimeException {

        public SesionNoValidaException(
                String mensaje
        ) {
            super(mensaje);
        }
    }

    public static final class CargaDuranteSimulacionException
            extends RuntimeException {

        public CargaDuranteSimulacionException(
                String mensaje
        ) {
            super(mensaje);
        }
    }
}
