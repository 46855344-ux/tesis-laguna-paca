package pe.edu.continental.lagunapaca;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Recorre la API de extremo a extremo: es el mismo camino que se usa en la
 * demostración (documentación interactiva, autenticación, ingesta de una muestra
 * y consulta del histórico con su alerta ECA-Agua).
 *
 * <p>Contra H2 porque la migración Flyway es específica de PostgreSQL; el
 * esquema lo genera Hibernate a partir de las entidades. La validación de Flyway
 * contra PostgreSQL real sigue pendiente.</p>
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:lagunapaca-integration;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureMockMvc
@DisplayName("API de extremo a extremo (recorrido de la demostración)")
class ApiIntegrationTest {

    /** Payload exacto del ESP32, con pH alcalino fuera del rango ECA-Agua. */
    private static final String PAYLOAD_PH_ALTO = """
            {"node_id":"N1","ph":9.5,"turbidity_ntu":15.4,"temp_c":18.5,"tds_ppm":320}""";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("la documentación interactiva se sirve sin autenticación")
    void documentacionInteractivaEsPublica() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/auth/token']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/telemetry']").exists())
                // El candado de Swagger UI necesita el esquema declarado.
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth").exists());

        mockMvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("la telemetría exige token: 401 con cuerpo ApiError, como dice el contrato")
    void laTelemetriaSigueProtegida() throws Exception {
        mockMvc.perform(get("/api/v1/telemetry"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value("Falta el token JWT o es inválido"))
                .andExpect(jsonPath("$.path").value("/api/v1/telemetry"));
    }

    @Test
    @DisplayName("un token inválido responde 401, no 403 ni 500")
    void unTokenInvalidoResponde401() throws Exception {
        mockMvc.perform(get("/api/v1/telemetry")
                        .header("Authorization", "Bearer esto-no-es-un-jwt"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("ingesta una muestra y la recupera con su alerta ECA-Agua")
    void flujoCompletoDeIngestaYAlerta() throws Exception {
        // 1. El cliente se autentica y obtiene el JWT.
        String tokenBody = mockMvc.perform(post("/api/v1/auth/token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"admin123\"}"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String token = objectMapper.readTree(tokenBody).get("access_token").asText();

        // 2. Ingresa la muestra del nodo: pH 9.5 supera el límite superior de la ECA-Agua.
        mockMvc.perform(post("/api/v1/telemetry")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(PAYLOAD_PH_ALTO))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.node_id").value("N1"))
                .andExpect(jsonPath("$.alert").value(true))
                .andExpect(jsonPath("$.alert_reasons[0].code").value("PH_HIGH"));

        // 3. Y aparece en el histórico filtrado por alertas.
        mockMvc.perform(get("/api/v1/telemetry")
                        .param("alert", "true")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total_elements").value(1))
                .andExpect(jsonPath("$.content[0].node_id").value("N1"))
                .andExpect(jsonPath("$.content[0].alert").value(true));
    }

    @Test
    @DisplayName("una muestra dentro de la ECA-Agua no genera alerta")
    void muestraDentroDeLaEcaNoAlerta() throws Exception {
        String tokenBody = mockMvc.perform(post("/api/v1/auth/token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"admin123\"}"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String token = objectMapper.readTree(tokenBody).get("access_token").asText();

        // El pH 7.2 del payload de referencia está dentro del rango.
        mockMvc.perform(post("/api/v1/telemetry")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"node_id\":\"N1\",\"ph\":7.2,\"turbidity_ntu\":15.4,"
                                + "\"temp_c\":18.5,\"tds_ppm\":320}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.alert").value(false))
                .andExpect(jsonPath("$.alert_reasons").isEmpty());
    }

    @Test
    @DisplayName("las predicciones y el estado de la IA exigen JWT como el resto de la API")
    void prediccionesSinTokenResponden401() throws Exception {
        mockMvc.perform(post("/api/v1/predictions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"horizon_hours\":6}"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/predictions"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/ai/health"))
                .andExpect(status().isUnauthorized());
    }
}
