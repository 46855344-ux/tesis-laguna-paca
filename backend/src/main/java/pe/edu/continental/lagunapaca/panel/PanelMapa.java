package pe.edu.continental.lagunapaca.panel;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.List;

/**
 * Mapa de la Laguna de Paca listo para dibujar.
 *
 * <p>Hay dos formas de verlo y las dos salen del mismo modelo:</p>
 *
 * <ul>
 *   <li>sin clave de Google, el servidor dibuja el contorno de OpenStreetMap
 *       como SVG ({@link #camino()}); la demo funciona sin internet;</li>
 *   <li>con clave, el navegador carga Google Maps y coloca una marca por
 *       estación con las mismas coordenadas y el mismo veredicto.</li>
 * </ul>
 *
 * <p>La geometría ({@code viewBox} y {@code camino}) es fija y la calcula
 * {@link MapaLagunaService} una sola vez; lo que cambia con cada refresco es la
 * clase de cada punto y el texto de su etiqueta.</p>
 */
public record PanelMapa(

        /** Atributo {@code viewBox} del SVG, con el tamaño del lienzo. */
        String viewBox,

        /** Trazo del contorno del lago, en coordenadas del lienzo. */
        String camino,

        /** Una entrada por estación, en el orden del tablero. */
        List<Punto> puntos,

        /**
         * Clave de Google Maps para el navegador; vacía si no está configurada.
         *
         * <p>Se pinta en el HTML del panel (el navegador la necesita para pedir
         * el mapa), pero <strong>nunca</strong> viaja en el JSON de
         * {@code /panel/datos}: ahí no hace falta y sería una fuga innecesaria.
         * Por eso está marcada con {@link JsonIgnore}.</p>
         */
        @JsonIgnore
        String googleKey
) {

    public String getViewBox() {
        return viewBox;
    }

    public String getCamino() {
        return camino;
    }

    public List<Punto> getPuntos() {
        return puntos;
    }

    @JsonIgnore
    public String getGoogleKey() {
        return googleKey;
    }

    /**
     * Estación ubicada sobre el mapa.
     *
     * @param id      identificador del nodo ({@code N1}…{@code N4})
     * @param x       coordenada horizontal dentro del lienzo SVG
     * @param y       coordenada vertical dentro del lienzo SVG
     * @param lat     latitud real, para el mapa de Google
     * @param lon     longitud real, para el mapa de Google
     * @param clase   sufijo CSS del veredicto ({@code exito}, {@code peligro}, {@code neutra})
     * @param ph      último pH ya formateado, o {@code null} si la estación no reportó
     * @param titulo  texto accesible del punto: estación, pH, veredicto y antigüedad
     */
    public record Punto(String id, int x, int y, double lat, double lon,
                        String clase, String ph, String titulo) {

        public String getId() {
            return id;
        }

        public int getX() {
            return x;
        }

        public int getY() {
            return y;
        }

        public double getLat() {
            return lat;
        }

        public double getLon() {
            return lon;
        }

        public String getClase() {
            return clase;
        }

        public String getPh() {
            return ph;
        }

        public String getTitulo() {
            return titulo;
        }
    }
}
