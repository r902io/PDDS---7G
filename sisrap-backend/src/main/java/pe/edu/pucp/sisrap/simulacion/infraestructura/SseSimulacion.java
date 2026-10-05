package pe.edu.pucp.sisrap.simulacion.infraestructura;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import pe.edu.pucp.sisrap.simulacion.dominio.SnapshotSimulacion;

@Component
public final class SseSimulacion {

    private final List<SseEmitter> clientes = new CopyOnWriteArrayList<>();

    public SseEmitter conectar(SnapshotSimulacion snapshotActual) {
        SseEmitter emitter = new SseEmitter(0L);
        clientes.add(emitter);

        emitter.onCompletion(() -> clientes.remove(emitter));
        emitter.onTimeout(() -> clientes.remove(emitter));
        emitter.onError(error -> clientes.remove(emitter));

        try {
            emitter.send(SseEmitter.event()
                    .name("snapshot")
                    .data(snapshotActual));
        } catch (IOException e) {
            clientes.remove(emitter);
            emitter.completeWithError(e);
        }

        return emitter;
    }

    public void publicar(SnapshotSimulacion snapshot) {
        for (SseEmitter emitter : clientes) {
            try {
                emitter.send(SseEmitter.event()
                        .name("snapshot")
                        .data(snapshot));
            } catch (IOException | IllegalStateException e) {
                clientes.remove(emitter);
                try {
                    emitter.complete();
                } catch (Exception ignored) {
                    // El cliente ya estaba cerrado.
                }
            }
        }
    }
}
