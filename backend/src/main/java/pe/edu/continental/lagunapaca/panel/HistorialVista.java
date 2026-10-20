package pe.edu.continental.lagunapaca.panel;

import java.util.List;

/**
 * Modelo de vista del historial: una página de muestras ya formateada, los
 * filtros aplicados y lo necesario para dibujar la paginación.
 *
 * <p>Como en el tablero, los valores numéricos vienen como texto con el formato
 * de la interfaz y el veredicto ECA-Agua es el que ya quedó guardado al ingerir.</p>
 */
public record HistorialVista(

        /** Filas de esta página, de la más reciente a la más antigua. */
        List<Fila> filas,

        /** Filtros aplicados, para volver a pintarlos en el formulario. */
        Filtros filtros,

        /** Página actual, base 1 (lo que ve la persona). */
        int pagina,

        int totalPaginas,

        /** Total de muestras que cumplen los filtros, no solo las de esta página. */
        long total,

        /** Número de la primera fila mostrada (1 si hay resultados). */
        int desdeFila,

        /** Número de la última fila mostrada. */
        int hastaFila,

        /** Números de página a mostrar; los huecos se dibujan como "…". */
        List<Integer> paginas,

        /** Aviso sobre filtros ignorados o inválidos, o {@code null}. */
        String aviso
) {

    public List<Fila> getFilas() {
        return filas;
    }

    public Filtros getFiltros() {
        return filtros;
    }

    public int getPagina() {
        return pagina;
    }

    public int getTotalPaginas() {
        return totalPaginas;
    }

    public long getTotal() {
        return total;
    }

    public int getDesdeFila() {
        return desdeFila;
    }

    public int getHastaFila() {
        return hastaFila;
    }

    public List<Integer> getPaginas() {
        return paginas;
    }

    public String getAviso() {
        return aviso;
    }

    public boolean isHayResultados() {
        return total > 0;
    }

    public boolean isHayAviso() {
        return aviso != null && !aviso.isBlank();
    }

    public boolean isHayVariasPaginas() {
        return totalPaginas > 1;
    }

    /** Una fila de la tabla: una muestra con sus valores ya formateados. */
    public record Fila(
            long id,
            String fecha,
            String hora,
            String estacion,
            String ph,
            String turbidez,
            String temperatura,
            String tds,
            EstadoMuestra estado,
            List<String> motivos
    ) {

        public long getId() {
            return id;
        }

        public String getFecha() {
            return fecha;
        }

        public String getHora() {
            return hora;
        }

        public String getEstacion() {
            return estacion;
        }

        public String getPh() {
            return ph;
        }

        public String getTurbidez() {
            return turbidez;
        }

        public String getTemperatura() {
            return temperatura;
        }

        public String getTds() {
            return tds;
        }

        public EstadoMuestra getEstado() {
            return estado;
        }

        public List<String> getMotivos() {
            return motivos;
        }
    }
}
