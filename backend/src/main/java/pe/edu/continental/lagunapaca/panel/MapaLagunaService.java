package pe.edu.continental.lagunapaca.panel;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Dibuja el contorno de la laguna y ubica las estaciones sobre él.
 *
 * <p>El archivo {@code geo/laguna-paca.json} lo genera
 * {@code backend/tools/fetch_laguna.py} a partir del polígono de OpenStreetMap
 * (way 26947746) y de las estaciones de {@code ia-model}: el panel no guarda
 * coordenadas propias, así que el mapa y el grafo de la GNN no pueden
 * discrepar.</p>
 *
 * <p>La proyección es equirectangular corregida por el coseno de la latitud.
 * Sin esa corrección un grado de longitud se dibujaría igual que uno de
 * latitud, y a 11° sur el lago saldría estirado horizontalmente.</p>
 */
@Service
public class MapaLagunaService {

    /** Recurso generado; ver {@code backend/tools/fetch_laguna.py}. */
    private static final String RECURSO = "geo/laguna-paca.json";

    /**
     * Lienzo del SVG. El alto es menor que el ancho porque la laguna es más
     * ancha que profunda: así el mapa ocupa la tarjeta sin dejar franjas vacías.
     */
    private static final int ANCHO = 720;
    private static final int ALTO = 460;

    /** Margen interior del lienzo, en píxeles del SVG. */
    private static final int MARGEN = 30;

    private final String viewBox;
    private final String camino;
    private final Map<String, PuntoGeo> estaciones;
    private final Proyeccion proyeccion;

    public MapaLagunaService(ObjectMapper objectMapper) {
        Archivo archivo = leer(objectMapper);
        this.viewBox = "0 0 " + ANCHO + " " + ALTO;
        this.estaciones = indexar(archivo.estaciones());
        this.proyeccion = Proyeccion.entre(archivo.laguna(), archivo.estaciones());
        this.camino = trazar(archivo.laguna(), proyeccion);
    }

    /**
     * Ubica cada estación del tablero sobre el mapa, con el veredicto que ya
     * decidió el backend.
     *
     * @throws IllegalStateException si una estación del tablero no tiene
     *                               coordenadas en el GeoJSON: es un error de
     *                               datos, no algo que deba pasar en silencio
     */
    public PanelMapa construir(List<PanelDashboard.Estacion> estacionesTablero) {
        List<PanelMapa.Punto> puntos = estacionesTablero.stream()
                .map(this::punto)
                .toList();
        return new PanelMapa(viewBox, camino, puntos);
    }

    private PanelMapa.Punto punto(PanelDashboard.Estacion estacion) {
        PuntoGeo geo = estaciones.get(estacion.getId());
        if (geo == null) {
            throw new IllegalStateException(
                    "La estación " + estacion.getId() + " no tiene coordenadas en " + RECURSO);
        }

        PanelDashboard.Muestra ultima = estacion.getUltima();
        String ph = ultima == null ? null : ultima.getPh();

        // El texto accesible dice lo mismo que la tarjeta, sin obligar a leer el
        // mapa: "N2 · pH 6.12 · Alerta ECA-Agua · hace 2 minutos".
        StringBuilder titulo = new StringBuilder(estacion.getId());
        if (ph != null) {
            titulo.append(" · pH ").append(ph);
        }
        titulo.append(" · ").append(estacion.getEtiqueta());
        if (ultima != null) {
            titulo.append(" · ").append(ultima.getHace());
        }

        return new PanelMapa.Punto(
                estacion.getId(),
                redondear(proyeccion.x(geo.lon())),
                redondear(proyeccion.y(geo.lat())),
                estacion.getClase(),
                ph,
                titulo.toString());
    }

