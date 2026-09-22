package pe.edu.pucp.sisrap.experimentacion.dominio;

/**
 * Los tres escenarios de evaluación definidos en el informe "Diseño de Experimento" (sección 3.4):
 * operación día a día, simulación acumulada de cinco días y colapso logístico.
 *
 * @param factorHorizonte cuánto se amplía el horizonte de pedidos de la instancia respecto de un día
 *                        de operación normal (la operación diaria es la unidad base; la simulación de
 *                        cinco días multiplica ese horizonte por 5; el colapso mantiene el horizonte
 *                        diario pero incrementa la presión de forma progresiva entre instancias)
 */
public enum EscenarioOperativo {
    OPERACION_DIARIA(1.0),
    SIMULACION_CINCO_DIAS(5.0),
    COLAPSO_LOGISTICO(1.0);

    public final double factorHorizonte;

    EscenarioOperativo(double factorHorizonte) {
        this.factorHorizonte = factorHorizonte;
    }
}