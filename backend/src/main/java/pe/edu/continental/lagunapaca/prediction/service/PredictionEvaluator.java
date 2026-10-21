package pe.edu.continental.lagunapaca.prediction.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.continental.lagunapaca.prediction.domain.Prediction;
import pe.edu.continental.lagunapaca.prediction.domain.PredictionRepository;
import pe.edu.continental.lagunapaca.prediction.domain.PredictionStatus;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Evaluación posterior de una predicción (RN-07).
 *
 * <p>Cuando llega una muestra real, se buscan las predicciones pendientes de esa
 * estación cuya hora objetivo cae dentro de una tolerancia. La tolerancia existe
 * porque los nodos no muestrean exactamente en el instante planificado: sin ella
 * una predicción a 6 h nunca se evaluaría.</p>
 *
 * <p>Se ejecuta en una transacción propia ({@code REQUIRES_NEW}) y absorbe sus
 * errores: un fallo al evaluar no puede impedir que se registre una medición
 * válida. Ese es un requisito explícito del sistema.</p>
 */
@Service
public class PredictionEvaluator {

    private static final Logger log = LoggerFactory.getLogger(PredictionEvaluator.class);

    /** ±30 min: suficiente para el desfase normal de muestreo sin cruzar horizontes. */
    static final Duration TOLERANCIA = Duration.ofMinutes(30);

    private final PredictionRepository repository;
    private final Clock clock;

    public PredictionEvaluator(PredictionRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    /**
     * Evalúa las predicciones que apuntan al instante de la muestra.
     *
     * @return cuántas predicciones se evaluaron
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int evaluar(String nodeId, Instant recordedAt, double phObservado) {
        List<Prediction> pendientes = repository.findByNodeIdAndStatusAndTargetAtBetween(
                nodeId,
                PredictionStatus.PENDING,
                recordedAt.minus(TOLERANCIA),
                recordedAt.plus(TOLERANCIA));

        if (pendientes.isEmpty()) {
            return 0;
        }

        Instant ahora = clock.instant();
        for (Prediction prediction : pendientes) {
            prediction.evaluate(phObservado, ahora);
        }
        repository.saveAll(pendientes);
        log.info("Se evaluaron {} predicciones de {} contra una muestra real", pendientes.size(), nodeId);
        return pendientes.size();
    }

    /**
     * Variante que nunca propaga errores. La usa la ingesta: registrar la muestra
     * es prioritario y la evaluación es un efecto secundario.
     */
    public void evaluarSinRomperIngesta(String nodeId, Instant recordedAt, double phObservado) {
        try {
            evaluar(nodeId, recordedAt, phObservado);
        } catch (RuntimeException ex) {
            log.warn("No se pudieron evaluar las predicciones de {}: {}", nodeId, ex.getMessage());
        }
    }
}
