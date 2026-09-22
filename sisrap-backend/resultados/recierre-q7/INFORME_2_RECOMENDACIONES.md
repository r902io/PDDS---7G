# INFORME 2 — Recomendaciones, cambios al PDF y consideraciones

## 1. Recomendación operativa (con datos actuales)

* **Adoptar GENETICO como planificador titular** para colapso (N100: +6pp cumplimiento, −132k F, mitad de V que SA).
* **Mantener RECOCIDO_SIMULADO como replanificador rápido**: 4-5x más veloz; en N25/N50 el cumplimiento empata y solo pierde por F (retraso fino).
* **Regla práctica:** N≤50 → cualquiera (SA si urge tiempo); N≥80 o ratio demanda/capacidad >1 → GA.

## 2. Qué corregir en el PDF antes de la expo

1. **§3.3/§4.1 — Primaria ambigua.** El texto dice primaria = cumplimiento, pero el veredicto implementado es sobre F. Decidir y escribir UNA: propuesta — primaria = cumplimiento; F = desempate técnico. Con esa regla, N25/N50 pasan a `SIN_DIFERENCIA` y solo N100 es `GANA_GENETICO`. Hoy el informe diría "GA gana todo", contradicho por sus propias medianas.
2. **§3.4 — Factorial 3×3.** Reemplazar "3 escenarios × 3 perfiles" por lo ejecutado (1 variante × 3 tamaños) O ejecutar lo pendiente: diaria (replanificar tras 1 avería), 5D acumulado (stock 23:59 entre días), colapso (rampa hasta N>0>50%). Mínimo exigible: 270-540 corridas con 3 instancias sep-oct para que el mantenimiento sí presione (hoy 0 efecto).
3. **§4.1 — Completar estadística.** Agregar `r=|Z|/√N` (0.1/0.3/0.5), Holm en secundarias, IQR/min/max, conteo de empates 100%-100%, y prueba a presupuesto igual como la de este run (aquí cambió la conclusión de N50 respecto a `final-gaVsSa`).
4. **§2.1 — Sincronizar con Q&A.** El PDF aún ignora: central (27,14)/este (57,27) (ya en código/BD), averías Tipo1-3 + trasvase 30min (no implementado), recarga en cualquier almacén con stock (no implementado), parciales 1h c/u (no implementado), bici 1 turno/moto 1 día/auto 2 días (parcial: TA 2 días en código). Cada ítem debe decir "implementado" o "límite declarado".
5. **§5 — Punto de colapso.** Fijar con este run: primer tamaño con >50% N>0 = **100** (N50 solo 3.3%). Reportar tabla N25/N50/N100 como la del Informe 1 §2.

## 3. Consideraciones necesarias (aunque no estén en el PDF)

* **Presupuesto desigual GA 9060 vs SA ~4700 evals:** toda comparación debe mostrar ambas columnas (F y F-a-igual-presupuesto). Sin esto la conclusión es atacable.
* **Tiempos solo intra-run:** no comparar ms entre `final-gaVsSa` y `recierre-q7` (distinto JDK/CPU).
* **Bloqueos Q7 actuales son heurística** (+2km/V), no A* con U-turn real. Suficiente para expo si se declara; pasar a grafo solo si el jurado lo exige.
* **Mantenimiento bimensual:** generar `mant*.txt` hasta dic-2028 o el validador de presión seguirá en 0. El código ya repite TA 2 días; falta el dato, no el código.
* **Migración `V20260922_1200`:** figura pendiente en `validate` aunque la BD ya tiene los valores (se corrigió fuera de flyway). Aplicar `deploy` (idempotente) para dejar historia limpia.
* **`mvn test` rojo pre-existente** (`contextLoads` sin DB en test). No es por estos cambios; para expo correr `compile` + `CheckBloqueos` + experimento basta.
* **Próximo paso mínimo:** 1 run sep-oct (mantenimiento real) + Wilcoxon sobre cumplimiento + `r`/Holm. Con eso el PDF queda defendible sin tocar recarga/parciales/turnos (se dejan como límites).
