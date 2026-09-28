package pe.edu.pucp.sisrap.experimentacion.dominio;

/** Escenarios operativos del diseño experimental. */
public enum EscenarioOperativo {
    OPERACION_DIARIA(1.0),
    SIMULACION_CINCO_DIAS(5.0),
    COLAPSO_LOGISTICO(1.0);

    /** Solo se usa para dimensionar las instancias estáticas. */
    public final double factorHorizonte;

    EscenarioOperativo(double factorHorizonte) {
        this.factorHorizonte = factorHorizonte;
    }
}