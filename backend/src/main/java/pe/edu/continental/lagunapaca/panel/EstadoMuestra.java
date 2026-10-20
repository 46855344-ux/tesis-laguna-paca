package pe.edu.continental.lagunapaca.panel;

/**
 * Veredicto visible de una muestra. Vive en un solo lugar porque lo usan dos
 * pantallas (el tablero y el historial) y los textos no deben divergir.
 *
 * <p>No es lógica de negocio: la alerta ya la decidió {@code EcaWaterQualityRule}
 * al ingerir. Esto solo traduce el booleano persistido a texto y a una clase
 * CSS.</p>
 */
public enum EstadoMuestra {

    SIN_DATOS("Sin datos", "neutra"),
    DENTRO("Dentro del rango", "exito"),
    ALERTA("Alerta ECA-Agua", "peligro");

    private final String etiqueta;
    private final String clase;

    EstadoMuestra(String etiqueta, String clase) {
        this.etiqueta = etiqueta;
        this.clase = clase;
    }

    /** Texto en español que ve la persona. */
    public String getEtiqueta() {
        return etiqueta;
    }

    /** Sufijo de la clase CSS ({@code etiqueta--exito}, etc.). */
    public String getClase() {
        return clase;
    }
}
