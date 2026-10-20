package pe.edu.continental.lagunapaca.panel;

/**
 * Filtros de la pantalla de alertas, tal como llegan por la URL.
 *
 * <p>Igual que en el historial, se guardan como texto para poder repintarlos
 * aunque no sean válidos: si alguien teclea {@code 2026-13-45} el campo conserva
 * el valor junto al aviso, en vez de vaciarse sin explicación.</p>
 *
 * @param estacion {@code N1}…{@code N4}, o vacío para todas
 * @param estado   {@code pendientes}, {@code atendidas} o {@code todas}
 * @param desde    fecha inicial {@code AAAA-MM-DD}, o vacío
 * @param hasta    fecha final {@code AAAA-MM-DD}, o vacío
 * @param pagina   página solicitada, base 0 (la interfaz muestra base 1)
 */
public record FiltrosAlertas(
        String estacion,
        String estado,
        String desde,
        String hasta,
        int pagina
) {

    /** Estado por defecto: lo primero que hay que ver es lo que sigue pendiente. */
    public static final String PENDIENTES = "pendientes";
    public static final String ATENDIDAS = "atendidas";
    public static final String TODAS = "todas";

    /**
     * Un estado desconocido cae en {@code pendientes} en vez de romper: la
     * pantalla es de gestión, y su valor por defecto es lo que exige acción.
     */
    public static String estadoValido(String estado) {
        if (ATENDIDAS.equalsIgnoreCase(estado)) {
            return ATENDIDAS;
        }
        if (TODAS.equalsIgnoreCase(estado)) {
            return TODAS;
        }
        return PENDIENTES;
    }

    public String getEstacion() {
        return estacion;
    }

    public String getEstado() {
        return estado;
    }

    public String getDesde() {
        return desde;
    }

    public String getHasta() {
        return hasta;
    }

    public int getPagina() {
        return pagina;
    }
}
