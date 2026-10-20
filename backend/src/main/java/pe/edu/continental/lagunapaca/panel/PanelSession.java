package pe.edu.continental.lagunapaca.panel;

import java.util.Locale;

/**
 * Datos de la persona que está usando el panel, listos para la vista.
 *
 * <p>Es un objeto de presentación, no de dominio: no representa a un usuario del
 * sistema (eso llegará con el registro de usuarios), sino la sesión actual tal
 * como la necesita la barra superior.</p>
 *
 * <p>Los accesores son métodos {@code get...()} explícitos a propósito: Thymeleaf
 * resuelve {@code ${sesion.usuario}} con las propiedades de JavaBean, y así no
 * depende de que el motor sepa leer componentes de un {@code record}.</p>
 */
public record PanelSession(String usuario, String rol) {

    /** Nombre del usuario que se muestra en la barra superior. */
    public String getUsuario() {
        return usuario;
    }

    /** Rol traducido a español ("Administrador"), no el código de Spring Security. */
    public String getRol() {
        return rol;
    }

    /** Inicial que se dibuja en el avatar cuando no hay fotografía. */
    public String getInicial() {
        if (usuario == null || usuario.isBlank()) {
            return "?";
        }
        return usuario.substring(0, 1).toUpperCase(Locale.ROOT);
    }
}
