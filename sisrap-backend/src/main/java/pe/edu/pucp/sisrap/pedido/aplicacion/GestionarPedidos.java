package pe.edu.pucp.sisrap.pedido.aplicacion;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.HexFormat;
import java.util.List;
import java.util.NoSuchElementException;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import pe.edu.pucp.sisrap.control.aplicacion.GestionarControlSimulacion;
import pe.edu.pucp.sisrap.mapa.dominio.MapaCiudad;
import pe.edu.pucp.sisrap.mapa.dominio.RepositorioMapa;
import pe.edu.pucp.sisrap.pedido.dominio.CargaHistoricaPedidos;
import pe.edu.pucp.sisrap.pedido.dominio.EstadoPedido;
import pe.edu.pucp.sisrap.pedido.dominio.PaginaPedidos;
import pe.edu.pucp.sisrap.pedido.dominio.PedidoHistoricoImportado;
import pe.edu.pucp.sisrap.pedido.dominio.PedidoOperativo;
import pe.edu.pucp.sisrap.pedido.dominio.RepositorioPedidos;

@Service
public final class GestionarPedidos {

    private static final long MAX_ARCHIVO_BYTES =
            10L * 1024L * 1024L;

    private final RepositorioPedidos repositorio;
    private final RepositorioMapa mapa;
    private final LectorPedidosHistoricos lector;
    private final GestionarControlSimulacion control;

    public GestionarPedidos(
            RepositorioPedidos repositorio,
            RepositorioMapa mapa,
            LectorPedidosHistoricos lector,
            GestionarControlSimulacion control
    ) {
        this.repositorio = repositorio;
        this.mapa = mapa;
        this.lector = lector;
        this.control = control;
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

        if (tamanio <= 0
                || tamanio > 500) {

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
                        YearMonth.of(
                                anio,
                                mes
                        );

                desde =
                        periodo
                                .atDay(1)
                                .atStartOfDay();

                hasta =
                        periodo
                                .plusMonths(1)
                                .atDay(1)
                                .atStartOfDay();

            } else {

                desde =
                        LocalDateTime.of(
                                anio,
                                1,
                                1,
                                0,
                                0
                        );

                hasta =
                        LocalDateTime.of(
                                anio + 1,
                                1,
                                1,
                                0,
                                0
                        );
            }
        }

        int offset =
                Math.multiplyExact(
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

        return repositorio
                .listarCargasHistoricas();
    }

    public synchronized CargaHistoricaPedidos
    importarHistorico(
            String token,
            int anio,
            int mes,
            MultipartFile archivo
    ) {

        control.verificarControlador(token);

        YearMonth periodo =
                YearMonth.of(
                        anio,
                        mes
                );

        if (archivo == null
                || archivo.isEmpty()) {

            throw new IllegalArgumentException(
                    "Debe adjuntar un archivo"
            );
        }

        if (archivo.getSize()
                > MAX_ARCHIVO_BYTES) {

            throw new IllegalArgumentException(
                    "El archivo supera el límite de 10 MB"
            );
        }

        if (repositorio.existeCargaPeriodo(
                anio,
                mes
        )) {

            throw new PeriodoYaCargadoException(
                    "Ya se cargaron pedidos para "
                            + periodo
            );
        }

        try {

            byte[] bytes =
                    archivo.getBytes();

            String contenido =
                    new String(
                            bytes,
                            StandardCharsets.UTF_8
                    );

            MapaCiudad ciudad =
                    mapa.obtener();

            List<PedidoHistoricoImportado> pedidos =
                    lector.leer(
                            archivo.getOriginalFilename(),
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

            return repositorio
                    .guardarCargaHistorica(
                            huella,
                            anio,
                            mes,
                            pedidos
                    );

        } catch (PeriodoYaCargadoException e) {
            throw e;

        } catch (IllegalArgumentException e) {
            throw e;

        } catch (Exception e) {

            throw new IllegalStateException(
                    "No se pudo leer el archivo",
                    e
            );
        }
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
}