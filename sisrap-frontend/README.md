# sisrap-frontend · Visualizador de SisRap

Componente visualizador del planificador de rutas de SisRap (PaqRap). Muestra en tiempo real la corrida
que ejecuta `sisrap-backend` sobre la retícula de 70 × 50 km. Cualquier dispositivo de la red que abra la
URL ve la misma corrida: la sesión que la inicia es la **controladora** y las demás son **observadoras**.

- React 19 + TypeScript + Vite + Tailwind. El estado global usa React Context y `useReducer`.
- Sin librerías de iconos: los iconos son SVG en línea (`src/components/iconos/`).
- Sin dependencias de internet en tiempo de ejecución: ni CDN ni fuentes externas.
- Sin datos simulados: si el backend no responde, la interfaz lo dice.

## Requisitos

- Node.js 20 o superior.
- Un `sisrap-backend` en ejecución (local o en otra máquina).

## Desarrollo

```bash
npm install
cp .env.example .env.local      # ajustar VITE_BACKEND_URL si el backend no está en localhost:8080
npm run dev                     # http://localhost:3000 y http://<ip-de-esta-máquina>:3000
```

Vite redirige `/api` (incluido el flujo SSE `/api/simulacion/eventos`) a `VITE_BACKEND_URL`, así que
el navegador nunca necesita CORS.

| Comando           | Uso                                                       |
|-------------------|-----------------------------------------------------------|
| `npm run dev`     | Servidor de desarrollo con proxy, abierto a la red.       |
| `npm run build`   | Genera `dist/` (HTML, JS y CSS estáticos, rutas relativas).|
| `npm run preview` | Sirve `dist/` en el puerto 4173 con el mismo proxy.       |
| `npm run lint`    | Verificación de tipos (`tsc --noEmit`).                   |

## Despliegue con una sola URL

El front usa navegación por fragmento (`#/operacion`, `#/mantenimiento/vehiculos`...). El servidor
solo recibe `/` e `index.html`, así que **ninguna ruta del front puede devolver 404** y no hace falta
una ruta de respaldo en el servidor.

### Opción A — el propio backend sirve el front (recomendada)

Spring Boot sirve como estáticos cualquier carpeta indicada en `spring.web.resources.static-locations`
y publica su `index.html` en `/`. No requiere cambiar código del backend:

```bash
npm run build
# En la máquina del backend, al arrancarlo:
SPRING_WEB_RESOURCES_STATIC_LOCATIONS=file:/ruta/a/sisrap-frontend/dist/,classpath:/static/
```

Alternativamente, copiar el contenido de `dist/` a `sisrap-backend/src/main/resources/static/`
antes de empaquetar el JAR. Con Docker, montar `dist/` como volumen y pasar la variable anterior en
`.env`. Así el front y la API quedan en el mismo origen (p. ej. `http://<servidor>:8080/`).

### Opción B — `vite preview` como servidor del front

```bash
npm run build
VITE_BACKEND_URL=http://1inf54-981-7g.inf.pucp.edu.pe:8080 npm run preview   # http://<esta-máquina>:4173
```

## Estructura

```
src/
  types/api.ts             Contrato: records del backend, campo por campo
  services/                apiClient, sesión anónima, simulación (REST + SSE), catálogos
  estado/                  SimulacionContext (useReducer) y navegación por fragmento
  hooks/useMapCanvas.ts    Motor de dibujo del mapa (Canvas)
  components/mapa/         Mapa, geometría de rutas, panel de detalle
  components/iconos/       Iconos SVG en línea y formas compartidas con el Canvas
  pages/                   Selector, Operación en curso, Resultado, Mantenimiento
  utilitarios/             Formato de fechas/cifras/coordenadas, escenarios, tipo de vehículo (TTNN)
docs/referencia/           Prototipo de diseño v03
BRECHAS_BACKEND.md         Datos que la interfaz necesita y el backend aún no entrega
```

## Unidades y envíos en la operación

- **Unidades de transporte (E14 a E17):** salen de los campos `horaSalida`, `horaLlegadaEstimada`,
  `distanciaRecorridaKm` y `distanciaPorRecorrerKm` del snapshot. La llegada y la distancia por recorrer se miden
  hasta el destino inmediato. Las unidades sin dato van al final en ambos sentidos.
- **Envíos (E31 a E33):** `GET /api/simulacion/envios?ventana=TODOS|ENTREGADOS|POR_ENTREGAR&horas=4&buscar=&pagina=0&tamanio=50`.
  Con `idPedido=` devuelve un envío completo, que es lo que usa el resaltado del mapa.
- **Entregados en las últimas x horas:** cuenta un pedido si tiene al menos una asignación entregada en la ventana,
  lo marca "Entrega parcial" si le faltan fracciones y suma solo los productos entregados en la ventana.
- **Por entregar en las siguientes x horas:** usa la llegada estimada. Si los paquetes no tienen unidad, usa el plazo
  y los muestra "Sin asignar".
- **Ruta real y estimada:** la ruta real es lo recorrido más el tramo en curso. Las paradas siguientes aún no tienen
  camino calculado: se estiman por distancia Manhattan, sin bloqueos, y se dibujan con línea discontinua.
- **Identificadores de producto:** el caso no los define. Cada paquete recibe un número estable entre 1 y la cantidad
  del pedido, con formato `idPedido-n`, y lo conserva si cambia de unidad.
- **Alcance:** las asignaciones viven en memoria (`RegistroEnvios`), por corrida. Se conservan al terminar la corrida
  hasta iniciar otra, pero no sobreviven a un reinicio del backend.

## Reglas que respeta la interfaz

- Ningún control de reproducción (pausa, reanudar, velocidad, barras de tiempo). Solo iniciar y detener.
- Los bloqueos viales se pueden crear y cancelar desde *Mantenimiento → Bloqueos*. El formulario valida fechas, límites de la ciudad y tramos horizontales/verticales.
- El registro de averías simuladas sigue pendiente de cerrar la regla de negocio durante una corrida.
- El primer pedido fuera de plazo (`pedidos.retrasados > 0`) se presenta como colapso logístico.
- Las fechas del backend son `LocalDateTime` sin zona: se muestran tal cual llegan.
- El tipo de vehículo se deduce del prefijo del código: `TA` auto, `TM` moto, `TB` bicicleta.
