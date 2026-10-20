package pe.edu.continental.lagunapaca.panel;

import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Recorre el panel web como lo haría una persona: llega sin sesión, inicia
 * sesión y entra al panel.
 *
 * <p>La prueba también fija una decisión de arquitectura: el panel y la API
 * usan <strong>mecanismos de autenticación distintos</strong>. El panel vive en
 * una sesión con formulario y CSRF; la API sigue siendo stateless con JWT. Un
 * JWT válido no abre el panel, y la ausencia de token no convierte el 401 de la
 * API en una redirección.</p>
 *
 * <p>Contra H2 por la misma razón que {@code ApiIntegrationTest}: la migración
 * Flyway es específica de PostgreSQL.</p>
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:lagunapaca-panel;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureMockMvc
@DisplayName("Panel web: shell, sesión y convivencia con la API")
class PanelIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("la raíz del sitio lleva al panel, no al 401 en JSON de la API")
    void laRaizRedirigeAlPanel() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/panel"));
    }

    @Test
    @DisplayName("el panel exige iniciar sesión: sin sesión redirige al formulario")
    void panelSinSesionRedirigeAlLogin() throws Exception {
        mockMvc.perform(get("/panel"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("http://localhost/login"));
    }

    @Test
    @DisplayName("el formulario de login se sirve sin autenticación y está en español")
    void elFormularioDeLoginEsPublico() throws Exception {
        mockMvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Iniciar sesión")))
                .andExpect(content().string(containsString("Laguna Paca")))
                .andExpect(content().string(containsString("Contraseña")));
    }

    @Test
    @DisplayName("los recursos estáticos se sirven sin sesión")
    void losRecursosEstaticosSonPublicos() throws Exception {
        mockMvc.perform(get("/css/tokens.css"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("--color-primary")));
    }

    @Test
    @DisplayName("con credenciales válidas se entra al panel")
    void conCredencialesValidasSeAccedeAlPanel() throws Exception {
        MvcResult result = mockMvc.perform(formLogin().user("admin").password("admin123"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/panel"))
                .andReturn();

        HttpSession session = result.getRequest().getSession(false);
        assertNotNull(session, "el login por formulario debe abrir una sesión");

        mockMvc.perform(get("/panel").session((org.springframework.mock.web.MockHttpSession) session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Panel de monitoreo")))
                // El nombre del usuario aparece en la barra superior.
                .andExpect(content().string(containsString("admin")));
    }

    @Test
    @DisplayName("con credenciales inválidas vuelve al login con aviso de error")
    void conCredencialesInvalidasVuelveAlLogin() throws Exception {
        mockMvc.perform(formLogin().user("admin").password("incorrecta"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?error"));
    }

    @Test
    @DisplayName("el envío del formulario exige el token CSRF")
    void elLoginPostExigeTokenCsrf() throws Exception {
        mockMvc.perform(post("/login")
                        .param("username", "admin")
                        .param("password", "admin123"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("un JWT válido no abre el panel: son mecanismos distintos")
    void elPanelNoAceptaJwt() throws Exception {
        String body = mockMvc.perform(post("/api/v1/auth/token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"admin123\"}"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String token = new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(body).get("access_token").asText();

        mockMvc.perform(get("/panel").header("Authorization", "Bearer " + token))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("http://localhost/login"));
    }

    @Test
    @DisplayName("la API conserva su contrato stateless: 401 sin token, no redirección")
    void laApiSigueSiendoStateless() throws Exception {
        mockMvc.perform(get("/api/v1/telemetry"))
                .andExpect(status().isUnauthorized());
    }
}
