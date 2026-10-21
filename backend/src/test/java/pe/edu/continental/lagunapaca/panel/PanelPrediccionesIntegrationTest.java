package pe.edu.continental.lagunapaca.panel;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import pe.edu.continental.lagunapaca.prediction.domain.Prediction;
import pe.edu.continental.lagunapaca.prediction.domain.PredictionRepository;
import pe.edu.continental.lagunapaca.prediction.service.AiUnavailableException;
import pe.edu.continental.lagunapaca.prediction.service.PredictionService;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
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

        String html = mockMvc.perform(get("/panel").session(sesionAdmin()))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);

        assertThat(html).contains("Predicciones del ST-GNN");
        assertThat(html).contains("N1 · 6 h");
        assertThat(html).contains("7.42");
        assertThat(html).contains("Datos sintéticos");
        assertThat(html).contains("Pendiente");
        // La predicción no se presenta como una alerta ni como una medición.
        assertThat(html).doesNotContain("pH 7.42 dentro");
    }

    @Test
    @DisplayName("una predicción evaluada muestra el valor observado y el error")
    void tablero_muestraEvaluacion() throws Exception {
        prediccion("N2", 7.20, 3, AHORA.minus(5, ChronoUnit.MINUTES), true);

        String html = mockMvc.perform(get("/panel").session(sesionAdmin()))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);

        assertThat(html).contains("Evaluada");
        assertThat(html).contains("Observado: 7.30");
        assertThat(html).contains("error -0.10");
    }

    @Test
    @DisplayName("sin predicciones el panel muestra un estado vacío honesto")
    void tablero_sinPredicciones_estadoVacio() throws Exception {
        String html = mockMvc.perform(get("/panel").session(sesionAdmin()))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);

        assertThat(html).contains("Todavía no hay predicciones");
    }

    @Test
    @DisplayName("generar sin sesión redirige al formulario de acceso")
    void generar_sinSesion_redirigeAlLogin() throws Exception {
        mockMvc.perform(post("/panel/predicciones").with(csrf()).param("horizonte", "6"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("http://localhost/login"));
    }

    @Test
    @DisplayName("generar sin CSRF responde 403")
    void generar_sinCsrf_prohibido() throws Exception {
        mockMvc.perform(post("/panel/predicciones")
                        .session(sesionAdmin())
                        .param("horizonte", "6"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("generar con sesión y CSRF delega en el servicio y confirma en el panel")
    void generar_conSesionYCsrf_delegaYConfirma() throws Exception {
        when(predictionService.generar(6)).thenReturn(List.of());

        MockHttpSession sesion = sesionAdmin();
        mockMvc.perform(post("/panel/predicciones")
                        .session(sesion)
                        .with(csrf())
                        .param("horizonte", "6"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/panel"));

        verify(predictionService).generar(6);

        mockMvc.perform(get("/panel").session(sesion))
                .andExpect(status().isOk())
                .andExpect(content().string(
                        org.hamcrest.Matchers.containsString("Predicción generada")));
    }

    @Test
    @DisplayName("con la IA caída el panel explica el motivo y sigue funcionando")
    void generar_iaCaida_avisaSinRomperElTablero() throws Exception {
        when(predictionService.generar(3))
                .thenThrow(new AiUnavailableException("No se pudo contactar al servicio de IA"));

        MockHttpSession sesion = sesionAdmin();
        mockMvc.perform(post("/panel/predicciones")
                        .session(sesion)
                        .with(csrf())
                        .param("horizonte", "3"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/panel"));

        mockMvc.perform(get("/panel").session(sesion))
                .andExpect(status().isOk())
                .andExpect(content().string(
                        org.hamcrest.Matchers.containsString("No se pudo generar la predicción")))
                .andExpect(content().string(
                        org.hamcrest.Matchers.containsString("Estado de la laguna")));
    }

    @Test
    @DisplayName("un horizonte no numérico se rechaza con aviso, no con error 500")
    void generar_horizonteInvalido_avisa() throws Exception {
        when(predictionService.generar(-1))
                .thenThrow(new IllegalArgumentException("Horizonte no soportado: -1. Use 1, 3 o 6 horas."));

        MockHttpSession sesion = sesionAdmin();
        mockMvc.perform(post("/panel/predicciones")
                        .session(sesion)
                        .with(csrf())
                        .param("horizonte", "seis"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/panel"));

        mockMvc.perform(get("/panel").session(sesion))
                .andExpect(content().string(
                        org.hamcrest.Matchers.containsString("Horizonte no soportado")));
    }
}
