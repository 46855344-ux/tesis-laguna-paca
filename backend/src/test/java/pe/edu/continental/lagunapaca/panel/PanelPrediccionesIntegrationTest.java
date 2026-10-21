package pe.edu.continental.lagunapaca.panel;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import pe.edu.continental.lagunapaca.prediction.domain.Prediction;
import pe.edu.continental.lagunapaca.prediction.domain.PredictionRepository;
import pe.edu.continental.lagunapaca.prediction.service.AiContractException;
import pe.edu.continental.lagunapaca.prediction.service.AiUnavailableException;
import pe.edu.continental.lagunapaca.prediction.service.IncompleteWindowException;
import pe.edu.continental.lagunapaca.prediction.service.PredictionService;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Contrato del panel con predicciones.
 *
 * <p>Fija lo que no puede romperse: la generación es un POST con sesión y CSRF,
 * la IA caída se explica sin romper el tablero, y las predicciones se muestran
 * como tales (nunca como mediciones), declarando si el modelo se entrenó con
 * datos sintéticos.</p>
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:lagunapaca-predicciones-panel;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureMockMvc
@DisplayName("Panel: predicciones del ST-GNN")
class PanelPrediccionesIntegrationTest {

    private static final Instant AHORA = Instant.now();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PredictionRepository predictions;

    @MockBean
    private PredictionService predictionService;

    @BeforeEach
    void limpiarBase() {
        predictions.deleteAll();
    }

    private MockHttpSession sesionAdmin() throws Exception {
        MvcResult result = mockMvc.perform(formLogin().user("admin").password("admin123"))
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private Prediction prediccion(String estacion, double previsto, int horizonte,
                                  Instant objetivo, boolean evaluada) {
        Prediction fila = new Prediction(
                UUID.randomUUID(), estacion, "ph", horizonte,
                AHORA.minus(10, ChronoUnit.MINUTES), objetivo, previsto,
                "stgnn-synthetic-s42-i24-e150", "synthetic");
        if (evaluada) {
            fila.evaluate(7.30, AHORA);
        }
        return predictions.save(fila);
    }

    @Test
    @DisplayName("el tablero muestra la predicción, su horizonte y el origen sintético")
    void tablero_muestraPrediccionYOrigen() throws Exception {
        prediccion("N1", 7.42, 6, AHORA.plus(6, ChronoUnit.HOURS), false);

        mockMvc.perform(get("/panel/api/datos").session(sesionAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.predicciones", hasSize(1)))
                .andExpect(jsonPath("$.predicciones[0].estacion").value("N1"))
                .andExpect(jsonPath("$.predicciones[0].horizonte").value(6))
                .andExpect(jsonPath("$.predicciones[0].phPrevisto").value("7.42"))
                .andExpect(jsonPath("$.predicciones[0].origen").value("Datos sintéticos"))
                .andExpect(jsonPath("$.predicciones[0].estado").value("Pendiente"))
                .andExpect(jsonPath("$.predicciones[0].evaluada").value(false));
    }

    @Test
    @DisplayName("una predicción evaluada muestra el valor observado y el error")
    void tablero_muestraEvaluacion() throws Exception {
        prediccion("N2", 7.20, 3, AHORA.minus(5, ChronoUnit.MINUTES), true);

        mockMvc.perform(get("/panel/api/datos").session(sesionAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.predicciones[0].estado").value("Evaluada"))
                .andExpect(jsonPath("$.predicciones[0].phObservado").value("7.30"))
                .andExpect(jsonPath("$.predicciones[0].error").value("-0.10"))
                .andExpect(jsonPath("$.predicciones[0].evaluada").value(true));
    }

    @Test
    @DisplayName("sin predicciones el JSON queda vacío para que la SPA muestre su estado honesto")
    void tablero_sinPredicciones_estadoVacio() throws Exception {
        mockMvc.perform(get("/panel/api/datos").session(sesionAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.predicciones", hasSize(0)));
    }

    @Test
    @DisplayName("generar sin sesión responde 401, no redirige")
    void generar_sinSesion_401() throws Exception {
        mockMvc.perform(post("/panel/api/predicciones")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"horizonte\":6}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("generar sin CSRF responde 403")
    void generar_sinCsrf_prohibido() throws Exception {
        mockMvc.perform(post("/panel/api/predicciones")
                        .session(sesionAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"horizonte\":6}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("generar con sesión y CSRF delega en el servicio y confirma en JSON")
    void generar_conSesionYCsrf_delegaYConfirma() throws Exception {
        when(predictionService.generar(6)).thenReturn(List.of());

        mockMvc.perform(post("/panel/api/predicciones")
                        .session(sesionAdmin())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"horizonte\":6}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mensaje", containsString("Predicción generada")));

        verify(predictionService).generar(6);
    }

    @Test
    @DisplayName("con la IA caída el panel explica el motivo y sigue funcionando")
    void generar_iaCaida_avisaSinRomperElTablero() throws Exception {
        when(predictionService.generar(3))
                .thenThrow(new AiUnavailableException("No se pudo contactar al servicio de IA"));

        mockMvc.perform(post("/panel/api/predicciones")
                        .session(sesionAdmin())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"horizonte\":3}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error", containsString("No se pudo contactar")));

        mockMvc.perform(get("/panel/api/datos").session(sesionAdmin()))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("una ventana incompleta se explica con 409 y no inventa una predicción")
    void generar_ventanaIncompleta_409() throws Exception {
        when(predictionService.generar(1))
                .thenThrow(new IncompleteWindowException("N1", 3, 24));

        mockMvc.perform(post("/panel/api/predicciones")
                        .session(sesionAdmin())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"horizonte\":1}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error", containsString("solo tiene 3 muestras")));
    }

    @Test
    @DisplayName("una respuesta inválida de la IA se traduce a 502")
    void generar_contratoInvalido_502() throws Exception {
        when(predictionService.generar(6))
                .thenThrow(new AiContractException("La IA devolvió una forma inesperada"));

        mockMvc.perform(post("/panel/api/predicciones")
                        .session(sesionAdmin())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"horizonte\":6}"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.error", containsString("forma inesperada")));
    }

    @Test
    @DisplayName("un horizonte no soportado se rechaza con 400, no con error 500")
    void generar_horizonteInvalido_400() throws Exception {
        when(predictionService.generar(0))
                .thenThrow(new IllegalArgumentException("Horizonte no soportado: 0. Use 1, 3 o 6 horas."));

        mockMvc.perform(post("/panel/api/predicciones")
                        .session(sesionAdmin())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"horizonte\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", containsString("Horizonte no soportado")));
    }

    @Test
    @DisplayName("un cuerpo sin horizonte se rechaza con 400")
    void generar_sinHorizonte_400() throws Exception {
        when(predictionService.generar(-1))
                .thenThrow(new IllegalArgumentException("Horizonte no soportado: -1."));

        mockMvc.perform(post("/panel/api/predicciones")
                        .session(sesionAdmin())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }
}
