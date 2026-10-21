package pe.edu.continental.lagunapaca.prediction.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Acceso a las predicciones persistidas. */
public interface PredictionRepository extends JpaRepository<Prediction, Long> {

    /** Predicciones pendientes de una estación cuya hora objetivo cae en el rango. */
    List<Prediction> findByNodeIdAndStatusAndTargetAtBetween(
            String nodeId, PredictionStatus status, Instant from, Instant to);

    /** Últimas ejecuciones, para el tablero (sin paginar: son pocas). */
    List<Prediction> findTop12ByOrderByGeneratedAtDesc();

    List<Prediction> findByModelRunId(UUID modelRunId);

    Page<Prediction> findByNodeId(String nodeId, Pageable pageable);

    Page<Prediction> findByHorizonHours(int horizonHours, Pageable pageable);

    Page<Prediction> findByNodeIdAndHorizonHours(String nodeId, int horizonHours, Pageable pageable);
}
