package pe.edu.pucp.sisrap.experimentacion.dominio;

import java.time.LocalDateTime;
import java.util.List;

import pe.edu.pucp.sisrap.flota.dominio.Vehiculo;
import pe.edu.pucp.sisrap.parametros.dominio.ReglasPlanificacion;
import pe.edu.pucp.sisrap.pedido.dominio.Pedido;
import pe.edu.pucp.sisrap.planificador.dominio.modelo.ContextoPlanificacion;

/**
 * Una instancia del problema: un bloque de pedidos reales planificados en el instante en que llegó el último,
 * bajo un escenario operativo (operación día a día, simulación de 5 días o colapso logístico) y un perfil de
 * presión (normal, alta o crítica), tal como los define el informe "Diseño de Experimento" (sección 3.4).
 * Las reglas de planificación dependen de la variante, por eso el contexto se arma bajo demanda.
 *
 * @param nivel                    ordinal de la instancia dentro de (escenarioOperativo, perfilPresion); en
 *                                COLAPSO_LOGISTICO representa además el nivel de presión progresivo (sección 4.2)
 * @param vehiculosEnMantenimiento ids marcados como no disponibles por el mantenimiento preventivo del día
 * @param vehiculosEnBajaPorPerfil ids retirados de forma permanente por reducción de flota del perfil (CRITICA)
 * @param bloqueosActivos          bloqueos vigentes en el instante; se usan para dimensionar la incidencia
 *                                simulada en operación día a día (sección 4.2 del informe de Selección de Algoritmos)
 */
public record Escenario(String id, EscenarioOperativo escenarioOperativo, PerfilPresion perfilPresion, int tamanio,
                        int nivel, LocalDateTime instante, List<Pedido> pedidos, List<Vehiculo> vehiculos,
                        List<String> vehiculosEnMantenimiento, List<String> vehiculosEnBajaPorPerfil,
                        List<Bloqueo> bloqueosActivos) {
    public Escenario {
        pedidos = List.copyOf(pedidos);
        vehiculos = List.copyOf(vehiculos);
        vehiculosEnMantenimiento = List.copyOf(vehiculosEnMantenimiento);
        vehiculosEnBajaPorPerfil = List.copyOf(vehiculosEnBajaPorPerfil);
        bloqueosActivos = List.copyOf(bloqueosActivos);
    }

    /** Compatibilidad con el código que identifica una instancia por su ordinal (p. ej. comparaciones pareadas). */
    public int instancia() {
        return nivel;
    }

    public ContextoPlanificacion contexto(BaseOperativa base, ReglasPlanificacion reglas) {
        return new ContextoPlanificacion(pedidos, vehiculos, base.almacenes(), instante, reglas);
    }

    public int cantidadTotalQq() {
        return pedidos.stream().mapToInt(Pedido::getCantidadQq).sum();
    }

    public int capacidadDisponibleQq() {
        return vehiculos.stream().filter(Vehiculo::isDisponible).mapToInt(Vehiculo::getCapacidadPaquetes).sum();
    }
}