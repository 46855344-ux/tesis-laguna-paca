package pe.edu.continental.lagunapaca.panel;

import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;

/**
 * Traduce las fechas de calendario que escribe una persona a los instantes que
 * guarda la base de datos.
 *
 * <p>La fecha de la interfaz es un día <em>de la laguna</em>, no un instante
 * UTC. Interpretarla en UTC desplazaría cinco horas el corte y una muestra de
 * las 23:30 aparecería en el día siguiente. Vive aquí, y no en cada servicio,
 * porque el historial y las alertas tienen que cortar el día igual.</p>
 *
 * <p>Un texto inválido no lanza: se anota el aviso y se devuelve {@code null}
 * (sin filtro), para que un error de tecleo no convierta la pantalla en un 400.</p>
 */
final class FechasPanel {

    private FechasPanel() {
    }

    /** Inicio del día indicado (00:00 en Lima), o {@code null} si no hay texto. */
    static Instant desde(String texto, String campo, List<String> avisos) {
        LocalDate fecha = leer(texto, campo, avisos);
        return fecha == null ? null : fecha.atStartOfDay(PanelDashboardService.ZONA).toInstant();
    }

    /** Final del día indicado (23:59:59.999 en Lima), o {@code null} si no hay texto. */
    static Instant hasta(String texto, String campo, List<String> avisos) {
        LocalDate fecha = leer(texto, campo, avisos);
        return fecha == null
                ? null
                : fecha.plusDays(1).atStartOfDay(PanelDashboardService.ZONA).toInstant().minusNanos(1);
    }

    private static LocalDate leer(String texto, String campo, List<String> avisos) {
        if (texto == null || texto.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(texto.trim());
        } catch (DateTimeParseException excepcion) {
            avisos.add("La fecha «" + campo + "» no es válida; usa el formato AAAA-MM-DD.");
            return null;
        }
    }
}