    private static Archivo leer(ObjectMapper objectMapper) {
        try (InputStream entrada = new ClassPathResource(RECURSO).getInputStream()) {
            return objectMapper.readValue(entrada, Archivo.class);
        } catch (IOException excepcion) {
            throw new IllegalStateException(
                    "No se pudo leer " + RECURSO + "; regenera el archivo con "
                            + "backend/tools/fetch_laguna.py", excepcion);
        }
    }

    private static Map<String, PuntoGeo> indexar(List<PuntoGeo> puntos) {
        Map<String, PuntoGeo> indice = new LinkedHashMap<>();
        for (PuntoGeo punto : puntos) {
            indice.put(punto.id(), punto);
        }
        return Map.copyOf(indice);
    }

    /** Trazo del contorno: "M x y L x y … Z", con un decimal. */
    private static String trazar(List<List<Double>> anillo, Proyeccion proyeccion) {
        StringBuilder camino = new StringBuilder(anillo.size() * 12);
        for (List<Double> par : anillo) {
            camino.append(camino.isEmpty() ? "M " : " L ");
            camino.append(decimal(proyeccion.x(par.get(0))))
                    .append(' ')
                    .append(decimal(proyeccion.y(par.get(1))));
        }
        return camino.append(" Z").toString();
    }

    private static int redondear(double valor) {
        return (int) Math.round(valor);
    }

    private static String decimal(double valor) {
        return String.format(Locale.ROOT, "%.1f", valor);
    }

    /**
     * Escala las coordenadas geográficas al lienzo.
     *
     * <p>Se calcula con la caja que encierra el lago <em>y</em> las estaciones,
     * no solo el lago: un punto en la orilla quedaría pegado al borde o fuera si
     * se ajustara únicamente al contorno.</p>
     */
    private record Proyeccion(double latMax, double lonMin, double coseno,
                              double escala, double sobraX, double sobraY) {

        static Proyeccion entre(List<List<Double>> anillo, Collection<PuntoGeo> estaciones) {
            double latMin = Double.MAX_VALUE;
            double latMax = -Double.MAX_VALUE;
            double lonMin = Double.MAX_VALUE;
            double lonMax = -Double.MAX_VALUE;

            for (List<Double> par : anillo) {
                lonMin = Math.min(lonMin, par.get(0));
                lonMax = Math.max(lonMax, par.get(0));
                latMin = Math.min(latMin, par.get(1));
                latMax = Math.max(latMax, par.get(1));
            }
            for (PuntoGeo estacion : estaciones) {
                lonMin = Math.min(lonMin, estacion.lon());
                lonMax = Math.max(lonMax, estacion.lon());
                latMin = Math.min(latMin, estacion.lat());
                latMax = Math.max(latMax, estacion.lat());
            }

            double coseno = Math.cos(Math.toRadians((latMin + latMax) / 2));
            double anchoUtil = ANCHO - 2.0 * MARGEN;
            double altoUtil = ALTO - 2.0 * MARGEN;

            double escala = Math.min(
                    anchoUtil / ((lonMax - lonMin) * coseno),
                    altoUtil / (latMax - latMin));

            return new Proyeccion(latMax, lonMin, coseno, escala,
                    (anchoUtil - (lonMax - lonMin) * coseno * escala) / 2,
                    (altoUtil - (latMax - latMin) * escala) / 2);
        }

        double x(double longitud) {
            return MARGEN + sobraX + (longitud - lonMin) * coseno * escala;
        }

        /** El eje Y del SVG crece hacia abajo; la latitud, hacia el norte. */
        double y(double latitud) {
            return MARGEN + sobraY + (latMax - latitud) * escala;
        }
    }

    /** Documento generado. Solo se leen los campos que el mapa necesita. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Archivo(
            @JsonProperty("laguna") List<List<Double>> laguna,
            @JsonProperty("estaciones") List<PuntoGeo> estaciones) {
    }

    private record PuntoGeo(
            @JsonProperty("id") String id,
            @JsonProperty("nombre") String nombre,
            @JsonProperty("lat") double lat,
            @JsonProperty("lon") double lon) {
    }
}
