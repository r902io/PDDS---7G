package pe.edu.pucp.sisrap.sesion.aplicacion;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

import org.springframework.stereotype.Service;

import pe.edu.pucp.sisrap.sesion.dominio.RepositorioSesion;
import pe.edu.pucp.sisrap.sesion.dominio.Sesion;

@Service
public final class GestionarSesiones {

    private static final Duration DURACION_SESION = Duration.ofHours(12);

    private final RepositorioSesion repositorio;

    private final SecureRandom secureRandom = new SecureRandom();

    public GestionarSesiones(RepositorioSesion repositorio) {
        this.repositorio = repositorio;
    }

    public SesionEmitida crear() {

        Instant ahora = Instant.now();

        repositorio.limpiarExpiradas(ahora);

        String token = generarToken();
        String hashToken = hash(token);

        Sesion sesion = new Sesion(
                UUID.randomUUID(),
                ahora,
                ahora,
                ahora.plus(DURACION_SESION)
        );

        repositorio.guardar(hashToken, sesion);

        return new SesionEmitida(
                sesion,
                token
        );
    }

    public Sesion validar(String token) {

        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("Token de sesión requerido");
        }

        Instant ahora = Instant.now();

        repositorio.limpiarExpiradas(ahora);

        String hashToken = hash(token);

        Sesion sesion = repositorio.buscar(hashToken)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Sesión inválida o expirada"
                        )
                );

        if (sesion.expirada(ahora)) {
            repositorio.eliminar(hashToken);

            throw new IllegalArgumentException(
                    "Sesión inválida o expirada"
            );
        }

        Sesion renovada = sesion.renovar(
                ahora,
                DURACION_SESION
        );

        repositorio.guardar(
                hashToken,
                renovada
        );

        return renovada;
    }

    public void cerrar(String token) {

        if (token == null || token.isBlank()) {
            return;
        }

        repositorio.eliminar(hash(token));
    }

    private String generarToken() {

        byte[] bytes = new byte[32];

        secureRandom.nextBytes(bytes);

        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(bytes);
    }

    private String hash(String token) {

        try {

            MessageDigest digest =
                    MessageDigest.getInstance("SHA-256");

            byte[] resultado = digest.digest(
                    token.getBytes(StandardCharsets.UTF_8)
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

    public record SesionEmitida(
            Sesion sesion,
            String token
    ) {}
}