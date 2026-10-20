package pe.edu.continental.lagunapaca.panel;

import java.time.Instant;
import java.util.List;

/**
 * Modelo de vista del tablero. No es una entidad ni un DTO de la API: es lo que
 * la pantalla necesita, ya formateado y sin lógica que resolver en la plantilla.
 *
 * <p>Se usa para dos salidas del mismo cálculo: el HTML renderizado en el
 * servidor (que funciona sin JavaScript) y el JSON de {@code /panel/datos}, que
 * refresca las tarjetas y dibuja el gráfico en vivo.</p>
 *
 * <p>Los valores numéricos de {@link Muestra} ya vienen como texto con el
 * formato de la interfaz. Así el servidor y el navegador no tienen dos reglas de
 * redondeo distintas que se puedan desincronizar.</p>
 */
public record PanelDashboard(

        /** Momento en que se construyó esta vista. */
        Instant actualizadoEn,

        /** La misma hora, ya formateada en la zona de la laguna. */
        String horaActualizacion,

        /** Muestras dentro de la ventana de 24 h. */
        int muestras,

        /** De esas muestras, cuántas tienen alerta ECA-Agua. */
        int alertas,

        List<Estacion> estaciones,

        /** Puntos de las últimas 24 h, en orden cronológico. */
        List<Punto> serie
) {

    public Instant getActualizadoEn() {
        return actualizadoEn;
    }

    public String getHoraActualizacion() {
        return horaActualizacion;
    }

    public int getMuestras() {
        return muestras;
    }

    public int getAlertas() {
        return alertas;
    }

    public List<Estacion> getEstaciones() {
        return estaciones;
    }

    public List<Punto> getSerie() {
        return serie;
    }

    /** Estaciones con al menos una muestra alguna vez (para el KPI "x / 4"). */
    public long getEstacionesConDatos() {
        return estaciones.stream().filter(Estacion::isConDatos).count();
    }

    /** Sin muestras en 24 h: el gráfico muestra su estado vacío. */
    public boolean isSinDatos() {
        return serie.isEmpty();
    }

    /**
     * Estado de una estación según la regla ECA-Agua que ya decidió el backend al
     * ingerir la muestra. El panel no reevalúa el pH: solo pinta el veredicto.
     *
     * @param id      identificador del nodo ({@code N1}…{@code N4})
     * @param estado  veredicto
     * @param ultima  última muestra conocida, o {@code null} si nunca reportó
     * @param motivos mensajes de los motivos de alerta, tal como se guardaron
     */
    public record Estacion(String id, Estado estado, Muestra ultima, List<String> motivos) {

        public String getId() {
            return id;
        }

        public Estado getEstado() {
            return estado;
        }

        public Muestra getUltima() {
            return ultima;
        }

        public List<String> getMotivos() {
            return motivos;
        }

        public boolean isConDatos() {
            return ultima != null;
        }

        /** Texto del veredicto, para el HTML y para el refresco en vivo. */
        public String getEtiqueta() {
            return estado.getEtiqueta();
        }

        /** Sufijo de clase CSS del veredicto, expuesto para que el JS no lo replique. */
        public String getClase() {
            return estado.getClase();
        }
    }

    /**
     * Última muestra de una estación, con los valores ya formateados y la
     * antigüedad en palabras ("hace 3 minutos") para que una tarjeta vieja no
     * parezca un dato de ahora.
     */
    public record Muestra(
            String ph,
            String turbidez,
            String temperatura,
            String tds,
            Instant registradaEn,
            String hora,
            String fecha,
            String hace
    ) {

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

        public Instant getRegistradaEn() {
            return registradaEn;
        }

        public String getHora() {
            return hora;
        }

        public String getFecha() {
            return fecha;
        }

        public String getHace() {
            return hace;
        }
    }

    /**
     * Punto del gráfico. Conserva los números (no el texto) porque las escalas
     * las calcula el navegador.
     */
    public record Punto(
            String estacion,
            Instant instante,
            double ph,
            double turbidez,
            double temperatura,
            double tds
    ) {
    }

    /** Veredicto visible de una estación. Solo existen estos tres. */
    public enum Estado {

        SIN_DATOS("Sin datos", "neutra"),
        DENTRO("Dentro del rango", "exito"),
        ALERTA("Alerta ECA-Agua", "peligro");

        private final String etiqueta;
        private final String clase;

        Estado(String etiqueta, String clase) {
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
}
