package pe.edu.continental.lagunapaca.prediction.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pe.edu.continental.lagunapaca.prediction.api.AiStatusResponse;
import pe.edu.continental.lagunapaca.prediction.api.PredictionResponse;
import pe.edu.continental.lagunapaca.prediction.client.AiClient;
import pe.edu.continental.lagunapaca.prediction.client.AiPredictionResult;
import pe.edu.continental.lagunapaca.prediction.domain.Prediction;
import pe.edu.continental.lagunapaca.prediction.domain.PredictionRepository;
import pe.edu.continental.lagunapaca.station.StationCatalog;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Caso de uso de predicciones")
class PredictionServiceTest {

    private static final Instant BASE = Instant.parse("2026-10-20T10:00:00Z");
    private static final Instant AHORA = Instant.parse("2026-10-20T10:00:01Z");

    @Mock
    private AiClient aiClient;

    @Mock
    private PredictionWindowBuilder windowBuilder;

    @Mock
    private PredictionRepository repository;

    private PredictionService service;

    @BeforeEach
    void setUp() {
        service = new PredictionService(aiClient, windowBuilder, repository,
                Clock.fixed(AHORA, ZoneOffset.UTC));
    }

    private static AiStatusResponse disponible() {
        return new AiStatusResponse(true, "stgnn-test", List.of(1, 3, 6), 24, "synthetic", null, AHORA);
    }

    private static AiPredictionResult resultado() {
        return new AiPredictionResult(
                "stgnn-test", "synthetic", 6, AHORA, BASE.plus(6, ChronoUnit.HOURS),
                StationCatalog.IDS.stream()
                        .map(id -> new AiPredictionResult.StationPrediction(id, 7.2))
                        .toList());
    }

    @Test
    @DisplayName("genera y persiste una fila por estación, agrupadas por model_run_id")
    @SuppressWarnings("unchecked")
    void generaYPersisteCuatroFilas() {
        when(aiClient.health()).thenReturn(disponible());
        when(windowBuilder.construir(24))
                .thenReturn(new PredictionWindow(StationCatalog.IDS, 24, BASE, new double[4][24][4]));
        when(aiClient.predict(eq(6), eq(BASE), any(double[][][].class))).thenReturn(resultado());
        when(repository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        List<PredictionResponse> respuestas = service.generar(6);

        assertThat(respuestas).hasSize(4);
        assertThat(respuestas).extracting(PredictionResponse::nodeId)
                .containsExactlyElementsOf(StationCatalog.IDS);
        assertThat(respuestas).allSatisfy(r -> {
            assertThat(r.predictedValue()).isEqualTo(7.2);
            assertThat(r.modelVersion()).isEqualTo("stgnn-test");
            assertThat(r.dataSource()).isEqualTo("synthetic");
            assertThat(r.targetAt()).isEqualTo(BASE.plus(6, ChronoUnit.HOURS));
        });
        assertThat(respuestas).extracting(PredictionResponse::modelRunId).containsOnly(respuestas.get(0).modelRunId());

        ArgumentCaptor<List<Prediction>> captor = ArgumentCaptor.forClass(List.class);
        verify(repository).saveAll(captor.capture());
        assertThat(captor.getValue()).hasSize(4);
        assertThat(captor.getValue()).allSatisfy(p -> assertThat(p.getStatus().name()).isEqualTo("PENDING"));
    }

    @Test
    @DisplayName("un horizonte no soportado falla antes de llamar a la IA")
    void horizonteNoSoportado_noLlamaALaIA() {
        assertThatThrownBy(() -> service.generar(2))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Horizonte no soportado");

        verifyNoInteractions(aiClient, windowBuilder, repository);
    }

    @Test
    @DisplayName("si la IA no está disponible no persiste nada")
    void iaNoDisponible_noPersiste() {
        when(aiClient.health()).thenReturn(AiStatusResponse.unavailable("caída", AHORA));

        assertThatThrownBy(() -> service.generar(6))
                .isInstanceOf(AiUnavailableException.class);

        verify(windowBuilder, never()).construir(anyInt());
        verify(repository, never()).saveAll(anyList());
    }

    @Test
    @DisplayName("si falta historia, el error es de negocio y no se llama a la IA")
    void ventanaIncompleta_noLlamaALaIA() {
        when(aiClient.health()).thenReturn(disponible());
        when(windowBuilder.construir(24)).thenThrow(new IncompleteWindowException("N2", 5, 24));

        assertThatThrownBy(() -> service.generar(6))
                .isInstanceOf(IncompleteWindowException.class);

        verify(aiClient, never()).predict(anyInt(), any(Instant.class), any(double[][][].class));
        verify(repository, never()).saveAll(anyList());
    }

    @Test
    @DisplayName("estadoIa delega en el cliente sin inventar disponibilidad")
    void estadoIa_delegaEnElCliente() {
        AiStatusResponse caido = AiStatusResponse.unavailable("sin conexión", AHORA);
        when(aiClient.health()).thenReturn(caido);

        assertThat(service.estadoIa()).isEqualTo(caido);
    }
}
