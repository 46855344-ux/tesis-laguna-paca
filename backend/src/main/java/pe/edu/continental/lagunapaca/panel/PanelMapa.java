package pe.edu.continental.lagunapaca.panel;

import java.util.List;

/**
 * Mapa de la Laguna de Paca listo para dibujar.
 *
 * <p>El contorno viene de OpenStreetMap y se guarda como GeoJSON en el
 * repositorio, así que el tablero no depende de teselas ni de conexión: el SVG
 * se dibuja igual que el gráfico de series, sin librerías externas.</p>
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
        List<Punto> puntos
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

    /**
     * Estación ubicada sobre el mapa.
     *
     * @param id      identificador del nodo ({@code N1}…{@code N4})
     * @param x       coordenada horizontal dentro del lienzo
     * @param y       coordenada vertical dentro del lienzo
     * @param clase   sufijo CSS del veredicto ({@code exito}, {@code peligro}, {@code neutra})
     * @param ph      último pH ya formateado, o {@code null} si la estación no reportó
     * @param titulo  texto accesible del punto: estación, pH, veredicto y antigüedad
     */
    public record Punto(String id, int x, int y, String clase, String ph, String titulo) {

        public String getId() {
            return id;
        }

        public int getX() {
            return x;
        }

        public int getY() {
            return y;
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
