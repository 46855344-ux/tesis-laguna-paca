package pe.edu.continental.lagunapaca.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import pe.edu.continental.lagunapaca.shared.api.ApiError;

import java.io.IOException;
import java.time.Instant;
import java.util.List;

/**
 * Responde {@code 401} cuando falta el JWT o es inválido, con el mismo cuerpo
 * {@link ApiError} que el resto de la API.
 *
 * <p>Sin este componente Spring Security usa su valor por defecto
 * ({@code Http403ForbiddenEntryPoint}), porque al no haber una estrategia de
 * autenticación que emita un desafío responde {@code 403}. Eso contradice el
 * contrato de {@code openapi.yaml} y confunde al cliente: {@code 403} significa
 * "autenticado pero sin permiso", no "faltan credenciales".</p>
 *
 * <p>No es un {@code @Component}: se registra explícitamente en
 * {@link SecurityConfig} para que los slices de test web no lo carguen.</p>
 */
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    public RestAuthenticationEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException) throws IOException {

        ApiError body = new ApiError(
                Instant.now(),
                HttpStatus.UNAUTHORIZED.value(),
                HttpStatus.UNAUTHORIZED.getReasonPhrase(),
                "Falta el token JWT o es inválido",
                request.getRequestURI(),
                List.of());

        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        // RFC 6750: el desafío indica el esquema esperado.
        response.setHeader("WWW-Authenticate", "Bearer realm=\"laguna-paca\"");

        objectMapper.writeValue(response.getOutputStream(), body);
    }
}
