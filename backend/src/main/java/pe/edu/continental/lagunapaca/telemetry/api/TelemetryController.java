package pe.edu.continental.lagunapaca.telemetry.api;

import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pe.edu.continental.lagunapaca.telemetry.domain.AlertCode;
import pe.edu.continental.lagunapaca.telemetry.service.IngestOutcome;
import pe.edu.continental.lagunapaca.telemetry.service.TelemetryQuery;
import pe.edu.continental.lagunapaca.telemetry.service.TelemetryService;

import java.time.Instant;

/**
 * Endpoints de telemetría. Requieren JWT (ver {@code SecurityConfig}).
 */
@RestController
@RequestMapping("/api/v1/telemetry")
public class TelemetryController {

    private final TelemetryService telemetryService;

    public TelemetryController(TelemetryService telemetryService) {
        this.telemetryService = telemetryService;
    }

    /**
     * Ingesta una muestra. Responde 201 si se creó y 200 si el {@code sample_id}
     * ya existía: el reintento es idempotente, no un error.
     */
    @PostMapping
    public ResponseEntity<TelemetryResponse> ingest(@Valid @RequestBody TelemetryRequest request) {
        IngestOutcome outcome = telemetryService.ingest(request);
        return ResponseEntity
                .status(outcome.created() ? HttpStatus.CREATED : HttpStatus.OK)
                .body(outcome.response());
    }

    @GetMapping
    public PageResponse<TelemetryResponse> search(
            @RequestParam(name = "node_id", required = false) String nodeId,
            @RequestParam(name = "from", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(name = "to", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(name = "alert", required = false) Boolean alert,
            @RequestParam(name = "reason", required = false) String reason,
            @RequestParam(name = "code", required = false) AlertCode code,
            @PageableDefault(size = 20, sort = "recordedAt", direction = Sort.Direction.DESC) Pageable pageable) {

        return telemetryService.search(new TelemetryQuery(nodeId, from, to, alert, reason, code), pageable);
    }
}
