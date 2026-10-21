package pe.edu.continental.lagunapaca.panel;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

import java.util.List;
import java.util.Locale;

/**
 * Configuración que el frontend React necesita para arrancar: la sesión actual,
 * las estaciones conocidas y la clave pública del mapa opcional.
 *
 * <p>La clave de Google viaja en este endpoint y no en
 * {@code /panel/api/datos}; el tablero se refresca cada 20 segundos y no tiene
 * por qué repetirla en cada respuesta.</p>
 */
public record PanelConfig(
        String usuario,
        String rol,
        String inicial,
        List<String> estaciones,
        String googleMapsApiKey
) {

    public static PanelConfig desde(Authentication authentication,
                                    List<String> estaciones,
                                    String googleMapsApiKey) {
        String usuario = authentication == null || !authentication.isAuthenticated()
                ? "Invitado"
                : authentication.getName();
        String rol = authentication == null || !authentication.isAuthenticated()
                ? "Sin sesión"
                : traducirRol(authentication);
        return new PanelConfig(
                usuario,
                rol,
                inicial(usuario),
                List.copyOf(estaciones),
                googleMapsApiKey == null ? "" : googleMapsApiKey.trim());
    }

    private static String traducirRol(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .map(rol -> rol.startsWith("ROLE_") ? rol.substring("ROLE_".length()) : rol)
                .map(PanelConfig::enEspanol)
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

    private static String inicial(String usuario) {
        if (usuario == null || usuario.isBlank()) {
            return "?";
        }
        return usuario.substring(0, 1).toUpperCase(Locale.ROOT);
    }

    public String getUsuario() {
        return usuario;
    }

    public String getRol() {
        return rol;
    }

    public String getInicial() {
        return inicial;
    }

    public List<String> getEstaciones() {
        return estaciones;
    }

    public String getGoogleMapsApiKey() {
        return googleMapsApiKey;
    }
}
