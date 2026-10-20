package pe.edu.continental.lagunapaca.panel;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import pe.edu.continental.lagunapaca.telemetry.domain.AlertCode;
import pe.edu.continental.lagunapaca.telemetry.domain.AlertReason;
import pe.edu.continental.lagunapaca.telemetry.domain.WaterQualitySample;
import pe.edu.continental.lagunapaca.telemetry.domain.WaterQualitySampleRepository;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Contrato del tablero: qué ve una persona cuando ya existen muestras reales.
 *
 * <p>Se prueban dos caminos del mismo modelo: el HTML renderizado en el
 * servidor (que debe funcionar aunque el navegador no ejecute JavaScript) y el
 * JSON de {@code /panel/datos}, que alimenta el refresco en vivo y el gráfico.</p>
 *
 * <p>Las muestras se insertan directamente en el repositorio, no por la API,
 * para poder fijar {@code recordedAt} y comprobar el corte de 24 horas sin
 * depender del reloj del sistema.</p>
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:lagunapaca-tablero;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureMockMvc
@DisplayName("Tablero del panel: datos reales, estado ECA-Agua y serie temporal")
class PanelDashboardIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private WaterQualitySampleRepository repository;

    @BeforeEach
    void limpiarBase() {
        repository.deleteAll();
    }

    /** Inicia sesión de verdad: el panel no acepta JWT, así que se usa el formulario. */
    private MockHttpSession sesionAdmin() throws Exception {
        MvcResult result = mockMvc.perform(formLogin().user("admin").password("admin123"))
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private void muestra(String nodo, double ph, Instant cuando, boolean alerta, String mensaje) {
        WaterQualitySample muestra = new WaterQualitySample(nodo, ph, 15.4, 18.5, 320.0, cuando);
        muestra.setAlert(alerta);
        if (alerta) {
            muestra.setAlertReasons(List.of(new AlertReason(AlertCode.PH_HIGH, mensaje)));
        }
        repository.save(muestra);
    }

    @Test
    @DisplayName("sin muestras, el tablero sigue mostrando las cuatro estaciones")
    void sinMuestrasSeVenLasCuatroEstaciones() throws Exception {
        mockMvc.perform(get("/panel").session(sesionAdmin()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("N1")))
                .andExpect(content().string(containsString("N4")))
                .andExpect(content().string(containsString("Sin datos")));
    }

    @Test
    @DisplayName("la tarjeta muestra la muestra MÁS RECIENTE, no la primera")
    void laTarjetaMuestraLaUltimaMuestra() throws Exception {
        Instant ahora = Instant.now();
        muestra("N1", 7.20, ahora.minus(2, ChronoUnit.HOURS), false, null);
        muestra("N1", 7.35, ahora.minus(3, ChronoUnit.MINUTES), false, null);

        mockMvc.perform(get("/panel").session(sesionAdmin()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("7.35")))
                .andExpect(content().string(containsString("Dentro del rango")));
    }

    @Test
    @DisplayName("una muestra con alerta se marca y muestra el motivo decidido por el backend")
    void laAlertaSeMuestraConSuMotivo() throws Exception {
        String motivo = "pH 9.60 supera el límite superior ECA-Agua (9.0)";
        muestra("N2", 9.60, Instant.now().minus(5, ChronoUnit.MINUTES), true, motivo);

        mockMvc.perform(get("/panel").session(sesionAdmin()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Alerta ECA-Agua")))
                .andExpect(content().string(containsString("supera el límite superior")));
    }

    @Test
    @DisplayName("el JSON del tablero trae las cuatro estaciones, su estado y la serie")
    void elJsonTraeEstacionesYSerie() throws Exception {
        Instant ahora = Instant.now();
        muestra("N1", 7.20, ahora.minus(1, ChronoUnit.HOURS), false, null);
        muestra("N3", 6.40, ahora.minus(30, ChronoUnit.MINUTES), true,
                "pH 6.40 está por debajo del límite inferior ECA-Agua (6.5)");

        mockMvc.perform(get("/panel/datos").session(sesionAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estaciones", hasSize(4)))
                .andExpect(jsonPath("$.estaciones[0].id").value("N1"))
                .andExpect(jsonPath("$.estaciones[0].estado").value("DENTRO"))
                .andExpect(jsonPath("$.estaciones[0].ultima.ph").value("7.20"))
                .andExpect(jsonPath("$.estaciones[1].estado").value("SIN_DATOS"))
                .andExpect(jsonPath("$.estaciones[2].id").value("N3"))
                .andExpect(jsonPath("$.estaciones[2].estado").value("ALERTA"))
                .andExpect(jsonPath("$.estaciones[2].motivos", hasSize(1)))
                .andExpect(jsonPath("$.serie", hasSize(2)))
                .andExpect(jsonPath("$.serie[0].estacion").value("N1"))
                .andExpect(jsonPath("$.muestras").value(2))
                .andExpect(jsonPath("$.alertas").value(1));
    }

    @Test
    @DisplayName("el JSON exige sesión: no es un endpoint público")
    void elJsonExigeSesion() throws Exception {
        mockMvc.perform(get("/panel/datos"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("http://localhost/login"));
    }

    @Test
    @DisplayName("la serie y los contadores ignoran lo anterior a 24 horas")
    void laSerieSoloCubreLasUltimas24Horas() throws Exception {
        Instant ahora = Instant.now();
        muestra("N1", 7.0, ahora.minus(25, ChronoUnit.HOURS), false, null);
        muestra("N1", 7.1, ahora.minus(1, ChronoUnit.HOURS), false, null);

        mockMvc.perform(get("/panel/datos").session(sesionAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.serie", hasSize(1)))
                .andExpect(jsonPath("$.muestras").value(1));
    }
}
