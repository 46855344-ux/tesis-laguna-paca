package pe.edu.continental.lagunapaca.panel;

import com.jayway.jsonpath.JsonPath;
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

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.lessThan;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Contrato del mapa de la laguna.
 *
 * <p>Fija tres cosas que no se ven a simple vista: que el contorno se calcula
 * en el servidor (sin teselas ni librerías externas), que las estaciones caen
 * dentro del lienzo y en el orden geográfico correcto, y que el color de cada
 * punto es el veredicto ECA-Agua que ya decidió el backend.</p>
 *
 * <p>El dibujo lo hace React; el backend entrega geometría, coordenadas y
 * veredicto. No se duplica la regla ECA-Agua en el navegador.</p>
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:lagunapaca-mapa;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureMockMvc
@DisplayName("Mapa de la laguna: contorno, proyección y veredicto")
class PanelMapaIntegrationTest {

    private static final int ANCHO = 720;
    private static final int ALTO = 460;

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

    private String datos() throws Exception {
        return mockMvc.perform(get("/panel/api/datos").session(sesionAdmin()))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
    }

    private WaterQualitySample alerta(String nodo, double ph, AlertCode codigo, String motivo) {
        WaterQualitySample muestra = new WaterQualitySample(
                nodo, ph, 15.4, 18.5, 320.0, Instant.now());
        muestra.setAlert(true);
        muestra.setAlertReasons(List.of(new AlertReason(codigo, motivo)));
        return repository.save(muestra);
    }

    @Test
    @DisplayName("el JSON trae el contorno y una marca por estación")
    void dibujaElContornoYLasCuatroEstaciones() throws Exception {
        String json = datos();

        assertThat(JsonPath.<String>read(json, "$.mapa.viewBox"))
                .isEqualTo("0 0 " + ANCHO + " " + ALTO);
        assertThat(JsonPath.<String>read(json, "$.mapa.camino"))
                .startsWith("M ")
                .contains(" L ");
        assertThat(JsonPath.<List<String>>read(json, "$.mapa.puntos[*].id"))
                .containsExactly("N1", "N2", "N3", "N4");
    }

    @Test
    @DisplayName("las cuatro estaciones caen dentro del lienzo")
    void lasEstacionesCaenDentroDelLienzo() throws Exception {
        mockMvc.perform(get("/panel/api/datos").session(sesionAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mapa.puntos", hasSize(4)))
                .andExpect(jsonPath("$.mapa.puntos[*].x").value(
                        everyItem(allOf(greaterThan(0), lessThan(ANCHO)))))
                .andExpect(jsonPath("$.mapa.puntos[*].y").value(
                        everyItem(allOf(greaterThan(0), lessThan(ALTO)))))
                // Las coordenadas reales acompañan al punto para que el mapa de
                // Google pueda ubicarlo sin volver a consultar el GeoJSON.
                .andExpect(jsonPath("$.mapa.puntos[*].lat").value(
                        everyItem(allOf(greaterThan(-11.80), lessThan(-11.68)))))
                .andExpect(jsonPath("$.mapa.puntos[*].lon").value(
                        everyItem(allOf(greaterThan(-75.55), lessThan(-75.45)))));
    }

    @Test
    @DisplayName("el norte queda arriba del sur: la proyección respeta la latitud")
    void elNorteQuedaArribaDelSur() throws Exception {
        String json = datos();

        assertThat(JsonPath.<String>read(json, "$.mapa.puntos[0].id")).isEqualTo("N1");
        assertThat(JsonPath.<String>read(json, "$.mapa.puntos[2].id")).isEqualTo("N3");

        int norte = JsonPath.read(json, "$.mapa.puntos[0].y");
        int sur = JsonPath.read(json, "$.mapa.puntos[2].y");

        assertThat(norte)
                .as("N1 está al norte de la laguna y en SVG el eje Y crece hacia abajo")
                .isLessThan(sur);
    }

    @Test
    @DisplayName("el color de cada punto es el veredicto del backend")
    void elColorEsElVeredictoDelBackend() throws Exception {
        alerta("N2", 9.62, AlertCode.PH_HIGH,
                "pH 9.62 supera el límite superior ECA-Agua (9.0)");

        mockMvc.perform(get("/panel/api/datos").session(sesionAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mapa.puntos[1].id").value("N2"))
                .andExpect(jsonPath("$.mapa.puntos[1].clase").value("peligro"))
                .andExpect(jsonPath("$.mapa.puntos[1].ph").value("9.62"))
                // N1 no reportó: gris, no verde. El mapa no inventa un estado.
                .andExpect(jsonPath("$.mapa.puntos[0].clase").value("neutra"));
    }

    @Test
    @DisplayName("el punto describe la estación para lectores de pantalla")
    void elPuntoTieneTextoAccesible() throws Exception {
        alerta("N3", 6.10, AlertCode.PH_LOW,
                "pH 6.10 es inferior al límite ECA-Agua (6.5)");

        mockMvc.perform(get("/panel/api/datos").session(sesionAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mapa.puntos[2].titulo",
                        containsString("N3 · pH 6.10 · Alerta ECA-Agua")));
    }

    @Test
    @DisplayName("el mapa entregado por el backend no depende de teselas ni scripts externos")
    void elMapaNoDependeDeInternet() throws Exception {
        String json = datos();

        assertThat(json).doesNotContain("tile.openstreetmap.org");
        assertThat(json).doesNotContain("leaflet");
        assertThat(json).doesNotContain("maps.googleapis");
        assertThat(json).doesNotContain("googleKey");
        assertThat(JsonPath.<String>read(json, "$.mapa.camino")).isNotEmpty();
    }
}
