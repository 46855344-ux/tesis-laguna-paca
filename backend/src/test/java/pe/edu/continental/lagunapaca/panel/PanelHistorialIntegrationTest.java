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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import pe.edu.continental.lagunapaca.telemetry.domain.AlertCode;
import pe.edu.continental.lagunapaca.telemetry.domain.AlertReason;
import pe.edu.continental.lagunapaca.telemetry.domain.WaterQualitySample;
import pe.edu.continental.lagunapaca.telemetry.domain.WaterQualitySampleRepository;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Contrato del historial: filtros, orden, ventana de fechas y paginación.
 *
 * <p>La SPA consume {@code /panel/api/historial}. Los filtros llegan como texto
 * (no como {@code LocalDate}) para que una fecha mal tecleada no convierta la
 * pantalla en un 400. Las fechas de calendario se interpretan en hora de Lima.</p>
 *
 * <p>Se insertan muestras por el repositorio para fijar {@code recordedAt}, así
 * los tests de fechas no dependen del reloj.</p>
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:lagunapaca-historial;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureMockMvc
@DisplayName("Historial del panel: filtros, orden y paginación")
class PanelHistorialIntegrationTest {

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

    private WaterQualitySample muestra(String nodo, double ph, Instant cuando,
                                       boolean alerta, String mensaje) {
        WaterQualitySample muestra = new WaterQualitySample(nodo, ph, 15.4, 18.5, 320.0, cuando);
        muestra.setAlert(alerta);
        if (alerta && mensaje != null) {
            muestra.setAlertReasons(List.of(new AlertReason(AlertCode.PH_HIGH, mensaje)));
        }
        return repository.save(muestra);
    }

