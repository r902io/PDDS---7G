package pe.edu.pucp.sisrap.pedido.api;

import java.util.List;
import java.util.NoSuchElementException;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import pe.edu.pucp.sisrap.control.aplicacion.GestionarControlSimulacion.ControlNoAutorizadoException;
import pe.edu.pucp.sisrap.pedido.aplicacion.GestionarPedidos;
import pe.edu.pucp.sisrap.pedido.aplicacion.GestionarPedidos.PeriodoYaCargadoException;
import pe.edu.pucp.sisrap.pedido.dominio.CargaHistoricaPedidos;
import pe.edu.pucp.sisrap.pedido.dominio.EstadoPedido;
import pe.edu.pucp.sisrap.pedido.dominio.PaginaPedidos;
import pe.edu.pucp.sisrap.pedido.dominio.PedidoOperativo;

@RestController
@RequestMapping("/api/pedidos")
public final class PedidoController {

    private final GestionarPedidos servicio;

    public PedidoController(
            GestionarPedidos servicio
    ) {
        this.servicio = servicio;
    }

    @GetMapping
    public PaginaPedidos listar(
            @RequestParam(required = false)
            Integer anio,

            @RequestParam(required = false)
            Integer mes,

            @RequestParam(required = false)
            EstadoPedido estado,

            @RequestParam(defaultValue = "0")
            int pagina,

            @RequestParam(defaultValue = "100")
            int tamanio
    ) {

        try {

            return servicio.listar(
                    anio,
                    mes,
                    estado,
                    pagina,
                    tamanio
            );

        } catch (IllegalArgumentException e) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    e.getMessage()
            );
        }
    }

    @GetMapping("/{idPedido}")
    public PedidoOperativo buscar(
            @PathVariable long idPedido
    ) {

        try {

            return servicio.buscar(
                    idPedido
            );

        } catch (NoSuchElementException e) {

            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    e.getMessage()
            );
        }
    }

    @GetMapping("/cargas-historicas")
    public List<CargaHistoricaPedidos>
    listarCargasHistoricas() {

        return servicio
                .listarCargasHistoricas();
    }

    @PostMapping(
            value = "/cargas-historicas",
            consumes = "multipart/form-data"
    )
    public ResponseEntity<CargaHistoricaPedidos>
    cargarHistorico(
            @RequestHeader(
                    value = "Authorization",
                    required = false
            )
            String authorization,

            @RequestParam int anio,

            @RequestParam int mes,

            @RequestPart("archivo")
            MultipartFile archivo
    ) {

        String token =
                extraerToken(
                        authorization
                );

        try {

            CargaHistoricaPedidos resultado =
                    servicio.importarHistorico(
                            token,
                            anio,
                            mes,
                            archivo
                    );

            return ResponseEntity
                    .status(HttpStatus.CREATED)
                    .body(resultado);

        } catch (ControlNoAutorizadoException e) {

            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    e.getMessage()
            );

        } catch (PeriodoYaCargadoException e) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    e.getMessage()
            );

        } catch (IllegalArgumentException e) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    e.getMessage()
            );
        }
    }

    private String extraerToken(
            String authorization
    ) {

        if (authorization == null
                || !authorization.startsWith("Bearer ")) {

            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Se requiere una sesión"
            );
        }

        String token =
                authorization.substring(7).trim();

        if (token.isBlank()) {

            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Token de sesión vacío"
            );
        }

        return token;
    }
}