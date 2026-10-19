package pe.edu.continental.lagunapaca.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Metadatos de la documentación interactiva (Swagger UI).
 *
 * <p>Además de describir la API, declara el esquema de seguridad que hace que el
 * botón <em>Authorize</em> funcione: el token que emite
 * {@code POST /api/v1/auth/token} se pega ahí una sola vez y Swagger UI lo envía
 * en todas las llamadas.</p>
 */
@Configuration
public class OpenApiConfig {

    private static final String BEARER_SCHEME = "bearerAuth";

    @Bean
    OpenAPI lagunaPacaOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("API de monitoreo de la Laguna de Paca")
                        .version("v1")
                        .description("""
                                Telemetría de los nodos sensores y evaluación de los límites \
                                ECA-Agua.

                                Para probar los endpoints protegidos: autenticarse con el botón \
                                Authorize usando el usuario configurado en APP_USER/APP_PASSWORD \
                                (por defecto admin/admin123)."""))
                .components(new Components().addSecuritySchemes(BEARER_SCHEME,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("JWT emitido por POST /api/v1/auth/token")))
                // El candado aparece en toda la API; /auth/token es la excepción pública.
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME));
    }
}
