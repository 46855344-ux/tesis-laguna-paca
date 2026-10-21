package pe.edu.continental.lagunapaca.telemetry.mqtt;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import pe.edu.continental.lagunapaca.telemetry.api.TelemetryRequest;
import pe.edu.continental.lagunapaca.telemetry.api.TelemetryResponse;
import pe.edu.continental.lagunapaca.telemetry.service.IngestOutcome;
import pe.edu.continental.lagunapaca.telemetry.service.TelemetryService;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * Procesa un mensaje MQTT de telemetría: deserializa y valida el JSON y delega
 * en el caso de uso de ingesta ({@link TelemetryService#ingest}).
 *
 * <p>Los errores de datos (JSON inválido o payload que no cumple las reglas de
 * validación) se registran y se descartan, para no detener al consumidor. Los
 * fallos de infraestructura (por ejemplo, la base de datos caída) sí se
 * propagan al canal de errores de Spring Integration, de modo que sean
 * visibles.</p>
 */
@Component
public class TelemetryMqttHandler {

    private static final Logger log = LoggerFactory.getLogger(TelemetryMqttHandler.class);

    private final TelemetryService telemetryService;
    private final ObjectMapper objectMapper;
    private final Validator validator;

    public TelemetryMqttHandler(TelemetryService telemetryService, ObjectMapper objectMapper, Validator validator) {
        this.telemetryService = telemetryService;
        this.objectMapper = objectMapper;
        this.validator = validator;
    }

    /**
     * @param payload JSON con el payload del ESP32
     *                ({@code {"node_id":"N1","ph":7.2,"turbidity_ntu":15.4,"temp_c":18.5,"tds_ppm":320}})
     */
    public void handle(String payload) {
        TelemetryRequest request;
        try {
            request = objectMapper.readValue(payload, TelemetryRequest.class);
        } catch (JsonProcessingException e) {
            log.warn("Payload MQTT descartado (JSON inválido): {}", e.getOriginalMessage());
            return;
        }

        if (request == null) {
            log.warn("Payload MQTT descartado (JSON nulo)");
            return;
        }

        Set<ConstraintViolation<TelemetryRequest>> violations = validator.validate(request);
        if (!violations.isEmpty()) {
            log.warn("Payload MQTT descartado (validación): {}", describe(violations));
            return;
        }

        IngestOutcome outcome = telemetryService.ingest(request);
        TelemetryResponse response = outcome.response();
        log.info("Telemetría MQTT ingerida: id={}, node={}, alert={}, idempotente={}",
                response.id(), response.nodeId(), response.alert(), !outcome.created());
    }

    private static String describe(Set<ConstraintViolation<TelemetryRequest>> violations) {
        return violations.stream()
                .map(violation -> violation.getPropertyPath() + " " + violation.getMessage())
                .collect(Collectors.joining("; "));
    }
}
