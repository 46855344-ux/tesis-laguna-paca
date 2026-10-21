package pe.edu.continental.lagunapaca.prediction.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import pe.edu.continental.lagunapaca.prediction.service.AiUnavailableException;
import pe.edu.continental.lagunapaca.prediction.service.IncompleteWindowException;
import pe.edu.continental.lagunapaca.prediction.service.PredictionService;
import pe.edu.continental.lagunapaca.telemetry.api.PageResponse;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {PredictionController.class, AiStatusController.class})
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("Contrato HTTP - /api/v1/predictions y /api/v1/ai")
class PredictionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PredictionService predictionService;

    private static PredictionResponse respuesta(String estacion) {
        return new PredictionResponse(
                1L, UUID.fromString("00000000-0000-0000-0000-000000000001"), estacion, "ph", 6,
                Instant.parse("2026-10-20T10:00:00Z"),
                Instant.parse("2026-10-20T16:00:00Z"),
                7.2, "stgnn-test", "synthetic", null, null, null, null);
    }

    @Test
    @DisplayName("POST válido responde 201 con cuatro predicciones")
    void postValido_responde201() throws Exception {
        given(predictionService.generar(6))
                .willReturn(List.of(respuesta("N1"), respuesta("N2"), respuesta("N3"), respuesta("N4")));

        mockMvc.perform(post("/api/v1/predictions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"horizon_hours\":6}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$[0].node_id").value("N1"))
                .andExpect(jsonPath("$[3].node_id").value("N4"))
                .andExpect(jsonPath("$[0].data_source").value("synthetic"));
    }

    @Test
    @DisplayName("POST con horizonte fuera de rango responde 400 sin llamar al servicio")
    void horizonteInvalido_responde400() throws Exception {
        mockMvc.perform(post("/api/v1/predictions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"horizon_hours\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.field_errors[0].field").value("horizonHours"));
    }

    @Test
    @DisplayName("POST con horizonte no soportado responde 400")
    void horizonteNoSoportado_responde400() throws Exception {
        given(predictionService.generar(2)).willThrow(new IllegalArgumentException("Horizonte no soportado: 2"));

        mockMvc.perform(post("/api/v1/predictions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"horizon_hours\":2}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Horizonte no soportado: 2"));
    }

    @Test
    @DisplayName("POST con la IA caída responde 503, no 500")
    void iaCaida_responde503() throws Exception {
        given(predictionService.generar(6))
                .willThrow(new AiUnavailableException("No se pudo contactar al servicio de IA"));

        mockMvc.perform(post("/api/v1/predictions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"horizon_hours\":6}"))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    @DisplayName("POST con ventana incompleta responde 409 y explica qué falta")
    void ventanaIncompleta_responde409() throws Exception {
        given(predictionService.generar(6))
                .willThrow(new IncompleteWindowException("N2", 5, 24));

        mockMvc.perform(post("/api/v1/predictions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"horizon_hours\":6}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("N2")));
    }

    @Test
    @DisplayName("GET responde una página y propaga los filtros")
    void get_listaPaginada() throws Exception {
        given(predictionService.buscar(any(), any(), any()))
                .willReturn(new PageResponse<>(List.of(respuesta("N1")), 0, 20, 1, 1));

        mockMvc.perform(get("/api/v1/predictions")
                        .param("node_id", "N1")
                        .param("horizon_hours", "6"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].node_id").value("N1"))
                .andExpect(jsonPath("$.total_elements").value(1));
    }

    @Test
    @DisplayName("GET /api/v1/ai/health responde 200 aunque la IA esté caída")
    void estadoIa_responde200AunqueCaida() throws Exception {
        given(predictionService.estadoIa())
                .willReturn(AiStatusResponse.unavailable("sin conexión", Instant.parse("2026-10-20T10:00:00Z")));

        mockMvc.perform(get("/api/v1/ai/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(false))
                .andExpect(jsonPath("$.detail").value("sin conexión"));
    }
}
