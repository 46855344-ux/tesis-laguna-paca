package pe.edu.continental.lagunapaca.prediction.service;

/**
 * No hay historia suficiente para armar la ventana que exige el modelo.
 *
 * <p>Es un error de negocio, no una caída: la estación existe, pero todavía no
 * tiene las horas de mediciones necesarias. Se responde 409 para que el panel
 * pueda explicarlo sin fingir una predicción.</p>
 */
public class IncompleteWindowException extends RuntimeException {

    private final String station;
    private final int available;
    private final int needed;

    public IncompleteWindowException(String station, int available, int needed) {
        super("La estación " + station + " solo tiene " + available
                + " muestras y el modelo necesita " + needed
                + ". Sigue midiendo o revisa la frecuencia de muestreo.");
        this.station = station;
        this.available = available;
        this.needed = needed;
    }

    public String getStation() {
        return station;
    }

    public int getAvailable() {
        return available;
    }

    public int getNeeded() {
        return needed;
    }
}
