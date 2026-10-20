package pe.edu.continental.lagunapaca.telemetry.domain;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Acceso a las series temporales de calidad de agua.
 */
public interface WaterQualitySampleRepository
        extends JpaRepository<WaterQualitySample, Long>, JpaSpecificationExecutor<WaterQualitySample> {

    List<WaterQualitySample> findByNodeIdOrderByRecordedAtDesc(String nodeId);

    /**
     * Última muestra conocida de un nodo, sin importar su antigüedad. El tablero
     * la usa para la tarjeta de la estación: si el nodo lleva días callado, la
     * tarjeta lo deja ver con el texto "hace X".
     */
    Optional<WaterQualitySample> findFirstByNodeIdOrderByRecordedAtDesc(String nodeId);

    /**
     * Muestras de la ventana indicada, en orden cronológico (lo que necesita un
     * gráfico). {@code pageable} acota el volumen: un nodo descontrolado no debe
     * poder arrastrar toda la tabla a la memoria del panel.
     */
    List<WaterQualitySample> findByRecordedAtGreaterThanEqualOrderByRecordedAtAsc(
            Instant from, Pageable pageable);
}
