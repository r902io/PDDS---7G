package pe.edu.pucp.sisrap.sesion.dominio;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

public record Sesion(
        UUID id,
        Instant creadaEn,
        Instant ultimaActividadEn,
        Instant expiraEn
) {

    public boolean expirada(Instant ahora) {
        return !ahora.isBefore(expiraEn);
    }

    public Sesion renovar(Instant ahora, Duration duracion) {
        return new Sesion(
                id,
                creadaEn,
                ahora,
                ahora.plus(duracion)
        );
    }
}