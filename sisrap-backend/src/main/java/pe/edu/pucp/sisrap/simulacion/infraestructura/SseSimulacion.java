package pe.edu.pucp.sisrap.simulacion.infraestructura;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import jakarta.annotation.PreDestroy;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import pe.edu.pucp.sisrap.simulacion.dominio.SnapshotSimulacion;

/** El motor nunca espera por la red; cada cliente conserva solo el último estado. */
@Component
public final class SseSimulacion {
    private final Set<Cliente> clientes = ConcurrentHashMap.newKeySet();
    private SnapshotSimulacion ultimo;
    private final ThreadPoolExecutor envios = new ThreadPoolExecutor(
            2, 2, 0L, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(64), r -> {
                Thread hilo = new Thread(r, "sisrap-eventos");
                hilo.setDaemon(true);
                return hilo;
            });

    public synchronized SseEmitter conectar(SnapshotSimulacion snapshotActual) {
        SseEmitter emitter = new SseEmitter(0L) {
            @Override
            protected void extendResponse(ServerHttpResponse respuesta) {
                super.extendResponse(respuesta);
                respuesta.getHeaders().set("Cache-Control", "no-cache, no-transform");
                respuesta.getHeaders().set("X-Accel-Buffering", "no");
            }
        };
        Cliente cliente = new Cliente(emitter);
        clientes.add(cliente);
        emitter.onCompletion(cliente::retirar);
        emitter.onTimeout(cliente::cerrar);
        emitter.onError(error -> cliente.retirar());
        if (ultimo != null && snapshotActual != null
                && java.util.Objects.equals(ultimo.idSimulacion(), snapshotActual.idSimulacion())
                && ultimo.relojSimulado() != null && snapshotActual.relojSimulado() != null
                && ultimo.relojSimulado().isAfter(snapshotActual.relojSimulado())) {
            snapshotActual = ultimo;
        }
        if (snapshotActual != null) cliente.ofrecer(snapshotActual);
        return emitter;
    }

    public synchronized void publicar(SnapshotSimulacion snapshot) {
        ultimo = snapshot;
        for (Cliente cliente : clientes) cliente.ofrecer(snapshot);
    }

    @PreDestroy
    void cerrar() {
        for (Cliente cliente : clientes) cliente.cerrar();
        envios.shutdownNow();
    }

    private final class Cliente {
        private final SseEmitter emitter;
        private final AtomicReference<SnapshotSimulacion> pendiente = new AtomicReference<>();
        private final AtomicBoolean enviando = new AtomicBoolean();
        private final AtomicBoolean cerrado = new AtomicBoolean();

        Cliente(SseEmitter emitter) { this.emitter = emitter; }

        void ofrecer(SnapshotSimulacion snapshot) {
            if (cerrado.get()) return;
            pendiente.set(snapshot);
            programar();
        }

        void programar() {
            if (cerrado.get() || !enviando.compareAndSet(false, true)) return;
            try {
                envios.execute(this::enviar);
            } catch (RejectedExecutionException e) {
                enviando.set(false);
                cerrar();
            }
        }

        void enviar() {
            try {
                SnapshotSimulacion snapshot = pendiente.getAndSet(null);
                if (!cerrado.get() && snapshot != null)
                    emitter.send(SseEmitter.event().name("snapshot").data(snapshot));
            } catch (IOException | RuntimeException e) {
                cerrar();
            } finally {
                enviando.set(false);
                if (!cerrado.get() && pendiente.get() != null) programar();
            }
        }

        void retirar() {
            cerrado.set(true);
            pendiente.set(null);
            clientes.remove(this);
        }

        void cerrar() {
            if (cerrado.getAndSet(true)) return;
            pendiente.set(null);
            clientes.remove(this);
            try { emitter.complete(); } catch (RuntimeException ignored) { }
        }
    }
}
