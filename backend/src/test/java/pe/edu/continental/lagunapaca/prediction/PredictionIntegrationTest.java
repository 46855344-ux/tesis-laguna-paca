package pe.edu.continental.lagunapaca.prediction;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import pe.edu.continental.lagunapaca.prediction.api.AiStatusResponse;
import pe.edu.continental.lagunapaca.prediction.client.AiClient;
import pe.edu.continental.lagunapaca.prediction.client.AiPredictionResult;
import pe.edu.continental.lagunapaca.prediction.domain.Prediction;
import pe.edu.continental.lagunapaca.prediction.domain.PredictionRepository;
import pe.edu.continental.lagunapaca.prediction.domain.PredictionStatus;
import pe.edu.continental.lagunapaca.station.StationCatalog;
import pe.edu.continental.lagunapaca.telemetry.domain.WaterQualitySample;
import pe.edu.continental.lagunapaca.telemetry.domain.WaterQualitySampleRepository;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integración real: backend + H2 + cliente de IA simulado.
 *
 * <p>Comprueba lo que los slices no pueden ver: que generar una predicción la
 * persista, que una medición posterior la evalúe, que la ingesta siga
 * funcionando con la IA caída y que el {@code sample_id} haga idempotente al
 * POST.</p>
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:lagunapaca-predictions;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("Integración de predicciones")
class PredictionIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private WaterQualitySampleRepository samples;

    @Autowired
    private PredictionRepository predictions;

    @MockBean
    private AiClient aiClient;

    @BeforeEach
    void setUp() {
        predictions.deleteAll();
        samples.deleteAll();

        when(aiClient.health()).thenReturn(new AiStatusResponse(
                true, "stgnn-test", List.of(1, 3, 6), 24, "synthetic", null, Instant.now()));
        when(aiClient.predict(anyInt(), any(Instant.class), any(double[][][].class)))
                .thenAnswer(invocation -> {
                    int horizon = invocation.getArgument(0);
                    Instant baseTime = invocation.getArgument(1);
                    return new AiPredictionResult(
                            "stgnn-test", "synthetic", horizon, Instant.now(),
                            baseTime.plus(horizon, ChronoUnit.HOURS),
                            StationCatalog.IDS.stream()
                                    .map(id -> new AiPredictionResult.StationPrediction(id, 7.2))
                                    .toList());
                });
    }

    /** 24 muestras horarias por estación que terminan hace 6 h (objetivo ≈ ahora). */
    private void sembrarVentana() {
        Instant ultima = Instant.now().minus(6, ChronoUnit.HOURS);
        for (String estacion : StationCatalog.IDS) {
            for (int i = 0; i < 24; i++) {
                samples.save(new WaterQualitySample(
                        estacion, 7.1, 15.0, 18.0, 320.0,
                        ultima.minus(23 - i, ChronoUnit.HOURS)));
            }
        }
    }

    @Test
    @DisplayName("POST /predictions persiste cuatro filas pendientes")
    void generarPrediccion_persisteCuatroFilas() throws Exception {
        sembrarVentana();

        mockMvc.perform(post("/api/v1/predictions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"horizon_hours\":6}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.length()").value(4))
                .andExpect(jsonPath("$[0].status").value("PENDING"));

        assertThat(predictions.count()).isEqualTo(4);
    }

    @Test
    @DisplayName("una medición real en el objetivo evalúa la predicción sin tocarla")
    void medicionPosterior_evaluaLaPrediccion() throws Exception {
        sembrarVentana();
        mockMvc.perform(post("/api/v1/predictions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"horizon_hours\":6}"))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/telemetry")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sample_id":"N1-1","node_id":"N1","ph":7.6,
                                 "turbidity_ntu":15.0,"temp_c":18.0,"tds_ppm":320}
                                """))
                .andExpect(status().isCreated());

        Prediction n1 = predictions.findByNodeId("N1", PageRequest.of(0, 10))
                .getContent().get(0);
        assertThat(n1.getStatus()).isEqualTo(PredictionStatus.EVALUATED);
        assertThat(n1.getObservedValue()).isEqualTo(7.6);
        assertThat(n1.getError()).isCloseTo(-0.4, org.assertj.core.data.Offset.offset(1e-9));
        assertThat(n1.getPredictedValue()).isEqualTo(7.2);

        // Las demás estaciones siguen pendientes: no se evaluaron con datos de N1.
        assertThat(predictions.findByNodeId("N2", PageRequest.of(0, 10))
                .getContent().get(0).getStatus()).isEqualTo(PredictionStatus.PENDING);
    }

    @Test
    @DisplayName("el mismo sample_id dos veces responde 201 y 200 y crea una sola fila")
    void sampleIdRepetido_esIdempotente() throws Exception {
        String payload = """
                {"sample_id":"N1-42","node_id":"N1","ph":7.2,
                 "turbidity_ntu":15.0,"temp_c":18.0,"tds_ppm":320}
                """;

        mockMvc.perform(post("/api/v1/telemetry")
                        .contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/telemetry")
                        .contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isOk());

        assertThat(samples.count()).isEqualTo(1);
        assertThat(samples.findBySampleId("N1-42")).isPresent();
    }

    @Test
    @DisplayName("con la IA caída, la predicción responde 503 pero la ingesta sigue funcionando")
    void iaCaida_noImpideLaIngesta() throws Exception {
        when(aiClient.health()).thenReturn(AiStatusResponse.unavailable("sin conexión", Instant.now()));
        sembrarVentana();

        mockMvc.perform(post("/api/v1/predictions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"horizon_hours\":6}"))
                .andExpect(status().isServiceUnavailable());

        mockMvc.perform(post("/api/v1/telemetry")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"node_id":"N1","ph":9.7,"turbidity_ntu":15.0,
                                 "temp_c":18.0,"tds_ppm":320}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.alert").value(true));

        assertThat(samples.count()).isEqualTo(97);
        assertThat(predictions.count()).isZero();
    }
}