    private String json(MockHttpServletRequestBuilder peticion) throws Exception {
        return mockMvc.perform(peticion.session(sesionAdmin()))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("el historial exige sesión, igual que el resto del panel")
    void exigeSesion() throws Exception {
        mockMvc.perform(get("/panel/api/historial"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("las muestras más recientes aparecen primero")
    void ordenaDeMasRecienteAMasAntigua() throws Exception {
        Instant base = Instant.parse("2026-10-19T12:00:00Z");
        muestra("N1", 7.10, base, false, null);
        muestra("N1", 7.20, base.plus(1, ChronoUnit.MINUTES), false, null);
        WaterQualitySample ultima = muestra("N1", 7.30, base.plus(2, ChronoUnit.MINUTES), false, null);

        mockMvc.perform(get("/panel/api/historial").session(sesionAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.filas", hasSize(3)))
                .andExpect(jsonPath("$.filas[0].id").value(ultima.getId()))
                .andExpect(jsonPath("$.filas[0].ph").value("7.30"));
    }

    @Test
    @DisplayName("pagina de 20 en 20 y la segunda página trae el resto")
    void paginaDeVeinteEnVeinte() throws Exception {
        Instant base = Instant.parse("2026-10-19T12:00:00Z");
        for (int i = 0; i < 25; i++) {
            muestra("N1", 7.0 + i * 0.01, base.plus(i, ChronoUnit.MINUTES), false, null);
        }

        mockMvc.perform(get("/panel/api/historial").session(sesionAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.filas", hasSize(20)))
                .andExpect(jsonPath("$.total").value(25))
                .andExpect(jsonPath("$.pagina").value(1))
                .andExpect(jsonPath("$.desdeFila").value(1))
                .andExpect(jsonPath("$.hastaFila").value(20));

        mockMvc.perform(get("/panel/api/historial").param("page", "1").session(sesionAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.filas", hasSize(5)))
                .andExpect(jsonPath("$.pagina").value(2))
                .andExpect(jsonPath("$.desdeFila").value(21))
                .andExpect(jsonPath("$.hastaFila").value(25));
    }

    @Test
    @DisplayName("una página fuera de rango se ajusta a la última, no se muestra vacía")
    void paginaFueraDeRangoSeAjusta() throws Exception {
        Instant base = Instant.parse("2026-10-19T12:00:00Z");
        for (int i = 0; i < 25; i++) {
            muestra("N1", 7.0, base.plus(i, ChronoUnit.MINUTES), false, null);
        }

        mockMvc.perform(get("/panel/api/historial").param("page", "99").session(sesionAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.filas", hasSize(5)))
                .andExpect(jsonPath("$.pagina").value(2))
                .andExpect(jsonPath("$.desdeFila").value(21))
                .andExpect(jsonPath("$.hastaFila").value(25));
    }

    @Test
    @DisplayName("filtra por estación y no mezcla nodos")
    void filtraPorEstacion() throws Exception {
        Instant base = Instant.parse("2026-10-19T12:00:00Z");
        muestra("N1", 7.10, base, false, null);
        muestra("N2", 9.40, base.plus(1, ChronoUnit.MINUTES), true,
                "pH 9.40 supera el límite superior ECA-Agua (9.0)");
        muestra("N2", 9.50, base.plus(2, ChronoUnit.MINUTES), true,
                "pH 9.50 supera el límite superior ECA-Agua (9.0)");

        mockMvc.perform(get("/panel/api/historial").param("estacion", "N2").session(sesionAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.filas", hasSize(2)))
                .andExpect(jsonPath("$.filas[0].estacion").value("N2"))
                .andExpect(jsonPath("$.filas[1].estacion").value("N2"))
                .andExpect(jsonPath("$.filtros.estacion").value("N2"));
    }

    @Test
    @DisplayName("las fechas se interpretan en hora de Lima, no en UTC")
    void filtraPorFechaEnHoraDeLima() throws Exception {
        // 04:30 UTC del día 18 es 23:30 del día 17 en Lima (UTC-5).
        muestra("N1", 7.20, Instant.parse("2026-10-18T04:30:00Z"), false, null);

        mockMvc.perform(get("/panel/api/historial")
                        .param("desde", "2026-10-17")
                        .param("hasta", "2026-10-17")
                        .session(sesionAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.filas", hasSize(1)));

        mockMvc.perform(get("/panel/api/historial")
                        .param("desde", "2026-10-18")
                        .param("hasta", "2026-10-18")
                        .session(sesionAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.filas", hasSize(0)));
    }

    @Test
    @DisplayName("'solo alertas' oculta las muestras dentro del rango")
    void filtraSoloAlertas() throws Exception {
        Instant base = Instant.parse("2026-10-19T12:00:00Z");
        muestra("N1", 7.20, base, false, null);
        muestra("N2", 9.60, base.plus(1, ChronoUnit.MINUTES), true,
                "pH 9.60 supera el límite superior ECA-Agua (9.0)");

        mockMvc.perform(get("/panel/api/historial").param("soloAlertas", "true").session(sesionAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.filas", hasSize(1)))
                .andExpect(jsonPath("$.filas[0].estado").value("ALERTA"))
                .andExpect(jsonPath("$.filtros.soloAlertas").value(true));
    }

    @Test
    @DisplayName("la búsqueda mira el texto del motivo que redactó el backend")
    void buscaEnElMotivo() throws Exception {
        Instant base = Instant.parse("2026-10-19T12:00:00Z");
        muestra("N1", 9.60, base, true, "pH 9.60 supera el límite superior ECA-Agua (9.0)");
        muestra("N2", 6.20, base.plus(1, ChronoUnit.MINUTES), true,
                "pH 6.20 está por debajo del límite inferior ECA-Agua (6.5)");

        mockMvc.perform(get("/panel/api/historial").param("motivo", "por debajo").session(sesionAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.filas", hasSize(1)))
                .andExpect(jsonPath("$.filas[0].estacion").value("N2"));
    }

    @Test
    @DisplayName("un rango invertido no se aplica y se avisa, en vez de romper")
    void rangoInvertidoSeAvisa() throws Exception {
        Instant base = Instant.parse("2026-10-19T12:00:00Z");
        muestra("N1", 7.20, base, false, null);

        mockMvc.perform(get("/panel/api/historial")
                        .param("desde", "2026-10-19")
                        .param("hasta", "2026-10-17")
                        .session(sesionAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.filas", hasSize(1)))
                .andExpect(jsonPath("$.aviso", containsString("no se aplicó")));
    }

    @Test
    @DisplayName("una fecha con formato inválido se avisa sin devolver un 400")
    void fechaInvalidaSeAvisa() throws Exception {
        muestra("N1", 7.20, Instant.parse("2026-10-19T12:00:00Z"), false, null);

        mockMvc.perform(get("/panel/api/historial").param("desde", "2026-13-45").session(sesionAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.filas", hasSize(1)))
                .andExpect(jsonPath("$.aviso", containsString("no es válida")));
    }

    @Test
    @DisplayName("la paginación conserva los filtros aplicados")
    void laPaginacionConservaLosFiltros() throws Exception {
        Instant base = Instant.parse("2026-10-19T12:00:00Z");
        for (int i = 0; i < 25; i++) {
            muestra("N2", 9.5, base.plus(i, ChronoUnit.MINUTES), true,
                    "pH 9.50 supera el límite superior ECA-Agua (9.0)");
        }

        mockMvc.perform(get("/panel/api/historial").param("estacion", "N2").session(sesionAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.filtros.estacion").value("N2"))
                .andExpect(jsonPath("$.totalPaginas").value(2))
                .andExpect(jsonPath("$.paginas", hasSize(2)));
    }

    @Test
    @DisplayName("sin resultados el JSON lo dice y la SPA puede ofrecer salida")
    void sinResultados() throws Exception {
        mockMvc.perform(get("/panel/api/historial").param("estacion", "N4").session(sesionAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hayResultados").value(false))
                .andExpect(jsonPath("$.filas", hasSize(0)))
                .andExpect(jsonPath("$.total").value(0));
    }
}
