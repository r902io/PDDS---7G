package pe.edu.pucp.sisrap.simulacion.dominio;

import java.time.LocalDateTime;

public record IncumplimientoPedido(long idPedido, LocalDateTime instante) { }
