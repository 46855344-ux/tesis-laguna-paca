package pe.edu.continental.lagunapaca.telemetry.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Prueba el mapeo JPA contra H2. Flyway se desactiva porque su migración es
 * específica de PostgreSQL; contra Postgres real se usará Testcontainers (futuro).
 */
@DataJpaTest(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@DisplayName("Repositorio de muestras de agua")
class WaterQualitySampleRepositoryTest {

    @Autowired
    private WaterQualitySampleRepository repository;

    @Autowired
    private TestEntityManager entityManager;

    private void seed() {
        repository.save(new WaterQualitySample("N1", 7.2, 15.4, 18.5, 320.0,
                Instant.parse("2026-10-18T09:00:00Z")));
        repository.save(new WaterQualitySample("N1", 7.5, 12.0, 19.0, 300.0,
                Instant.parse("2026-10-18T11:00:00Z")));
        repository.save(new WaterQualitySample("N2", 6.8, 20.0, 17.0, 410.0,
                Instant.parse("2026-10-18T10:00:00Z")));
    }

    private static WaterQualitySample alertSample(String nodeId, Instant recordedAt, AlertCode code, String message) {
        WaterQualitySample sample = new WaterQualitySample(nodeId, 9.7, 12.0, 19.0, 300.0, recordedAt);
        sample.setAlert(true);
        sample.setAlertReasons(List.of(new AlertReason(code, message)));
        return sample;
    }

    @Test
    @DisplayName("persiste y recupera por nodo ordenado por fecha descendente")
    void persisteYRecupera_porNodoOrdenado() {
        seed();

        List<WaterQualitySample> n1 = repository.findByNodeIdOrderByRecordedAtDesc("N1");

        assertThat(n1).hasSize(2);
        assertThat(n1.get(0).getRecordedAt()).isAfter(n1.get(1).getRecordedAt());
    }

    @Test
    @DisplayName("filtra por nodo y rango de fechas con Specifications")
    void filtraPorNodoYRangoDeFechas() {
        seed();

        Specification<WaterQualitySample> spec = Specification
                .where(WaterQualitySampleSpecifications.hasNodeId("N1"))
                .and(WaterQualitySampleSpecifications.recordedAtFrom(Instant.parse("2026-10-18T09:30:00Z")))
                .and(WaterQualitySampleSpecifications.recordedAtTo(Instant.parse("2026-10-18T11:30:00Z")));

        List<WaterQualitySample> result = repository.findAll(spec);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getRecordedAt()).isEqualTo(Instant.parse("2026-10-18T11:00:00Z"));
    }

    @Test
    @DisplayName("persiste los motivos estructurados en la tabla alert_reason")
    void persisteLosMotivosEstructurados() {
        WaterQualitySample sample = alertSample("N1", Instant.parse("2026-10-18T09:00:00Z"),
                AlertCode.PH_HIGH, "pH 9.70 supera el límite superior ECA-Agua (9.0)");

        repository.saveAndFlush(sample);
        entityManager.clear();

        WaterQualitySample reloaded = repository.findById(sample.getId()).orElseThrow();

        assertThat(reloaded.getAlertReasons()).hasSize(1);
        assertThat(reloaded.getAlertReasons().get(0).getCode()).isEqualTo(AlertCode.PH_HIGH);
        assertThat(reloaded.getAlertReasons().get(0).getMessage())
                .isEqualTo("pH 9.70 supera el límite superior ECA-Agua (9.0)");
    }

    @Test
    @DisplayName("filtra por alerta y por texto del mensaje")
    void filtraPorAlertaYPorMotivo() {
        WaterQualitySample apt = new WaterQualitySample("N1", 7.2, 15.4, 18.5, 320.0,
                Instant.parse("2026-10-18T09:00:00Z"));
        apt.setAlert(false);

        WaterQualitySample high = alertSample("N1", Instant.parse("2026-10-18T11:00:00Z"),
                AlertCode.PH_HIGH, "pH 9.70 supera el límite superior ECA-Agua (9.0)");
        WaterQualitySample low = alertSample("N2", Instant.parse("2026-10-18T10:00:00Z"),
                AlertCode.PH_LOW, "pH 6.10 está por debajo del límite inferior ECA-Agua (6.5)");

        repository.saveAll(List.of(apt, high, low));
        repository.flush();

        List<WaterQualitySample> conAlerta = repository.findAll(
                Specification.where(WaterQualitySampleSpecifications.hasAlert(true)));
        assertThat(conAlerta).hasSize(2);

        List<WaterQualitySample> porTexto = repository.findAll(Specification
                .where(WaterQualitySampleSpecifications.hasReasonContaining("SUPERIOR")));
        assertThat(porTexto).hasSize(1);
        assertThat(porTexto.get(0).getNodeId()).isEqualTo("N1");
    }

    @Test
    @DisplayName("filtra por código exacto del motivo")
    void filtraPorCodigoExacto() {
        repository.save(alertSample("N1", Instant.parse("2026-10-18T11:00:00Z"),
                AlertCode.PH_HIGH, "pH 9.70 supera el límite superior ECA-Agua (9.0)"));
        repository.save(alertSample("N2", Instant.parse("2026-10-18T10:00:00Z"),
                AlertCode.PH_LOW, "pH 6.10 está por debajo del límite inferior ECA-Agua (6.5)"));
        repository.flush();

        List<WaterQualitySample> soloHigh = repository.findAll(
                Specification.where(WaterQualitySampleSpecifications.hasCode(AlertCode.PH_HIGH)));

        assertThat(soloHigh).hasSize(1);
        assertThat(soloHigh.get(0).getNodeId()).isEqualTo("N1");
    }
}
