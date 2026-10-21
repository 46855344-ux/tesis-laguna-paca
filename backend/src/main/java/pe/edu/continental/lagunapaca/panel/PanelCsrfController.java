package pe.edu.continental.lagunapaca.panel;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Entrega el token CSRF a la SPA.
 *
 * <p>El formulario de login de React se envía con {@code fetch} y no puede
 * leer un token de un campo oculto del HTML. Este endpoint es
 * público a propósito: el token está ligado a la sesión y sin él no se podría
 * ni iniciar sesión. Spring Security sigue rechazando cualquier POST que no lo
 * incluya.</p>
 */
@RestController
public class PanelCsrfController {

    @GetMapping("/panel/csrf")
    public CsrfResponse token(HttpServletRequest request) {
        CsrfToken token = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
        if (token == null) {
            token = (CsrfToken) request.getAttribute("_csrf");
        }
        if (token == null) {
            throw new IllegalStateException("Spring Security no publicó el token CSRF.");
        }
        return new CsrfResponse(token.getToken(), token.getHeaderName(), token.getParameterName());
    }

    public record CsrfResponse(String token, String headerName, String parameterName) {
    }
}
