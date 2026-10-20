package pe.edu.continental.lagunapaca.panel;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.continental.lagunapaca.telemetry.domain.AlertReason;
import pe.edu.continental.lagunapaca.telemetry.domain.WaterQualitySample;
import pe.edu.continental.lagunapaca.telemetry.domain.WaterQualitySampleRepository;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Arma el modelo del tablero a partir de la telemetría ya persistida.
 *
 * <p>Lee del repositorio, no de la API REST: el panel se autentica con sesión y
 * no tiene por qué cargar un JWT en el navegador. Tampoco reevalúa la regla
 * ECA-Agua; usa el campo {@code alert} que guardó la ingesta, para que solo haya
 * una fuente de verdad.</p>
 */
@Service
public class PanelDashboardService {

    /** Zona de la laguna: las horas del panel son las que ve el guardaparque. */
    static final ZoneId ZONA = ZoneId.of("America/Lima");

    /** Ventana de la serie y de los contadores del tablero. */
    static final Duration VENTANA = Duration.ofHours(24);

    /**
     * Tope defensivo de puntos. Con una muestra cada 15 min son 96 por estación
     * al día; el tope solo protege contra un nodo que publique sin control.
     */
    static final int MAX_PUNTOS = 5000;

    /**
     * Estaciones de la laguna. Coinciden con el {@code node_id} del ESP32 y con
     * las coordenadas de {@code ia-model/lagunapaca_gnn/stations.py}.
     *
     * <p>Es una constante porque todavía no existe una entidad {@code Estacion};
     * cuando exista, esta lista saldrá de la base de datos sin tocar la vista.</p>
     */
    static final List<String> ESTACIONES = List.of("N1", "N2", "N3", "N4");

    private static final DateTimeFormatter HORA =
            DateTimeFormatter.ofPattern("HH:mm").withZone(ZONA);
    private static final DateTimeFormatter HORA_CON_SEGUNDOS =
            DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZONA);
    private static final DateTimeFormatter FECHA_HORA =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(ZONA);

    private final WaterQualitySampleRepository repository;
    private final MapaLagunaService mapaService;
    private final Clock clock;

    @Autowired
    public PanelDashboardService(WaterQualitySampleRepository repository,
                                 MapaLagunaService mapaService) {
        this(repository, mapaService, Clock.systemUTC());
    }

    /** Constructor con reloj explícito: permite fijar el "ahora" en los tests. */
    public PanelDashboardService(WaterQualitySampleRepository repository,
                                 MapaLagunaService mapaService, Clock clock) {
        this.repository = repository;
        this.mapaService = mapaService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PanelDashboard construir() {
        Instant ahora = clock.instant();

        List<WaterQualitySample> recientes = repository
                .findByRecordedAtGreaterThanEqualOrderByRecordedAtAsc(
                        ahora.minus(VENTANA), PageRequest.of(0, MAX_PUNTOS));

        List<PanelDashboard.Estacion> estaciones = ESTACIONES.stream()
                .map(id -> estacion(id, ahora))
                .toList();

        List<PanelDashboard.Punto> serie = recientes.stream()
                .map(PanelDashboardService::punto)
                .toList();

        int alertas = (int) recientes.stream()
                .filter(WaterQualitySample::isAlert)
                .count();

        return new PanelDashboard(ahora, HORA_CON_SEGUNDOS.format(ahora),
                recientes.size(), alertas, estaciones, mapaService.construir(estaciones), serie);
    }

    /**
     * La tarjeta usa la última muestra <em>sin límite de antigüedad</em>: si una
     * estación lleva tres días callada, es más honesto mostrar su último dato con
     * "hace 3 días" que ocultarlo como si nunca hubiera medido.
     */
    private PanelDashboard.Estacion estacion(String id, Instant ahora) {
        return repository.findFirstByNodeIdOrderByRecordedAtDesc(id)
                .map(ultima -> new PanelDashboard.Estacion(
                        id,
                        ultima.isAlert()
                                ? EstadoMuestra.ALERTA
                                : EstadoMuestra.DENTRO,
                        muestra(ultima, ahora),
                        ultima.getAlertReasons().stream().map(AlertReason::getMessage).toList(),
                        ultima.isAcknowledged(),
                        ultima.getAcknowledgedBy(),
                        ultima.isAcknowledged()
                                ? FormatosPanel.hace(ultima.getAcknowledgedAt(), ahora)
                                : null))
                .orElseGet(() -> new PanelDashboard.Estacion(
                        id, EstadoMuestra.SIN_DATOS, null, List.of(), false, null, null));
    }

    private static PanelDashboard.Muestra muestra(WaterQualitySample muestra, Instant ahora) {
        return new PanelDashboard.Muestra(
                FormatosPanel.formatear(muestra.getPh(), 2),
                FormatosPanel.formatear(muestra.getTurbidityNtu(), 1),
                FormatosPanel.formatear(muestra.getTempC(), 1),
                FormatosPanel.formatear(muestra.getTdsPpm(), 0),
                muestra.getRecordedAt(),
                HORA.format(muestra.getRecordedAt()),
                FECHA_HORA.format(muestra.getRecordedAt()),
                FormatosPanel.hace(muestra.getRecordedAt(), ahora));
    }

    private static PanelDashboard.Punto punto(WaterQualitySample muestra) {
        return new PanelDashboard.Punto(
                muestra.getNodeId(),
                muestra.getRecordedAt(),
                muestra.getPh(),
                muestra.getTurbidityNtu(),
                muestra.getTempC(),
                muestra.getTdsPpm());
    }
}
