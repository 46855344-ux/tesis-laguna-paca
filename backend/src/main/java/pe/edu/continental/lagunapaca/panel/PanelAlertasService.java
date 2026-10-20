package pe.edu.continental.lagunapaca.panel;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import pe.edu.continental.lagunapaca.telemetry.domain.AlertReason;
import pe.edu.continental.lagunapaca.telemetry.domain.WaterQualitySample;
import pe.edu.continental.lagunapaca.telemetry.domain.WaterQualitySampleRepository;
import pe.edu.continental.lagunapaca.telemetry.domain.WaterQualitySampleSpecifications;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Gestión de alertas ECA-Agua desde el panel: listarlas y registrar que alguien
 * se ocupó de ellas.
 *
 * <p>La atención <strong>no</strong> cambia el veredicto. {@code alert} lo decidió
 * {@code EcaWaterQualityRule} al ingerir y sigue siendo cierto; lo que se guarda
 * aquí es la respuesta humana (quién, cuándo y con qué nota). Por eso el tablero
 * conserva sus tres estados y esta pantalla añade la dimensión "pendiente /
 * atendida" por separado.</p>
 */
@Service
public class PanelAlertasService {

    /** Alertas por página: suficientes para priorizar sin perder el contexto. */
    static final int TAMANO_PAGINA = 20;

    /** Ventana de los contadores que dicen "24 h". */
    private static final Duration VENTANA = Duration.ofHours(24);

    /** Longitud de la columna {@code acknowledge_note}. */
    private static final int MAX_NOTA = 500;

    private static final DateTimeFormatter FECHA =
            DateTimeFormatter.ofPattern("dd/MM/yyyy").withZone(PanelDashboardService.ZONA);
    private static final DateTimeFormatter HORA =
            DateTimeFormatter.ofPattern("HH:mm").withZone(PanelDashboardService.ZONA);

    private final WaterQualitySampleRepository repository;
    private final Clock clock;

    @Autowired
    public PanelAlertasService(WaterQualitySampleRepository repository) {
        this(repository, Clock.systemUTC());
    }

