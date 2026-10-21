package pe.edu.continental.lagunapaca.station;

import java.util.List;

/**
 * Catálogo de estaciones de la laguna.
 *
 * <p>Los identificadores coinciden con el {@code node_id} del ESP32 y con las
 * coordenadas de {@code ia-model/lagunapaca_gnn/stations.py}. Es una constante
 * porque todavía no existe una entidad {@code station} en la base de datos; el
 * día que exista, esta clase será la que consulte la tabla y el resto del
 * código no tendrá que cambiar.</p>
 */
public final class StationCatalog {

    /** Orden estable de las estaciones (el mismo del tablero y del grafo). */
    public static final List<String> IDS = List.of("N1", "N2", "N3", "N4");

    private StationCatalog() {
    }

    public static boolean exists(String id) {
        return id != null && IDS.contains(id);
    }
}
