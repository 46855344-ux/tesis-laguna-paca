package pe.edu.continental.lagunapaca.prediction.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pe.edu.continental.lagunapaca.prediction.service.PredictionService;

/**
 * Estado del servicio de inferencia.
 *
 * <p>Responde 200 aunque la IA esté caída: el cuerpo dice
 * {@code available=false}. Así un monitor distingue «el backend no responde» de
 * «el backend responde y la IA no está disponible».</p>
 */
@RestController
@RequestMapping("/api/v1/ai")
public class AiStatusController {

    private final PredictionService predictionService;

    public AiStatusController(PredictionService predictionService) {
        this.predictionService = predictionService;
    }

    @GetMapping("/health")
    public AiStatusResponse salud() {
        return predictionService.estadoIa();
    }
}
