package pe.edu.continental.lagunapaca.panel;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Recorre el panel web como lo haría una persona: llega sin sesión, inicia
 * sesión y entra al shell React.
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
@DisplayName("Panel web: shell React, sesión y convivencia con la API")
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
    @DisplayName("el shell de React se sirve sin autenticación en /login")
    void elFormularioDeLoginEsPublico() throws Exception {
        String html = mockMvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(content().string(containsString("id=\"root\"")))
                .andExpect(content().string(containsString("Laguna Paca")))
                .andReturn()
                .getResponse()
                .getContentAsString();

        // La interfaz se dibuja en el navegador, pero el HTML debe cargar los
        // recursos que Vite compiló dentro del JAR.
        assertTrue(html.contains("/panel/assets/"), "el shell debe referenciar los assets de Vite");
    }

    @Test
    @DisplayName("los recursos estáticos de la SPA se sirven sin sesión")
    void losRecursosEstaticosSonPublicos() throws Exception {
        mockMvc.perform(get("/panel/favicon.svg"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<svg")));

        String html = mockMvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        Matcher matcher = Pattern.compile("(/panel/assets/[^\"']+\\.js)").matcher(html);
        assertTrue(matcher.find(), "el shell debe incluir al menos un script compilado");

        mockMvc.perform(get(matcher.group(1)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("createRoot")));
    }

    @Test
    @DisplayName("con credenciales válidas se abre la sesión y se entra al shell")
    void conCredencialesValidasSeAccedeAlPanel() throws Exception {
        MvcResult result = mockMvc.perform(formLogin().user("admin").password("admin123"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/panel"))
                .andReturn();

        MockHttpSession session = (MockHttpSession) result.getRequest().getSession(false);
        assertNotNull(session, "el login por formulario debe abrir una sesión");

        mockMvc.perform(get("/panel").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("id=\"root\"")))
                .andExpect(content().string(containsString("/panel/assets/")));

        mockMvc.perform(get("/panel/").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("id=\"root\"")));

        mockMvc.perform(get("/panel/metricas").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("id=\"root\"")));

        mockMvc.perform(get("/panel/api/sesion").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usuario").value("admin"))
                .andExpect(jsonPath("$.rol").value("Administrador"))
                .andExpect(jsonPath("$.inicial").value("A"))
                .andExpect(jsonPath("$.estaciones", hasSize(4)));
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
    @DisplayName("el token CSRF se entrega a la SPA sin sesión")
    void elTokenCsrfEsPublico() throws Exception {
        mockMvc.perform(get("/panel/csrf"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.headerName").value("X-CSRF-TOKEN"))
                .andExpect(jsonPath("$.parameterName").value("_csrf"));
    }

    @Test
    @DisplayName("cerrar sesión invalida la sesión y vuelve al login")
    void cerrarSesionInvalidaLaSesion() throws Exception {
        MvcResult acceso = mockMvc.perform(formLogin().user("admin").password("admin123"))
                .andReturn();
        MockHttpSession sesion = (MockHttpSession) acceso.getRequest().getSession(false);

        mockMvc.perform(post("/logout").session(sesion).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?logout"));

        assertTrue(sesion.isInvalid(), "el logout debe invalidar la sesión del servidor");
    }

    @Test
    @DisplayName("cerrar sesión exige CSRF, igual que cualquier POST del panel")
    void cerrarSesionExigeCsrf() throws Exception {
        MvcResult acceso = mockMvc.perform(formLogin().user("admin").password("admin123"))
                .andReturn();
        MockHttpSession sesion = (MockHttpSession) acceso.getRequest().getSession(false);

        mockMvc.perform(post("/logout").session(sesion))
                .andExpect(status().isForbidden());

        assertTrue(!sesion.isInvalid(), "un POST sin token no debe cerrar la sesión");
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
    @DisplayName("la API del panel responde 401, no una redirección, si no hay sesión")
    void laApiDelPanelResponde401SinSesion() throws Exception {
        mockMvc.perform(get("/panel/api/sesion"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.error").value("La sesión caducó. Inicia sesión de nuevo."));
        mockMvc.perform(get("/panel/api/datos"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/panel/api/historial"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/panel/api/alertas"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("la API conserva su contrato stateless: 401 sin token, no redirección")
    void laApiSigueSiendoStateless() throws Exception {
        mockMvc.perform(get("/api/v1/telemetry"))
                .andExpect(status().isUnauthorized());
    }
}
