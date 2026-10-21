package pe.edu.continental.lagunapaca.prediction.api;

import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import pe.edu.continental.lagunapaca.prediction.service.PredictionService;
import pe.edu.continental.lagunapaca.telemetry.api.PageResponse;

import java.util.List;

/**
 * API REST de predicciones (JWT, sin estado).
 *
 * <p>No expone entidades JPA: el contrato son DTO. La generación es explícita
 * (POST) y no se dispara en cada ingesta, para no acoplar la telemetría a la
 * disponibilidad de la IA.</p>
 */
@RestController
@RequestMapping("/api/v1/predictions")
public class PredictionController {

    private final PredictionService predictionService;

    public PredictionController(PredictionService predictionService) {
        this.predictionService = predictionService;
    }

    /** Genera una predicción para las cuatro estaciones en el horizonte pedido. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public List<PredictionResponse> generar(@Valid @RequestBody PredictionRequest request) {
        return predictionService.generar(request.horizonHours());
    }

    /** Consulta paginada, de la más reciente a la más antigua. */
    @GetMapping
    public PageResponse<PredictionResponse> listar(
            @RequestParam(name = "node_id", required = false) String nodeId,
            @RequestParam(name = "horizon_hours", required = false) Integer horizonHours,
            @PageableDefault(size = 20, sort = "generatedAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return predictionService.buscar(nodeId, horizonHours, pageable);
    }

    /** Últimas predicciones, sin paginar. */
    @GetMapping("/latest")
    public List<PredictionResponse> ultimas() {
        return predictionService.ultimas();
    }
}
