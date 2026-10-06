# Brechas entre el visualizador y sisrap-backend

Documento para el equipo de backend. Lista los datos que el visualizador necesita y que el backend
todavía no entrega. Donde falta un dato, la interfaz muestra **"No disponible"** y no inventa valores.

Revisado contra el código de `sisrap-backend` (rama `dev`, 06/10/2026) y contra el backend desplegado en
`http://1inf54-981-7g.inf.pucp.edu.pe`.

---

## 1. Registro de averías tipo 1, 2 y 3 desde el visualizador

- **Pantalla:** Operación en curso (detalle del vehículo seleccionado).
- **Situación actual:** existe `POST /api/vehiculos/{id}/averias` con `{ tipoAveria, fechaOcurrencia, horaRetornoEstimada }`
  y `TipoAveria = TIPO1_MENOR | TIPO2_INTERMEDIA | TIPO3_MAYOR`. Falta definir cómo se usa durante una corrida:
  - `fechaOcurrencia` debería ser el **reloj simulado** y no la hora del cliente. Propuesta: que el backend la
    tome de `MotorSimulacion` cuando hay corrida activa y la ignore si viene en la solicitud.
  - `horaRetornoEstimada` debería calcularla el backend según el tipo (reglas del enunciado), no el cliente.
  - El endpoint solo valida que la sesión exista. Debe decidirse si solo la **sesión controladora** puede
    registrar averías (como `/detener`) y responder 403 a las demás.
  - La respuesta debería incluir el snapshot actualizado o, al menos, el nuevo estado `EN_AVERIA` del vehículo.
- **Mientras tanto:** el visualizador no muestra ningún control de avería (el curso prohíbe controles simulados).
  Las averías existentes se ven en solo lectura en *Mantenimiento → Vehículos* y en el mapa (icono de avería).

## 2. Mantenimiento preventivo en el snapshot

- **Pantalla:** mapa e indicadores de flota.
- **Situación actual:** `VehiculoSnapshot.estado` vale `EN_MANTENIMIENTO` tanto para mantenimiento preventivo
  como correctivo (`sincronizarDisponibilidad`).
- **Propuesta:** agregar a `VehiculoSnapshot` el campo `tipoIndisponibilidad: "PREVENTIVO" | "CORRECTIVO" | "AVERIA" | null`,
  o un estado distinto `EN_MANTENIMIENTO_PREVENTIVO`, para mostrar el preventivo (00:00–23:59 del día programado)
  distinto de una avería y de un correctivo.

## 3. Ubicación y semáforo de los pedidos (clientes en el mapa)

- **Pantalla:** mapa (marcas de cliente con color de semáforo) e indicadores.
- **Situación actual:** el snapshot solo trae el resumen `pedidos` (contadores). Los pedidos de la corrida son
  sintéticos, con **identificadores negativos** (`-1000004`), y no existen en `/api/pedidos`, así que el
  front no puede pedir su ubicación ni su plazo.
- **Propuesta:** agregar al snapshot `pedidosActivos: [{ idPedido, idCliente, x, y, cantidad, fechaLlegada,
  fechaLimite, estado, holguraHoras }]` con los pedidos pendientes, en ruta y reasignados. Con `holguraHoras`
  el front aplica el semáforo (verde/ámbar/rojo) con los umbrales de `parametros_semaforo`.
- **Alternativa:** `GET /api/simulacion/pedidos` que devuelva lo mismo.

## 4. Holgura mínima y pedido crítico

- **Pantalla:** Operación en curso, panel de indicadores ("Holgura mínima", "Pedido crítico").
- **Propuesta:** en `SnapshotSimulacion`, `holguraMinimaHoras: number | null` e `idPedidoCritico: number | null`.
  Si se implementa el punto 3, el front puede calcularlo, pero es preferible que lo entregue el backend.

## 5. Distancia, costo y utilización por tipo de vehículo

- **Pantallas:** indicadores de flota y resultado de la corrida ("Distancia recorrida", "Costo acumulado",
  "Utilización por tipo de vehículo").
- **Propuesta:** en `SnapshotSimulacion`, `indicadoresFlota: [{ tipo: "AUTO"|"MOTO"|"BICICLETA", distanciaKm,
  costoSoles, utilizacion }]` (utilización = fracción del tiempo o de la capacidad, a definir) y los totales.
- Hoy el front solo muestra, desde el snapshot, cuántas unidades de cada tipo están en ruta, averiadas o en
  mantenimiento.

## 6. Carga actual y capacidad de cada vehículo

- **Pantalla:** tooltip y panel de detalle del vehículo.
- **Situación actual:** la capacidad sale de `/api/vehiculos`; la carga actual no está en ningún endpoint.
- **Propuesta:** en `VehiculoSnapshot`, `cargaActual: number` (paquetes a bordo) y opcionalmente `capacidad`.

## 7. Tramo ya recorrido de cada ruta

