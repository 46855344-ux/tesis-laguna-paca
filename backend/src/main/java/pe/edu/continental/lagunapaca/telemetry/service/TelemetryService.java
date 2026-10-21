package pe.edu.continental.lagunapaca.telemetry.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.continental.lagunapaca.prediction.service.PredictionEvaluator;
import pe.edu.continental.lagunapaca.telemetry.api.AlertReasonDto;
import pe.edu.continental.lagunapaca.telemetry.api.PageResponse;
import pe.edu.continental.lagunapaca.telemetry.api.TelemetryRequest;
import pe.edu.continental.lagunapaca.telemetry.api.TelemetryResponse;
import pe.edu.continental.lagunapaca.telemetry.domain.AlertReason;
import pe.edu.continental.lagunapaca.telemetry.domain.WaterQualitySample;
import pe.edu.continental.lagunapaca.telemetry.domain.WaterQualitySampleRepository;
import pe.edu.continental.lagunapaca.telemetry.domain.WaterQualitySampleSpecifications;

import java.time.Clock;
import java.util.Optional;

/**
 * Casos de uso del dominio de telemetría: ingesta y consulta histórica.
 * El {@link Clock} es inyectable para tests.
 */
@Service
public class TelemetryService {

    private final WaterQualitySampleRepository repository;
    private final EcaWaterQualityRule rule;
    private final Clock clock;
    private final PredictionEvaluator predictionEvaluator;

    /**
     * Constructor que usa Spring.
     *
     * <p>La anotación es imprescindible: la clase tiene varios constructores (los
     * demás permiten inyectar un reloj fijo en los tests) y, sin ella, Spring no
     * sabe cuál elegir y el contexto de la aplicación no arranca.</p>
     */
    @Autowired
    public TelemetryService(WaterQualitySampleRepository repository, EcaWaterQualityRule rule,
                            PredictionEvaluator predictionEvaluator) {
        this(repository, rule, Clock.systemUTC(), predictionEvaluator);
    }

    /** Constructor para tests que no ejercitan la evaluación de predicciones. */
    public TelemetryService(WaterQualitySampleRepository repository, EcaWaterQualityRule rule, Clock clock) {
        this(repository, rule, clock, null);
    }

    /** Constructor mínimo para slices que solo prueban la ingesta. */
    public TelemetryService(WaterQualitySampleRepository repository, EcaWaterQualityRule rule) {
        this(repository, rule, Clock.systemUTC(), null);
    }

    public TelemetryService(WaterQualitySampleRepository repository, EcaWaterQualityRule rule,
                            Clock clock, PredictionEvaluator predictionEvaluator) {
        this.repository = repository;
        this.rule = rule;
        this.clock = clock;
        this.predictionEvaluator = predictionEvaluator;
    }

    /**
     * Ingesta una muestra: evalúa los límites ECA-Agua, la persiste con su marca
     * temporal y devuelve la respuesta.
     *
     * <p>Si llega {@code sample_id} y ya existe, no se crea otra fila: se devuelve
     * la muestra original con {@code created=false}. El índice único de la V5 es
     * la garantía de último recurso ante dos peticiones simultáneas.</p>
     */
    @Transactional
    public IngestOutcome ingest(TelemetryRequest request) {
        String sampleId = normalize(request.sampleId());
        if (sampleId != null) {
            Optional<WaterQualitySample> existente = repository.findBySampleId(sampleId);
            if (existente.isPresent()) {
                return IngestOutcome.duplicate(toResponse(existente.get()));
            }
        }

        AlertEvaluation evaluation = rule.evaluate(request);

        WaterQualitySample sample = new WaterQualitySample(
                request.nodeId(),
                request.ph(),
                request.turbidityNtu(),
                request.tempC(),
                request.tdsPpm(),
                clock.instant());
        sample.setSampleId(sampleId);
        sample.setAlert(evaluation.alert());
        sample.setAlertReasons(evaluation.reasons());

        WaterQualitySample guardada = repository.save(sample);

        // La evaluación es un efecto secundario: nunca puede impedir que la
        // medición quede registrada (requisito explícito).
        if (predictionEvaluator != null) {
            predictionEvaluator.evaluarSinRomperIngesta(
                    guardada.getNodeId(), guardada.getRecordedAt(), guardada.getPh());
        }

        return IngestOutcome.created(toResponse(guardada));
    }

    private static String normalize(String sampleId) {
        if (sampleId == null || sampleId.isBlank()) {
            return null;
        }
        return sampleId.trim();
    }

    /**
     * Consulta histórica paginada, filtrada opcionalmente por nodo, rango de fechas,
     * alerta, texto del motivo y código del motivo.
     *
     * @throws IllegalArgumentException si {@code from} es posterior a {@code to}
     */
    @Transactional(readOnly = true)
    public PageResponse<TelemetryResponse> search(TelemetryQuery query, Pageable pageable) {
        if (query.from() != null && query.to() != null && query.from().isAfter(query.to())) {
            throw new IllegalArgumentException("'from' no puede ser posterior a 'to'");
        }

        Specification<WaterQualitySample> specification = Specification
                .where(WaterQualitySampleSpecifications.hasNodeId(query.nodeId()))
                .and(WaterQualitySampleSpecifications.recordedAtFrom(query.from()))
                .and(WaterQualitySampleSpecifications.recordedAtTo(query.to()))
                .and(WaterQualitySampleSpecifications.hasAlert(query.alert()))
                .and(WaterQualitySampleSpecifications.hasReasonContaining(query.reason()))
                .and(WaterQualitySampleSpecifications.hasCode(query.code()))
                .and(WaterQualitySampleSpecifications.isAcknowledged(query.acknowledged()));

        Page<TelemetryResponse> page = repository.findAll(specification, pageable)
                .map(TelemetryService::toResponse);

        return PageResponse.from(page);
    }

    /** Mapea la entidad a su DTO. Debe invocarse dentro de la transacción (carga perezosa de los motivos). */
    private static TelemetryResponse toResponse(WaterQualitySample sample) {
        return new TelemetryResponse(
                sample.getId(),
                sample.getNodeId(),
                sample.getPh(),
                sample.getTurbidityNtu(),
                sample.getTempC(),
                sample.getTdsPpm(),
                sample.isAlert(),
                sample.getAlertReasons().stream()
                        .map(TelemetryService::toDto)
                        .toList(),
                sample.getRecordedAt());
    }

    private static AlertReasonDto toDto(AlertReason reason) {
        return new AlertReasonDto(reason.getCode(), reason.getMessage());
    }
}
