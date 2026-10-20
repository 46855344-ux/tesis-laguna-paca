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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Contrato del historial: filtros, orden, ventana de fechas y paginación.
 *
 * <p>Se insertan muestras por el repositorio para fijar {@code recordedAt}, así
 * los tests de fechas no dependen del reloj. Las aserciones cuentan filas por el
 * atributo {@code data-muestra}, en vez de buscar texto suelto que podría
 * aparecer también en el formulario de filtros.</p>
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

    private static final String MARCA_FILA = "data-muestra=\"";

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

    private String html(MockHttpServletRequestBuilder peticion) throws Exception {
        return mockMvc.perform(peticion.session(sesionAdmin()))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
    }

    /** Cuenta filas de la tabla sin depender de un parser HTML en los tests. */
    private static int filas(String html) {
        int total = 0;
        int desde = 0;
        while ((desde = html.indexOf(MARCA_FILA, desde)) >= 0) {
            total++;
            desde += MARCA_FILA.length();
        }
        return total;
    }

    @Test
    @DisplayName("el historial exige sesión, igual que el resto del panel")
    void exigeSesion() throws Exception {
        mockMvc.perform(get("/panel/historial"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("http://localhost/login"));
    }

    @Test
    @DisplayName("las muestras más recientes aparecen primero")
    void ordenaDeMasRecienteAMasAntigua() throws Exception {
        Instant base = Instant.parse("2026-10-19T12:00:00Z");
        WaterQualitySample primera = muestra("N1", 7.10, base, false, null);
        muestra("N1", 7.20, base.plus(1, ChronoUnit.MINUTES), false, null);
        WaterQualitySample ultima = muestra("N1", 7.30, base.plus(2, ChronoUnit.MINUTES), false, null);

        String html = html(get("/panel/historial"));

        assertThat(filas(html)).isEqualTo(3);
        assertThat(html.indexOf("data-muestra=\"" + ultima.getId() + "\""))
                .isLessThan(html.indexOf("data-muestra=\"" + primera.getId() + "\""));
    }

    @Test
    @DisplayName("pagina de 20 en 20 y la segunda página trae el resto")
    void paginaDeVeinteEnVeinte() throws Exception {
        Instant base = Instant.parse("2026-10-19T12:00:00Z");
        for (int i = 0; i < 25; i++) {
            muestra("N1", 7.0 + i * 0.01, base.plus(i, ChronoUnit.MINUTES), false, null);
        }

        String primera = html(get("/panel/historial"));
        String segunda = html(get("/panel/historial").param("page", "1"));

        assertThat(filas(primera)).isEqualTo(20);
        assertThat(filas(segunda)).isEqualTo(5);
        assertThat(primera).contains("25 muestras");
        assertThat(segunda).contains("21–25");
    }

    @Test
    @DisplayName("una página fuera de rango se ajusta a la última, no se muestra vacía")
    void paginaFueraDeRangoSeAjusta() throws Exception {
        Instant base = Instant.parse("2026-10-19T12:00:00Z");
        for (int i = 0; i < 25; i++) {
            muestra("N1", 7.0, base.plus(i, ChronoUnit.MINUTES), false, null);
        }

        String html = html(get("/panel/historial").param("page", "99"));

        assertThat(filas(html)).isEqualTo(5);
        assertThat(html).contains("21–25");
    }

    @Test
    @DisplayName("filtra por estación y no mezcla nodos")
    void filtraPorEstacion() throws Exception {
        Instant base = Instant.parse("2026-10-19T12:00:00Z");
        muestra("N1", 7.10, base, false, null);
        muestra("N2", 9.40, base.plus(1, ChronoUnit.MINUTES), true, "pH 9.40 supera el límite superior ECA-Agua (9.0)");
        muestra("N2", 9.50, base.plus(2, ChronoUnit.MINUTES), true, "pH 9.50 supera el límite superior ECA-Agua (9.0)");

        String html = html(get("/panel/historial").param("estacion", "N2"));

        assertThat(filas(html)).isEqualTo(2);
        assertThat(html).contains("data-estacion=\"N2\"");
        assertThat(html).doesNotContain("data-estacion=\"N1\"");
    }

    @Test
    @DisplayName("las fechas se interpretan en hora de Lima, no en UTC")
    void filtraPorFechaEnHoraDeLima() throws Exception {
        // 04:30 UTC del día 18 es 23:30 del día 17 en Lima (UTC-5).
        muestra("N1", 7.20, Instant.parse("2026-10-18T04:30:00Z"), false, null);

        String dia17 = html(get("/panel/historial")
                .param("desde", "2026-10-17")
                .param("hasta", "2026-10-17"));
        String dia18 = html(get("/panel/historial")
                .param("desde", "2026-10-18")
                .param("hasta", "2026-10-18"));

        assertThat(filas(dia17)).isEqualTo(1);
        assertThat(filas(dia18)).isZero();
    }

    @Test
    @DisplayName("'solo alertas' oculta las muestras dentro del rango")
    void filtraSoloAlertas() throws Exception {
        Instant base = Instant.parse("2026-10-19T12:00:00Z");
        muestra("N1", 7.20, base, false, null);
        muestra("N2", 9.60, base.plus(1, ChronoUnit.MINUTES), true,
                "pH 9.60 supera el límite superior ECA-Agua (9.0)");

        String html = html(get("/panel/historial").param("soloAlertas", "true"));

        assertThat(filas(html)).isEqualTo(1);
        assertThat(html).contains("Alerta ECA-Agua");
        assertThat(html).doesNotContain("7.20");
    }

    @Test
    @DisplayName("la búsqueda mira el texto del motivo que redactó el backend")
    void buscaEnElMotivo() throws Exception {
        Instant base = Instant.parse("2026-10-19T12:00:00Z");
        muestra("N1", 9.60, base, true, "pH 9.60 supera el límite superior ECA-Agua (9.0)");
        muestra("N2", 6.20, base.plus(1, ChronoUnit.MINUTES), true,
                "pH 6.20 está por debajo del límite inferior ECA-Agua (6.5)");

        String html = html(get("/panel/historial").param("motivo", "por debajo"));

        assertThat(filas(html)).isEqualTo(1);
        assertThat(html).contains("data-estacion=\"N2\"");
    }

    @Test
    @DisplayName("un rango invertido no se aplica y se avisa, en vez de romper")
    void rangoInvertidoSeAvisa() throws Exception {
        Instant base = Instant.parse("2026-10-19T12:00:00Z");
        muestra("N1", 7.20, base, false, null);

        String html = html(get("/panel/historial")
                .param("desde", "2026-10-19")
                .param("hasta", "2026-10-17"));

        assertThat(filas(html)).isEqualTo(1);
        assertThat(html).contains("no se aplicó");
    }

    @Test
    @DisplayName("una fecha con formato inválido se avisa sin devolver un 400")
    void fechaInvalidaSeAvisa() throws Exception {
        muestra("N1", 7.20, Instant.parse("2026-10-19T12:00:00Z"), false, null);

        String html = html(get("/panel/historial").param("desde", "2026-13-45"));

        assertThat(filas(html)).isEqualTo(1);
        assertThat(html).contains("no es válida");
    }

    @Test
    @DisplayName("los enlaces de paginación conservan los filtros aplicados")
    void laPaginacionConservaLosFiltros() throws Exception {
        Instant base = Instant.parse("2026-10-19T12:00:00Z");
        for (int i = 0; i < 25; i++) {
            muestra("N2", 9.5, base.plus(i, ChronoUnit.MINUTES), true,
                    "pH 9.50 supera el límite superior ECA-Agua (9.0)");
        }

        String html = html(get("/panel/historial").param("estacion", "N2"));

        assertThat(html).contains("estacion=N2");
        assertThat(html).contains("page=1");
    }

    @Test
    @DisplayName("sin resultados muestra un estado vacío con salida a quitar filtros")
    void sinResultadosMuestraEstadoVacio() throws Exception {
        String html = html(get("/panel/historial").param("estacion", "N4"));

        assertThat(filas(html)).isZero();
        assertThat(html).contains("Sin resultados");
        assertThat(html).contains("Quitar los filtros");
    }
}
