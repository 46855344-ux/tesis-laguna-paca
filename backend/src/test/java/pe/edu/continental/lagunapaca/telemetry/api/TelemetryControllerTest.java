package pe.edu.continental.lagunapaca.telemetry.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import pe.edu.continental.lagunapaca.telemetry.domain.AlertCode;
import pe.edu.continental.lagunapaca.telemetry.service.IngestOutcome;
import pe.edu.continental.lagunapaca.telemetry.service.TelemetryQuery;
import pe.edu.continental.lagunapaca.telemetry.service.TelemetryService;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Contrato HTTP del endpoint de telemetría. La seguridad se prueba aparte
 * ({@code JwtServiceTest}) y aquí se desactivan los filtros para aislar la capa web.
 */
@WebMvcTest(controllers = TelemetryController.class)
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("Contrato HTTP - /api/v1/telemetry")
class TelemetryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TelemetryService telemetryService;

    private static final String VALID_PAYLOAD = """
            {"node_id":"N1","ph":7.2,"turbidity_ntu":15.4,"temp_c":18.5,"tds_ppm":320}
            """;

    // ---------------------------------------------------------------- ingesta

    @Test
    @DisplayName("POST payload válido responde 201 con la muestra")
    void payloadValido_responde201() throws Exception {
        given(telemetryService.ingest(any())).willReturn(IngestOutcome.created(new TelemetryResponse(
                1L, "N1", 7.2, 15.4, 18.5, 320.0, false, List.of(),
                Instant.parse("2026-10-18T10:00:00Z"))));

        mockMvc.perform(post("/api/v1/telemetry")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_PAYLOAD))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.node_id").value("N1"))
                .andExpect(jsonPath("$.alert").value(false));
    }

    @Test
    @DisplayName("POST con alerta devuelve alert_reasons como {code, message}")
    void post_conAlerta_devuelveCodigoYmensaje() throws Exception {
        given(telemetryService.ingest(any())).willReturn(IngestOutcome.created(new TelemetryResponse(
                2L, "N1", 9.7, 15.4, 18.5, 320.0, true,
                List.of(new AlertReasonDto(AlertCode.PH_HIGH, "pH 9.70 supera el límite superior ECA-Agua (9.0)")),
                Instant.parse("2026-10-18T10:00:00Z"))));

        String payload = """
                {"node_id":"N1","ph":9.7,"turbidity_ntu":15.4,"temp_c":18.5,"tds_ppm":320}
                """;

        mockMvc.perform(post("/api/v1/telemetry")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.alert").value(true))
                .andExpect(jsonPath("$.alert_reasons[0].code").value("PH_HIGH"))
                .andExpect(jsonPath("$.alert_reasons[0].message").exists());
    }

    @Test
    @DisplayName("POST con sample_id repetido responde 200 (idempotente), no 201")
    void sampleIdRepetido_responde200() throws Exception {
        given(telemetryService.ingest(any())).willReturn(IngestOutcome.duplicate(new TelemetryResponse(
                7L, "N1", 7.2, 15.4, 18.5, 320.0, false, List.of(),
                Instant.parse("2026-10-18T10:00:00Z"))));

        String payload = """
                {"sample_id":"N1-7","node_id":"N1","ph":7.2,"turbidity_ntu":15.4,"temp_c":18.5,"tds_ppm":320}
                """;

        mockMvc.perform(post("/api/v1/telemetry")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(7));
    }

    @Test
    @DisplayName("POST con pH fuera de rango responde 400 sin llamar al servicio")
    void phFueraDeRango_responde400() throws Exception {
        String payload = """
                {"node_id":"N1","ph":15.0,"turbidity_ntu":15.4,"temp_c":18.5,"tds_ppm":320}
                """;

        mockMvc.perform(post("/api/v1/telemetry")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.field_errors[0].field").value("ph"));

        verifyNoInteractions(telemetryService);
    }

    @Test
    @DisplayName("POST sin node_id responde 400")
    void sinNodeId_responde400() throws Exception {
        String payload = """
                {"ph":7.2,"turbidity_ntu":15.4,"temp_c":18.5,"tds_ppm":320}
                """;

        mockMvc.perform(post("/api/v1/telemetry")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest());
    }

    // --------------------------------------------------------------- consulta

    @Test
    @DisplayName("GET sin filtros responde 200 con una página")
    void listado_sinFiltros_responde200() throws Exception {
        given(telemetryService.search(any(), any()))
                .willReturn(new PageResponse<>(List.of(), 0, 20, 0, 0));

        mockMvc.perform(get("/api/v1/telemetry"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.total_elements").value(0));
    }

    @Test
    @DisplayName("GET mapea los filtros node_id, alert, reason y code hacia la consulta")
    void listado_mapeaTodosLosFiltros() throws Exception {
        given(telemetryService.search(any(), any()))
                .willReturn(new PageResponse<>(List.of(), 0, 20, 0, 0));

        mockMvc.perform(get("/api/v1/telemetry")
                        .param("node_id", "N1")
                        .param("alert", "true")
                        .param("reason", "superior")
                        .param("code", "PH_HIGH"))
                .andExpect(status().isOk());

        ArgumentCaptor<TelemetryQuery> captor = ArgumentCaptor.forClass(TelemetryQuery.class);
        verify(telemetryService).search(captor.capture(), any());

        TelemetryQuery captured = captor.getValue();
        assertThat(captured.nodeId()).isEqualTo("N1");
        assertThat(captured.alert()).isTrue();
        assertThat(captured.reason()).isEqualTo("superior");
        assertThat(captured.code()).isEqualTo(AlertCode.PH_HIGH);
    }

    @Test
    @DisplayName("GET con from > to responde 400")
    void listado_conRangoInvalido_responde400() throws Exception {
        given(telemetryService.search(any(), any()))
                .willThrow(new IllegalArgumentException("'from' no puede ser posterior a 'to'"));

        mockMvc.perform(get("/api/v1/telemetry")
                        .param("node_id", "N1")
                        .param("from", "2026-10-18T12:00:00Z")
                        .param("to", "2026-10-18T10:00:00Z"))
                .andExpect(status().isBadRequest());
    }
}