    /** Constructor con reloj explícito: permite fijar el "ahora" en los tests. */
    public PanelAlertasService(WaterQualitySampleRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public AlertasVista construir(FiltrosAlertas filtros) {
        List<String> avisos = new ArrayList<>(1);

        Instant desde = FechasPanel.desde(filtros.getDesde(), "desde", avisos);
        Instant hasta = FechasPanel.hasta(filtros.getHasta(), "hasta", avisos);
        if (desde != null && hasta != null && desde.isAfter(hasta)) {
            avisos.add("El rango de fechas no se aplicó: «desde» es posterior a «hasta».");
            desde = null;
            hasta = null;
        }

        Specification<WaterQualitySample> filtro = Specification
                .where(WaterQualitySampleSpecifications.hasAlert(Boolean.TRUE))
                .and(WaterQualitySampleSpecifications.hasNodeId(vacioANulo(filtros.getEstacion())))
                .and(WaterQualitySampleSpecifications.recordedAtFrom(desde))
                .and(WaterQualitySampleSpecifications.recordedAtTo(hasta))
                .and(WaterQualitySampleSpecifications.isAcknowledged(atendidaSegun(filtros.getEstado())));

        int pagina = Math.max(0, filtros.getPagina());
        Page<WaterQualitySample> resultado = buscar(filtro, pagina);

        // Al cambiar de filtro es fácil quedar en una página que ya no existe.
        if (resultado.isEmpty() && pagina > 0 && resultado.getTotalPages() > 0) {
            pagina = resultado.getTotalPages() - 1;
            resultado = buscar(filtro, pagina);
        }

        Instant ahora = clock.instant();
        List<AlertasVista.Fila> filas = resultado.getContent().stream()
                .map(muestra -> fila(muestra, ahora))
                .toList();

        int desdeFila = filas.isEmpty() ? 0 : pagina * TAMANO_PAGINA + 1;
        int hastaFila = filas.isEmpty() ? 0 : desdeFila + filas.size() - 1;

        return new AlertasVista(
                filas,
                filtros,
                pagina + 1,
                resultado.getTotalPages(),
                resultado.getTotalElements(),
                desdeFila,
                hastaFila,
                Paginas.visibles(pagina + 1, resultado.getTotalPages()),
                contar(WaterQualitySampleSpecifications.isAcknowledged(Boolean.FALSE)),
                contar(WaterQualitySampleSpecifications.isAcknowledged(Boolean.TRUE),
                        WaterQualitySampleSpecifications.acknowledgedAtFrom(ahora.minus(VENTANA))),
                contar(WaterQualitySampleSpecifications.recordedAtFrom(ahora.minus(VENTANA))),
                avisos.isEmpty() ? null : String.join(" ", avisos));
    }

    /**
     * Marca una alerta como atendida.
     *
     * <p>Si ya estaba atendida no se vuelve a escribir: un doble envío del
     * formulario (recargar la página) no debe pisar a quien la atendió primero.</p>
     *
     * @throws ResponseStatusException 404 si la muestra no existe o no es una alerta
     */
    @Transactional
    public void atender(long id, String nota, String usuario) {
        WaterQualitySample alerta = buscarAlerta(id);
        if (alerta.isAcknowledged()) {
            return;
        }
        alerta.acknowledge(clock.instant(), usuario, recortar(nota));
    }

    /** Devuelve una alerta atendida a la lista de pendientes. */
    @Transactional
    public void reabrir(long id) {
        buscarAlerta(id).reopen();
    }

    private Page<WaterQualitySample> buscar(Specification<WaterQualitySample> filtro, int pagina) {
        return repository.findAll(filtro,
                PageRequest.of(pagina, TAMANO_PAGINA, Sort.by(Sort.Direction.DESC, "recordedAt")));
    }

    /** El panel es de alertas: una muestra sin alerta no es un recurso de esta pantalla. */
    private WaterQualitySample buscarAlerta(long id) {
        WaterQualitySample muestra = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "La muestra " + id + " no existe."));
        if (!muestra.isAlert()) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "La muestra " + id + " no tiene alerta ECA-Agua.");
        }
        return muestra;
    }

    /** Cuenta alertas que cumplen todas las condiciones indicadas. */
    @SafeVarargs
    private long contar(Specification<WaterQualitySample>... condiciones) {
        Specification<WaterQualitySample> filtro =
                Specification.where(WaterQualitySampleSpecifications.hasAlert(Boolean.TRUE));
        for (Specification<WaterQualitySample> condicion : condiciones) {
            filtro = filtro.and(condicion);
        }
        return repository.count(filtro);
    }

    private static Boolean atendidaSegun(String estado) {
        if (FiltrosAlertas.ATENDIDAS.equals(estado)) {
            return Boolean.TRUE;
        }
        if (FiltrosAlertas.TODAS.equals(estado)) {
            return null;
        }
        return Boolean.FALSE;
    }

    private static AlertasVista.Fila fila(WaterQualitySample muestra, Instant ahora) {
        return new AlertasVista.Fila(
                muestra.getId(),
                muestra.getNodeId(),
                FECHA.format(muestra.getRecordedAt()),
                HORA.format(muestra.getRecordedAt()),
                FormatosPanel.hace(muestra.getRecordedAt(), ahora),
                FormatosPanel.formatear(muestra.getPh(), 2),
                FormatosPanel.formatear(muestra.getTurbidityNtu(), 1),
                FormatosPanel.formatear(muestra.getTempC(), 1),
                FormatosPanel.formatear(muestra.getTdsPpm(), 0),
                muestra.getAlertReasons().stream().map(AlertReason::getMessage).toList(),
                muestra.isAcknowledged(),
                muestra.getAcknowledgedBy(),
                muestra.isAcknowledged()
                        ? FormatosPanel.hace(muestra.getAcknowledgedAt(), ahora)
                        : null,
                muestra.getAcknowledgeNote());
    }

    private static String vacioANulo(String texto) {
        return texto == null || texto.isBlank() ? null : texto.trim();
    }

    /**
     * Recorta la nota al tamaño de la columna. El formulario ya limita a
     * {@value #MAX_NOTA} caracteres; esto es la red de seguridad para una petición
     * armada a mano, que no debe terminar en un error 500 de base de datos.
     */
    private static String recortar(String nota) {
        if (nota == null) {
            return null;
        }
        String limpia = nota.trim();
        return limpia.length() <= MAX_NOTA ? limpia : limpia.substring(0, MAX_NOTA);
    }
}