- **Pantalla:** mapa (el prototipo dibuja lo recorrido punteado y lo pendiente en línea llena).
- **Situación actual:** `VehiculoSnapshot.caminoActual` contiene solo lo pendiente.
- **Propuesta:** en `VehiculoSnapshot`, `caminoRecorrido: PuntoSimulacion[]` desde el inicio del viaje actual, o
  `rutaCompleta` más el índice del nodo actual.

## 8. Pedido que originó el colapso y su instante

- **Pantalla:** Resultado de la corrida (colapso logístico).
- **Situación actual:** al detectar el colapso, `MotorSimulacion.finalizar` solo deja el mensaje
  "Colapso logístico: se detectó el primer incumplimiento de plazo". El front muestra el reloj al detenerse
  como instante de detección.
- **Propuesta:** en `SnapshotSimulacion`, `colapso: { idPedido, idCliente, x, y, fechaLimite, instanteDeteccion } | null`.

## 9. Coordenadas de los almacenes

- **Situación actual:** correctas. La migración `V20260922_1200__corrige_almacenes.sql` deja el central en
  (27,14) y el Este en (57,27); el Nor-Oeste está en (12,38). El backend desplegado responde esos valores.
- **Observación:** la semilla original (`V20260919_1043__schema_inicial.sql`) inserta (25,15) y (55,27). Si alguien
  inicializa una base sin aplicar todas las migraciones, el mapa mostrará esas posiciones: el front no corrige
  coordenadas.

## 10. Varios escenarios a la vez desde distintos dispositivos

- **Situación actual:** `MotorSimulacion` ejecuta una sola corrida y `ControlSimulacion` admite un solo
  propietario. Todos los dispositivos ven la misma corrida; si alguien intenta iniciar otra recibe 409.
- **Pregunta para el equipo:** ¿el profesor espera que distintos dispositivos vean escenarios distintos al mismo
  tiempo? Si es así, se necesita un motor por corrida, `idSimulacion` en las rutas
  (`/api/simulaciones/{id}/eventos`, `/estado`, `/detener`) y un `GET /api/simulaciones` que liste las activas.

---

## Otras brechas detectadas durante la integración

### 11. Sesión controladora no informada

- **Pantalla:** cabecera (rol Controlador / Observador).
- **Situación actual:** el snapshot no dice qué sesión controla la corrida. El front recuerda localmente qué
  sesión hizo `POST /iniciar` con éxito y corrige su rol si `/detener` responde 403. Si el usuario borra el
  almacenamiento del navegador, ve la corrida como observador aunque su token siga siendo el controlador.
- **Propuesta:** `GET /api/sesiones/actual` (o el snapshot) con `controlaSimulacion: boolean`.

### 12. Semilla de la corrida

- **Pantalla:** panel de parámetros ("Semilla").
- **Propuesta:** agregar `semilla` a `SnapshotSimulacion` para que los observadores vean con qué semilla corre.

### 13. SSE sin latido

- **Situación actual:** `SseSimulacion` no envía nada mientras no hay corrida. En la prueba con el servidor
  desplegado (nginx), una conexión inactiva quedó abierta pero muerta, sin error en el navegador.
- **Mitigación en el front:** si no llega un evento en 6 s (corrida activa) o 20 s (sin corrida), la
  conexión se reabre y el backend reenvía el snapshot vigente.
- **Propuesta:** enviar un comentario SSE (`:ping`) o un evento `latido` cada 15 s, configurar en nginx
  `proxy_buffering off; proxy_read_timeout 1h;` para `/api/simulacion/eventos`, y agregar la cabecera
  `X-Accel-Buffering: no` en la respuesta.

### 14. Mensajes de error sin detalle

- Spring Boot omite `message` en las respuestas de error (`server.error.include-message` por defecto). El front
  traduce el código HTTP a un mensaje genérico. Propuesta: `server.error.include-message=always` para que el
  usuario vea, por ejemplo, "Otra sesión ya controla la simulación activa".

### 15. Parámetros del algoritmo

- `ParametrosController` (`/api/parametros/{perfil}`) solo existe con el perfil `experimentacion`. En el perfil
  normal el visualizador no puede mostrar los parámetros del perfil `BASE` que usa la corrida.

### 16. Bloqueos creados desde el front anterior

- En la base desplegada hay bloqueos con `idIncidencia` 11, 12 y 13 creados el 06/10/2026 desde el botón de
  "crear bloqueo" del front anterior (ya eliminado). El enunciado indica que los bloqueos vienen solo del
  archivo mensual; conviene cancelarlos. `POST /api/bloqueos` sigue abierto a cualquier sesión.

### 17. Pedidos de la fecha elegida

- Una corrida de prueba iniciada el 06/10/2026 mostró `pedidos.total = 48`, todos generados por el modelo de
  demanda. Conviene confirmar qué meses de `ventas.aaaamm` están cargados en la base desplegada, porque el
  selector permite cualquier fecha de inicio.
