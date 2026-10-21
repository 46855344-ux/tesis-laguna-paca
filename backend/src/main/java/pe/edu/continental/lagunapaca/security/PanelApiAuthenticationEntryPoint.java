package pe.edu.continental.lagunapaca.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

import java.io.IOException;
import java.util.Map;

/**
 * Responde {@code 401} en JSON cuando la sesión del panel caducó.
 *
 * <p>La SPA no puede distinguir bien un 302 al formulario de acceso de una
 * respuesta válida, porque {@code fetch} sigue la redirección y recibe el HTML
 * del login. Un 401 con cuerpo le permite reaccionar y volver al acceso sin
 * interpretar HTML. Las rutas de página conservan la redirección normal.</p>
 *
 * <p>No es un {@code @Component}: se registra en {@link SecurityConfig} para
 * que las pruebas por capa no carguen componentes de seguridad.</p>
 */
public class PanelApiAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private static final String MENSAJE = "La sesión caducó. Inicia sesión de nuevo.";

    private final ObjectMapper objectMapper;

    public PanelApiAuthenticationEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException) throws IOException {

        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getOutputStream(), Map.of("error", MENSAJE));
    }
}
