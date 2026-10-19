package pe.edu.continental.lagunapaca.telemetry.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;

/**
 * Acceso a las series temporales de calidad de agua.
 */
public interface WaterQualitySampleRepository
        extends JpaRepository<WaterQualitySample, Long>, JpaSpecificationExecutor<WaterQualitySample> {

    List<WaterQualitySample> findByNodeIdOrderByRecordedAtDesc(String nodeId);
}
