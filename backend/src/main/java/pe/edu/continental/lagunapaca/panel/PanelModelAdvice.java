package pe.edu.continental.lagunapaca.panel;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * Expone la sesión a todas las vistas del panel bajo el atributo {@code sesion}.
 *
 * <p>Se limita a {@link PanelController} a propósito: si fuera global, cada
 * petición de la API pagaría el costo de construir un objeto que nunca usa, y
 * cualquier error aquí podría romper el contrato JSON.</p>
 */
@ControllerAdvice(assignableTypes = PanelController.class)
public class PanelModelAdvice {

    @ModelAttribute("sesion")
    public PanelSession sesion() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated()) {
            // La página de login se renderiza sin sesión: la barra superior no
            // se muestra, pero la vista no debe caer por un atributo ausente.
            return new PanelSession("Invitado", "Sin sesión");
        }

        return new PanelSession(authentication.getName(), traducirRol(authentication));
    }

    private String traducirRol(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .map(rol -> rol.startsWith("ROLE_") ? rol.substring("ROLE_".length()) : rol)
                .map(PanelModelAdvice::enEspanol)
                .findFirst()
                .orElse("Usuario");
    }

    private static String enEspanol(String rol) {
        return switch (rol) {
            case "ADMIN" -> "Administrador";
            case "USER" -> "Usuario";
            default -> rol;
        };
    }
}
