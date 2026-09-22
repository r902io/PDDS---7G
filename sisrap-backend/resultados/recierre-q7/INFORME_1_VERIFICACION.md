# INFORME 1 — Verificación técnica del re-run `recierre-q7` vs PDF de experimento

Fecha: 2026-09-22. Código: central (27,14) + este (57,27), bloqueos Q7 (+2km/V), mantenimiento TA 2 días.
Plan: `BASE × [25,50,100] × 3 instancias × 15 reps × 2 alg = 270 corridas`, semillas 777-791, pareado perfecto (45 pares/tamaño), 0 fallos de verificación.

## 1. Cumplimiento PDF §2–§4

| Exigencia PDF | Estado | Evidencia |
|---|---|---|
| Pareado misma instancia/semilla, 30→15 reps | ✅ | 45 pares/tamaño, 0 parejas incompletas (`corridas.csv`) |
| Wilcoxon bilateral α=0.05 | ✅ | `comparacion.csv`, exacta n≤50 / normal+corrección |
| Métrica primaria cumplimiento + secundarias | ⚠️ parcial | Se miden (cumplimiento, prioritarios, R, N, V, costo, distancia, utilización, tiempo), pero el **veredicto solo testea F** |
| FO `T+β1R+β2N+β3V`, `β=100/1000/10000` | ✅ | Infactible siempre pierde; N100 lo confirma (F 440k vs 572k) |
| `servicioDentroPlazo=false` (Q11: 1h fuera del plazo) | ✅ | Retraso usa llegada; no romper |
| Flota 37 / almacenes Q5-CAMBIO / plazos 4-36h | ✅ | `flota.csv`, `almacenes.csv` (27,14 / 57,27) |
| 3 escenarios × 3 perfiles = 540 mín | ❌ | 1 variante BASE × 3 tamaños = 270. Sin diaria/5D/colapso formal ni Normal/Alta/Crítica |
| Replanificación (tiempo + tasa) | ❌ | No se mide; `replanificar()` existe pero `CorredorExperimento` no lo llama |
| Bloqueos afectan (Q7 U-turn) | ✅ nuevo | +2km por tramo afectado, V si entrega en nodo bloqueado |
| Recarga cualquier almacén (Q10), parciales (Q13-14), turnos (Q12), velocidad caliente (Q16) | ❌ | 1 salida/vehículo, indivisible, sin turnos; declarado en manifiesto |
| `r`, Holm, IQR, permutación | ❌ | Solo `p`, victorias, empates |
| Piloto separado | ⚠️ | `ga-etapa1/sa-etapa2` existen pero sin aislamiento formal |

## 2. Resultados (tienen sentido)

* **Colapso:** N25 0% con N>0 → N50 3/90 (3.3%) → N100 90/90 (100%). Punto de colapso = 100, coherente con ratio demanda/capacidad 0.3→0.65→1.3.
* **Cumplimiento:** N25 57.3/57.4 (med 56/56) → N50 27.3/27.3 (med 28/28) → N100 GA 13.1 (med 13) vs SA 7.2 (med 6). Degradación monótona esperada.
* **F y Wilcoxon:** GANA_GENETICO en los 3 tamaños, también a presupuesto igual (N25 p=0.016, N50 p=1e-5, N100 p≈0). Victorias 28-8 (9 empates), 36-9, 45-0.
* **Bloqueos Q7 muerden solo en colapso:** V>0 en N100 (GA 15/45, SA 30/45), V=0 en N25/N50. SA duplica infactibilidad bajo presión.
* **Costo operativo:** SA 4-5x más rápido (231 vs 1028ms N25; 763 vs 2788ms N100) con ~mitad de evaluaciones (4374-4920 vs 9060). A igual presupuesto GA sigue ganando aquí (difiere de `final-gaVsSa` N50, que era empate con 300 pares; con 45 pares el poder es menor — ver §3).
* **Mantenimiento:** sin efecto (instancias ene-26, mant sep-oct-26; 37/37 disponibles). La extensión TA→2 días quedó en código pero no se ejercita.

## 3. Advertencias estadísticas

* 45 pares (no 300): poder suficiente para efectos grandes (N100), justo para N25/N50. No reducir más.
* Tiempos absolutos no comparables con `final-gaVsSa` (JDK 23 vs 21, 12 vs 16 cores, carga distinta). Comparar solo dentro del mismo run.
* Decisión por F ≠ decisión por cumplimiento: en N25/N50 las medianas de cumplimiento empatan (56/56, 28/28); el veredicto sale de F (retraso+T). Si la primaria es cumplimiento, N25/N50 son **empate operativo**.
