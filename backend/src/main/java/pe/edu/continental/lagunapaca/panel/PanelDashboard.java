package pe.edu.continental.lagunapaca.panel;

import java.time.Instant;
import java.util.List;

/**
 * Modelo de vista del tablero. No es una entidad ni un DTO de la API: es lo que
 * la pantalla necesita, ya formateado y sin lógica que resolver en React.
 *
 * <p>Se sirve como JSON en {@code /panel/api/datos} (y su alias
 * {@code /panel/datos}); el frontend refresca las tarjetas, el gráfico y el mapa
 * sin recargar la página.</p>
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

        /** Las mismas estaciones ubicadas sobre el contorno de la laguna. */
        PanelMapa mapa,

        /** Puntos de las últimas 24 h, en orden cronológico. */
        List<Punto> serie,

        /** Últimas predicciones del ST-GNN, de la más reciente a la más antigua. */
        List<Prediccion> predicciones
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

    public PanelMapa getMapa() {
        return mapa;
    }

    public List<Punto> getSerie() {
        return serie;
    }

    public List<Prediccion> getPredicciones() {
        return predicciones;
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
     * @param id           identificador del nodo ({@code N1}…{@code N4})
     * @param estado       veredicto
     * @param ultima       última muestra conocida, o {@code null} si nunca reportó
     * @param motivos      mensajes de los motivos de alerta, tal como se guardaron
     * @param atendida     si la última alerta ya fue atendida desde el panel
     * @param atendidaPor  usuario que la atendió, o {@code null}
     * @param atendidaHace antigüedad de la atención en palabras, o {@code null}
     */
    public record Estacion(String id, EstadoMuestra estado, Muestra ultima, List<String> motivos,
                           boolean atendida, String atendidaPor, String atendidaHace) {

        public String getId() {
            return id;
        }

        public EstadoMuestra getEstado() {
            return estado;
        }

        public Muestra getUltima() {
            return ultima;
        }

        public List<String> getMotivos() {
            return motivos;
        }

        public boolean isAtendida() {
            return atendida;
        }

        public String getAtendidaPor() {
            return atendidaPor;
        }

        public String getAtendidaHace() {
            return atendidaHace;
        }

        public boolean isConDatos() {
            return ultima != null;
        }

        /** Texto del veredicto, para las tarjetas y el refresco en vivo. */
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

    /**
     * Predicción ya formateada para la tarjeta.
     *
     * <p>Se muestra <strong>aparte</strong> de las mediciones y con su origen
     * explícito: un valor previsto nunca se pinta como si fuera un sensor. Si el
     * modelo se entrenó con datos sintéticos, la tarjeta lo dice.</p>
     */
    public record Prediccion(
            String estacion,
            int horizonte,
            String phPrevisto,
            String objetivo,
            String generadoHace,
            String modelo,
            String origen,
            String estado,
            String phObservado,
            String error,
            boolean evaluada
    ) {

        public String getEstacion() {
            return estacion;
        }

        public int getHorizonte() {
            return horizonte;
        }

        public String getPhPrevisto() {
            return phPrevisto;
        }

        public String getObjetivo() {
            return objetivo;
        }

        public String getGeneradoHace() {
            return generadoHace;
        }

        public String getModelo() {
            return modelo;
        }

        public String getOrigen() {
            return origen;
        }

        public String getEstado() {
            return estado;
        }

        public String getPhObservado() {
            return phObservado;
        }

        public String getError() {
            return error;
        }

        public boolean isEvaluada() {
            return evaluada;
        }
    }
}
