package pe.edu.pucp.sisrap.pedido.api;

import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import javax.sql.DataSource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pe.edu.pucp.sisrap.pedido.dominio.EstadoPedido;
import pe.edu.pucp.sisrap.pedido.dominio.PedidoOperativo;
import pe.edu.pucp.sisrap.pedido.dominio.TipoPrioridad;
import pe.edu.pucp.sisrap.simulacion.aplicacion.MotorSimulacion;

@RestController
@RequestMapping("/api/pedidos")
public class PedidoCriticoController {
    private final DataSource fuente;
    private final MotorSimulacion motor;

    public PedidoCriticoController(DataSource fuente, MotorSimulacion motor) {
        this.fuente = fuente; this.motor = motor;
    }

    public record Criticidad(Long idSimulacion, LocalDateTime relojSimulado, PedidoOperativo pedido) { }

    @GetMapping("/criticidad")
    public Criticidad consultar() {
        synchronized (motor) {
            var estado = motor.estadoActual();
            var reloj = estado.relojSimulado();
            if (estado.idSimulacion() == null || reloj == null)
                return new Criticidad(estado.idSimulacion(), reloj, null);
            try (var cn = fuente.getConnection(); var ps = cn.prepareStatement("""
                    SELECT p.id_pedido,p.id_cliente,p.cantidad_qq,p.prioridad,p.horas_limite,
                           p.fecha_llegada,p.estado,p.ubicacion_x,p.ubicacion_y
                    FROM pedido p
                    WHERE p.fecha_entrega_real IS NULL AND p.fecha_llegada<=?
                      AND p.estado IN ('PENDIENTE','EN_RUTA','REASIGNADO','RETRASADO')
                      AND NOT EXISTS (
                          SELECT 1 FROM pedido_arribo a
                          WHERE a.id_pedido=p.id_pedido AND a.fecha_arribo<=?
                      )
                    ORDER BY TIMESTAMPADD(HOUR,p.horas_limite,p.fecha_llegada),p.id_pedido LIMIT 1
                    """)) {
                // El SLA se cumple al completar el arribo, antes de la atención.
                // Para pedidos fraccionados, el motor registra ese arribo cuando
                // todas las fracciones llegaron; los parciales siguen participando.
                ps.setTimestamp(1, Timestamp.valueOf(reloj));
                ps.setTimestamp(2, Timestamp.valueOf(reloj));
                try (var rs = ps.executeQuery()) {
                    PedidoOperativo pedido = rs.next() ? new PedidoOperativo(rs.getLong("id_pedido"),
                        rs.getString("id_cliente"), rs.getInt("cantidad_qq"), TipoPrioridad.valueOf(rs.getString("prioridad")),
                        rs.getInt("horas_limite"), rs.getTimestamp("fecha_llegada").toLocalDateTime(), null,
                        EstadoPedido.valueOf(rs.getString("estado")), rs.getInt("ubicacion_x"), rs.getInt("ubicacion_y")) : null;
                    return new Criticidad(estado.idSimulacion(), reloj, pedido);
                }
            } catch (SQLException e) { throw new IllegalStateException("No se pudo consultar el pedido de mayor criticidad", e); }
        }
    }
}
