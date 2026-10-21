package pe.edu.continental.lagunapaca.panel;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * El mapa de Google es opcional: se activa con {@code app.maps.google-key},
 * que en producción llega por la variable de entorno {@code GOOGLE_MAPS_API_KEY}.
 *
 * <p>Fija las dos reglas que protegen la clave y la demo: la SPA recibe la
 * clave por el endpoint de sesión, y la clave nunca viaja en el JSON que se
 * refresca cada 20 segundos ni se incrusta en el HTML. El contorno propio sigue
 * siendo el respaldo si la API de Google no carga.</p>
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:lagunapaca-mapa-google;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "app.maps.google-key=clave-de-prueba"
})
@AutoConfigureMockMvc
@DisplayName("Mapa de Google: clave configurada")
class PanelMapaGoogleIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    private MockHttpSession sesionAdmin() throws Exception {
        MvcResult result = mockMvc.perform(formLogin().user("admin").password("admin123"))
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private String texto(String ruta) throws Exception {
        return mockMvc.perform(get(ruta).session(sesionAdmin()))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("la configuración de sesión entrega la clave a la SPA")
    void laSesionEntregaLaClave() throws Exception {
        mockMvc.perform(get("/panel/api/sesion").session(sesionAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.googleMapsApiKey").value("clave-de-prueba"));
    }

    @Test
    @DisplayName("el shell HTML no incrusta la clave: la pide al arrancar la SPA")
    void elShellNoIncrustaLaClave() throws Exception {
        assertThat(texto("/login")).doesNotContain("clave-de-prueba");
        assertThat(texto("/panel")).doesNotContain("clave-de-prueba");
    }

    @Test
    @DisplayName("la clave no viaja en el JSON del refresco")
    void laClaveNoViajaEnElJson() throws Exception {
        String json = texto("/panel/api/datos");
        assertThat(json).doesNotContain("clave-de-prueba");
        assertThat(json).doesNotContain("googleMapsApiKey");
    }
}
