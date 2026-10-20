package pe.edu.continental.lagunapaca.panel;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;

/**
 * Formatos de presentación compartidos por las pantallas del panel.
 *
 * <p>Existen para que el tablero y el historial no tengan dos reglas de redondeo
 * distintas: si el tablero dice {@code 7.20} y la tabla {@code 7.2}, la persona
 * que compara ambas cifras duda de los datos.</p>
 */
final class FormatosPanel {

    private FormatosPanel() {
    }

    /** Formato fijo con punto decimal: no depende del locale de la máquina. */
    static String formatear(double valor, int decimales) {
        return String.format(Locale.ROOT, "%." + decimales + "f", valor);
    }

    /** Antigüedad en palabras. Evita que una tarjeta vieja parezca actual. */
    static String hace(Instant cuando, Instant ahora) {
        long segundos = Math.max(0, Duration.between(cuando, ahora).toSeconds());
        if (segundos < 60) {
            return "hace instantes";
        }
        long minutos = segundos / 60;
        if (minutos < 60) {
            return "hace " + minutos + (minutos == 1 ? " minuto" : " minutos");
        }
        long horas = minutos / 60;
        if (horas < 24) {
            return "hace " + horas + (horas == 1 ? " hora" : " horas");
        }
        long dias = horas / 24;
        return "hace " + dias + (dias == 1 ? " día" : " días");
    }
}
