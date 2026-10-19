package pe.edu.continental.lagunapaca.telemetry.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import pe.edu.continental.lagunapaca.telemetry.api.AlertReasonDto;
import pe.edu.continental.lagunapaca.telemetry.api.PageResponse;
import pe.edu.continental.lagunapaca.telemetry.api.TelemetryRequest;
import pe.edu.continental.lagunapaca.telemetry.api.TelemetryResponse;
import pe.edu.continental.lagunapaca.telemetry.domain.AlertCode;
import pe.edu.continental.lagunapaca.telemetry.domain.AlertReason;
import pe.edu.continental.lagunapaca.telemetry.domain.WaterQualitySample;
import pe.edu.continental.lagunapaca.telemetry.domain.WaterQualitySampleRepository;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Servicio de telemetría")
class TelemetryServiceTest {

    @Mock
    private WaterQualitySampleRepository repository;

    private TelemetryService service;

    @BeforeEach
    void setUp() {
        service = new TelemetryService(repository, new EcaWaterQualityRule());
    }

    private static TelemetryRequest sample(double ph) {
        return new TelemetryRequest("N1", ph, 15.4, 18.5, 320.0);
    }

    private static TelemetryQuery noFilters() {
        return new TelemetryQuery("N1", null, null, null, null, null);
    }

    // ---------------------------------------------------------------- ingesta

    @Test
    @DisplayName("ingesta: persiste la muestra y devuelve la respuesta mapeada")
    void ingesta_persisteYMapea_laRespuesta() {
        when(repository.save(any(WaterQualitySample.class))).thenAnswer(invocation -> {
            WaterQualitySample sample = invocation.getArgument(0);
            sample.setId(1L);
            return sample;
        });

        TelemetryResponse response = service.ingest(sample(7.2));

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.nodeId()).isEqualTo("N1");
        assertThat(response.ph()).isEqualTo(7.2);
        assertThat(response.alert()).isFalse();
        assertThat(response.alertReasons()).isEmpty();
        assertThat(response.recordedAt()).isNotNull();
        verify(repository).save(any(WaterQualitySample.class));
    }

    @Test
    @DisplayName("ingesta: con pH crítico expone código y mensaje")
    void ingesta_conPhCritico_marcaAlerta() {
        when(repository.save(any(WaterQualitySample.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        TelemetryResponse response = service.ingest(sample(9.7));

        assertThat(response.alert()).isTrue();
        assertThat(response.alertReasons()).extracting(AlertReasonDto::code).containsExactly(AlertCode.PH_HIGH);
        assertThat(response.alertReasons()).extracting(AlertReasonDto::message).allMatch(m -> !m.isBlank());
    }

    // --------------------------------------------------------------- consulta

    @Test
    @DisplayName("consulta: mapea la página del repositorio a PageResponse")
    void consulta_mapeaLaPagina() {
        WaterQualitySample stored = new WaterQualitySample(
                "N1", 7.2, 15.4, 18.5, 320.0, Instant.parse("2026-10-18T09:00:00Z"));
        stored.setId(10L);
        Page<WaterQualitySample> page = new PageImpl<>(List.of(stored), PageRequest.of(0, 20), 1);

        when(repository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);

        PageResponse<TelemetryResponse> result = service.search(noFilters(), PageRequest.of(0, 20));

        assertThat(result.content()).hasSize(1);
        assertThat(result.content().get(0).id()).isEqualTo(10L);
        assertThat(result.page()).isZero();
        assertThat(result.size()).isEqualTo(20);
        assertThat(result.totalElements()).isEqualTo(1);
        assertThat(result.totalPages()).isEqualTo(1);
        verify(repository).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    @DisplayName("consulta: 'from' posterior a 'to' lanza IllegalArgumentException")
    void consulta_conRangoInvalido_lanzaExcepcion() {
        TelemetryQuery query = new TelemetryQuery(
                "N1",
                Instant.parse("2026-10-18T12:00:00Z"),
                Instant.parse("2026-10-18T10:00:00Z"),
                null,
                null,
                null);

        assertThatThrownBy(() -> service.search(query, PageRequest.of(0, 20)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
