package pe.edu.continental.lagunapaca.panel;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Contrato de la gestión de alertas: qué se lista, quién puede atender y qué
 * queda registrado.
 *
 * <p>La atención es un dato nuevo sobre la muestra; el veredicto ECA-Agua no se
 * reescribe. Los tests fijan justamente eso: después de atender, la muestra
 * sigue teniendo {@code alert = true}.</p>
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:lagunapaca-alertas;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureMockMvc
@DisplayName("Gestión de alertas: listado, filtros y atención")
class PanelAlertasIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private WaterQualitySampleRepository repository;

    @BeforeEach
    void limpiarBase() {
        repository.deleteAll();
    }

    private MockHttpSession sesionAdmin() throws Exception {
        MvcResult result = mockMvc.perform(formLogin().user("admin").password("admin123"))
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    /** Muestra con alerta, guardada directamente para fijar la hora. */
    private WaterQualitySample alerta(String nodo, double ph, Instant cuando) {
        WaterQualitySample muestra = new WaterQualitySample(nodo, ph, 15.4, 18.5, 320.0, cuando);
        muestra.setAlert(true);
        muestra.setAlertReasons(List.of(
                new AlertReason(AlertCode.PH_HIGH, "pH " + ph + " supera el límite superior ECA-Agua (9.0)")));
        return repository.save(muestra);
    }

    /** Muestra sin alerta: no debe aparecer nunca en esta pantalla. */
    private WaterQualitySample normal(String nodo, double ph, Instant cuando) {
        WaterQualitySample muestra = new WaterQualitySample(nodo, ph, 15.4, 18.5, 320.0, cuando);
        muestra.setAlert(false);
        return repository.save(muestra);
    }

    @Test
    @DisplayName("la gestión de alertas exige sesión")
    void exigeSesion() throws Exception {
        mockMvc.perform(get("/panel/api/alertas"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("solo lista muestras con alerta")
    void soloListaAlertas() throws Exception {
        Instant base = Instant.parse("2026-10-19T12:00:00Z");
        normal("N1", 7.20, base);
        alerta("N2", 9.60, base.plus(1, ChronoUnit.MINUTES));

        mockMvc.perform(get("/panel/api/alertas").session(sesionAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.filas", hasSize(1)))
                .andExpect(jsonPath("$.filas[0].estacion").value("N2"))
                .andExpect(jsonPath("$.total").value(1));
    }

    @Test
    @DisplayName("por defecto muestra pendientes y esconde las ya atendidas")
    void porDefectoMuestraSoloPendientes() throws Exception {
        Instant base = Instant.parse("2026-10-19T12:00:00Z");
        alerta("N1", 9.60, base);
        WaterQualitySample atendida = alerta("N2", 6.20, base.plus(1, ChronoUnit.MINUTES));
        atendida.acknowledge(Instant.now().minus(10, ChronoUnit.MINUTES), "admin", null);
        repository.save(atendida);

        mockMvc.perform(get("/panel/api/alertas").session(sesionAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.filas", hasSize(1)))
                .andExpect(jsonPath("$.filas[0].estacion").value("N1"))
                .andExpect(jsonPath("$.filtros.estado").value("pendientes"));
    }

    @Test
    @DisplayName("el filtro 'atendidas' muestra las que ya se revisaron")
    void filtraAtendidas() throws Exception {
        Instant base = Instant.parse("2026-10-19T12:00:00Z");
        alerta("N1", 9.60, base);
        WaterQualitySample atendida = alerta("N2", 6.20, base.plus(1, ChronoUnit.MINUTES));
        atendida.acknowledge(Instant.now().minus(10, ChronoUnit.MINUTES), "admin", "Se avisó a la municipalidad");
        repository.save(atendida);

        mockMvc.perform(get("/panel/api/alertas").param("estado", "atendidas").session(sesionAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.filas", hasSize(1)))
                .andExpect(jsonPath("$.filas[0].estacion").value("N2"))
                .andExpect(jsonPath("$.filas[0].atendida").value(true))
                .andExpect(jsonPath("$.filas[0].atendidaPor").value("admin"))
                .andExpect(jsonPath("$.filas[0].nota").value("Se avisó a la municipalidad"));
    }

    @Test
    @DisplayName("filtra por estación")
    void filtraPorEstacion() throws Exception {
        Instant base = Instant.parse("2026-10-19T12:00:00Z");
        alerta("N1", 9.60, base);
        alerta("N2", 9.70, base.plus(1, ChronoUnit.MINUTES));
        alerta("N2", 9.80, base.plus(2, ChronoUnit.MINUTES));

        mockMvc.perform(get("/panel/api/alertas").param("estacion", "N2").session(sesionAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.filas", hasSize(2)))
                .andExpect(jsonPath("$.filas[0].estacion").value("N2"))
                .andExpect(jsonPath("$.filas[1].estacion").value("N2"))
                .andExpect(jsonPath("$.filtros.estacion").value("N2"));
    }

    @Test
    @DisplayName("los contadores separan pendientes de atendidas")
    void losContadoresSeparanPendientesDeAtendidas() throws Exception {
        Instant ahora = Instant.now();
        alerta("N1", 9.60, ahora.minus(2, ChronoUnit.HOURS));
        alerta("N1", 9.70, ahora.minus(90, ChronoUnit.MINUTES));
        WaterQualitySample atendida = alerta("N2", 6.20, ahora.minus(1, ChronoUnit.HOURS));
        atendida.acknowledge(ahora.minus(30, ChronoUnit.MINUTES), "admin", null);
        repository.save(atendida);

        mockMvc.perform(get("/panel/api/alertas").param("estado", "todas").session(sesionAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pendientes").value(2))
                .andExpect(jsonPath("$.atendidas24h").value(1))
                .andExpect(jsonPath("$.alertas24h").value(3));
    }

    @Test
    @DisplayName("atender exige CSRF: no basta con tener sesión")
    void atenderExigeCsrf() throws Exception {
        WaterQualitySample pendiente = alerta("N1", 9.60, Instant.now());

        mockMvc.perform(post("/panel/api/alertas/{id}/atender", pendiente.getId())
                        .session(sesionAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nota\":\"intento sin token\"}"))
                .andExpect(status().isForbidden());

        assertThat(repository.findById(pendiente.getId()).orElseThrow().isAcknowledged()).isFalse();
    }

    @Test
    @DisplayName("atender registra usuario, hora y nota, y conserva el veredicto ECA-Agua")
    void atenderRegistraLaAtencion() throws Exception {
        WaterQualitySample pendiente = alerta("N1", 9.60, Instant.now());
        MockHttpSession sesion = sesionAdmin();

        mockMvc.perform(post("/panel/api/alertas/{id}/atender", pendiente.getId())
                        .session(sesion)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nota\":\"Se coordinó con el guardaparque\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mensaje", containsString("Alerta atendida")));

        WaterQualitySample guardada = repository.findById(pendiente.getId()).orElseThrow();
        assertThat(guardada.isAcknowledged()).isTrue();
        assertThat(guardada.getAcknowledgedBy()).isEqualTo("admin");
        assertThat(guardada.getAcknowledgeNote()).isEqualTo("Se coordinó con el guardaparque");
        assertThat(guardada.getAcknowledgedAt()).isNotNull();
        // El veredicto original no se toca: solo se añadió la atención.
        assertThat(guardada.isAlert()).isTrue();

        mockMvc.perform(get("/panel/api/alertas").param("estado", "atendidas").session(sesion))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.filas[0].atendidaPor").value("admin"))
                .andExpect(jsonPath("$.filas[0].nota").value("Se coordinó con el guardaparque"));
    }

    @Test
    @DisplayName("una muestra sin alerta no se puede atender")
    void noSePuedeAtenderUnaMuestraSinAlerta() throws Exception {
        WaterQualitySample sana = normal("N1", 7.20, Instant.now());

        mockMvc.perform(post("/panel/api/alertas/{id}/atender", sana.getId())
                        .session(sesionAdmin())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").isNotEmpty());

        assertThat(repository.findById(sana.getId()).orElseThrow().isAcknowledged()).isFalse();
    }

    @Test
    @DisplayName("una alerta inexistente responde 404, no 500")
    void alertaInexistenteResponde404() throws Exception {
        mockMvc.perform(post("/panel/api/alertas/{id}/atender", 999_999L)
                        .session(sesionAdmin())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("reabrir devuelve la alerta a pendientes y borra la auditoría")
    void reabrirDejaLaAlertaPendiente() throws Exception {
        WaterQualitySample atendida = alerta("N2", 9.60, Instant.now());
        atendida.acknowledge(Instant.now().minus(20, ChronoUnit.MINUTES), "admin", "Revisado");
        repository.save(atendida);

        mockMvc.perform(post("/panel/api/alertas/{id}/reabrir", atendida.getId())
                        .session(sesionAdmin())
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mensaje", containsString("pendientes")));

        WaterQualitySample guardada = repository.findById(atendida.getId()).orElseThrow();
        assertThat(guardada.isAcknowledged()).isFalse();
        assertThat(guardada.getAcknowledgedBy()).isNull();
        assertThat(guardada.getAcknowledgeNote()).isNull();
        assertThat(guardada.isAlert()).isTrue();
    }

    @Test
    @DisplayName("el tablero avisa que la última alerta de la estación ya se atendió")
    void elTableroIndicaLaAlertaAtendida() throws Exception {
        WaterQualitySample atendida = alerta("N3", 9.60, Instant.now().minus(5, ChronoUnit.MINUTES));
        atendida.acknowledge(Instant.now().minus(2, ChronoUnit.MINUTES), "admin", null);
        repository.save(atendida);

        mockMvc.perform(get("/panel/api/datos").session(sesionAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estaciones[2].id").value("N3"))
                .andExpect(jsonPath("$.estaciones[2].estado").value("ALERTA"))
                .andExpect(jsonPath("$.estaciones[2].atendida").value(true))
                .andExpect(jsonPath("$.estaciones[2].atendidaPor").value("admin"));
    }
}
