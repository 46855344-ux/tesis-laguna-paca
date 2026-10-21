package pe.edu.continental.lagunapaca.prediction.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import pe.edu.continental.lagunapaca.prediction.api.AiStatusResponse;
import pe.edu.continental.lagunapaca.prediction.api.PredictionResponse;
import pe.edu.continental.lagunapaca.prediction.client.AiClient;
import pe.edu.continental.lagunapaca.prediction.client.AiPredictionResult;
import pe.edu.continental.lagunapaca.prediction.domain.Prediction;
import pe.edu.continental.lagunapaca.prediction.domain.PredictionRepository;
import pe.edu.continental.lagunapaca.telemetry.api.PageResponse;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Caso de uso de predicciones.
 *
 * <p>El servicio de IA entrega las cuatro estaciones en una sola inferencia.
 * Aquí se persiste una fila por estación, agrupadas por {@code model_run_id},
 * para poder evaluarlas después contra la medición real.</p>
 *
 * <p>La llamada HTTP no ocurre dentro de una transacción: sostener una conexión
 * a la base durante una espera de red de hasta 15 s agotaría el pool.</p>
 */
@Service
public class PredictionService {

    /** Horizontes que el experimento entrena y publica. */
    public static final Set<Integer> HORIZONTES = Set.of(1, 3, 6);

    private final AiClient aiClient;
    private final PredictionWindowBuilder windowBuilder;
    private final PredictionRepository repository;
    private final Clock clock;

    public PredictionService(AiClient aiClient,
                             PredictionWindowBuilder windowBuilder,
                             PredictionRepository repository,
                             Clock clock) {
        this.aiClient = aiClient;
        this.windowBuilder = windowBuilder;
        this.repository = repository;
        this.clock = clock;
    }

    /**
     * Genera una predicción para las cuatro estaciones y la persiste.
     *
     * @throws AiUnavailableException    si el servicio de IA no responde
     * @throws AiContractException       si la respuesta no cumple el contrato
     * @throws IncompleteWindowException si falta historia en alguna estación
     */
    public List<PredictionResponse> generar(int horizonHours) {
        if (!HORIZONTES.contains(horizonHours)) {
            throw new IllegalArgumentException(
                    "Horizonte no soportado: " + horizonHours + ". Use 1, 3 o 6 horas.");
        }

        AiStatusResponse estado = aiClient.health();
        if (!estado.available()) {
            throw new AiUnavailableException(estado.detail() != null
                    ? estado.detail()
                    : "El servicio de IA no está disponible.");
        }
        if (estado.inputLen() == null || estado.inputLen() <= 0) {
            throw new AiContractException("El servicio de IA no informa el tamaño de la ventana");
        }

        PredictionWindow ventana = windowBuilder.construir(estado.inputLen());
        AiPredictionResult resultado = aiClient.predict(horizonHours, ventana.baseTime(), ventana.valores());

        UUID modelRunId = UUID.randomUUID();
        Instant generado = clock.instant();
        List<Prediction> filas = resultado.predictions().stream()
                .map(p -> new Prediction(
                        modelRunId,
                        p.station(),
                        "ph",
                        horizonHours,
                        generado,
                        resultado.targetAt(),
                        p.ph(),
                        resultado.modelVersion(),
                        resultado.dataSource()))
                .toList();

        return repository.saveAll(filas).stream().map(PredictionResponse::from).toList();
    }

    /** Estado del servicio de IA para el endpoint de observabilidad. */
    public AiStatusResponse estadoIa() {
        return aiClient.health();
    }

    /** Últimas predicciones, para el tablero. */
    public List<PredictionResponse> ultimas() {
        return repository.findTop12ByOrderByGeneratedAtDesc().stream()
                .map(PredictionResponse::from)
                .toList();
    }

    /** Consulta paginada con filtros opcionales. */
    public PageResponse<PredictionResponse> buscar(String nodeId, Integer horizonHours, Pageable pageable) {
        Page<Prediction> page;
        if (nodeId != null && horizonHours != null) {
            page = repository.findByNodeIdAndHorizonHours(nodeId, horizonHours, pageable);
        } else if (nodeId != null) {
            page = repository.findByNodeId(nodeId, pageable);
        } else if (horizonHours != null) {
            page = repository.findByHorizonHours(horizonHours, pageable);
        } else {
            page = repository.findAll(pageable);
        }
        return PageResponse.from(page.map(PredictionResponse::from));
    }
}
