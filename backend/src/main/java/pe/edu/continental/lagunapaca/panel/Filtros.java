package pe.edu.continental.lagunapaca.panel;

/**
 * Filtros del historial tal como llegan por la URL.
 *
 * <p>Se guardan como texto, no como tipos ya convertidos, porque el panel
 * necesita <em>volver a pintarlos</em> en el formulario exactamente como los
 * escribió la persona, incluso cuando no son válidos: si alguien teclea
 * {@code 2026-13-45}, el campo debe conservar ese valor junto al aviso, no
 * vaciarse sin explicación.</p>
 *
 * @param estacion   {@code N1}…{@code N4}, o vacío para todas
 * @param desde      fecha inicial {@code AAAA-MM-DD}, o vacío
 * @param hasta      fecha final {@code AAAA-MM-DD}, o vacío
 * @param soloAlertas si solo se quieren muestras con alerta ECA-Agua
 * @param motivo     texto buscado dentro del mensaje de los motivos
 * @param pagina     página solicitada, base 0 (la interfaz muestra base 1)
 */
public record Filtros(
        String estacion,
        String desde,
        String hasta,
        boolean soloAlertas,
        String motivo,
        int pagina
) {

    public String getEstacion() {
        return estacion;
    }

    public String getDesde() {
        return desde;
    }

    public String getHasta() {
        return hasta;
    }

    public boolean isSoloAlertas() {
        return soloAlertas;
    }

    public String getMotivo() {
        return motivo;
    }

    public int getPagina() {
        return pagina;
    }
}
