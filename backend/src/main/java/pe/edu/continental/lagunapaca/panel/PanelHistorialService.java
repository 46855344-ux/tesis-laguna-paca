package pe.edu.continental.lagunapaca.panel;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import pe.edu.continental.lagunapaca.telemetry.api.AlertReasonDto;
import pe.edu.continental.lagunapaca.telemetry.api.PageResponse;
import pe.edu.continental.lagunapaca.telemetry.api.TelemetryResponse;
import pe.edu.continental.lagunapaca.telemetry.service.TelemetryQuery;
import pe.edu.continental.lagunapaca.telemetry.service.TelemetryService;

import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

/**
 * Arma el historial paginado del panel.
 *
 * <p>No reimplementa la consulta: delega en {@link TelemetryService#search},
 * que ya aplica las especificaciones y está probado. Aquí solo se traducen los
 * filtros de la URL (fechas de calendario, checkbox) al lenguaje del dominio
 * (instantes) y el resultado a texto listo para la tabla.</p>
 */
@Service
public class PanelHistorialService {

    /** Filas por página: suficientes para comparar, pocas para no scrollear. */
    static final int TAMANO_PAGINA = 20;

    private static final DateTimeFormatter FECHA =
            DateTimeFormatter.ofPattern("dd/MM/yyyy").withZone(PanelDashboardService.ZONA);
    private static final DateTimeFormatter HORA =
            DateTimeFormatter.ofPattern("HH:mm").withZone(PanelDashboardService.ZONA);

    private final TelemetryService telemetryService;

    public PanelHistorialService(TelemetryService telemetryService) {
        this.telemetryService = telemetryService;
    }

    public HistorialVista construir(Filtros filtros) {
        List<String> avisos = new ArrayList<>(1);

        Instant desde = limiteInferior(filtros.getDesde(), avisos);
        Instant hasta = limiteSuperior(filtros.getHasta(), avisos);

        if (desde != null && hasta != null && desde.isAfter(hasta)) {
            avisos.add("El rango de fechas no se aplicó: «desde» es posterior a «hasta».");
            desde = null;
            hasta = null;
        }

        TelemetryQuery consulta = new TelemetryQuery(
                textoONulo(filtros.getEstacion()),
                desde,
                hasta,
                filtros.isSoloAlertas() ? Boolean.TRUE : null,
                textoONulo(filtros.getMotivo()),
                null);

        int pagina = Math.max(0, filtros.getPagina());
        PageResponse<TelemetryResponse> resultado = buscar(consulta, pagina);

        // Al cambiar de filtro es fácil quedar en una página que ya no existe.
        // Mostrar la última es mejor que una tabla vacía que parece "sin datos".
        if (resultado.content().isEmpty() && pagina > 0 && resultado.totalPages() > 0) {
            pagina = resultado.totalPages() - 1;
            resultado = buscar(consulta, pagina);
        }

        List<HistorialVista.Fila> filas = resultado.content().stream()
                .map(PanelHistorialService::fila)
                .toList();

        int desdeFila = filas.isEmpty() ? 0 : pagina * TAMANO_PAGINA + 1;
        int hastaFila = filas.isEmpty() ? 0 : desdeFila + filas.size() - 1;

        return new HistorialVista(
                filas,
                filtros,
                pagina + 1,
                resultado.totalPages(),
                resultado.totalElements(),
                desdeFila,
                hastaFila,
                paginasVisibles(pagina + 1, resultado.totalPages()),
                avisos.isEmpty() ? null : String.join(" ", avisos));
    }

    private PageResponse<TelemetryResponse> buscar(TelemetryQuery consulta, int pagina) {
        return telemetryService.search(consulta,
                PageRequest.of(pagina, TAMANO_PAGINA,
                        Sort.by(Sort.Direction.DESC, "recordedAt")));
    }

    private static HistorialVista.Fila fila(TelemetryResponse muestra) {
        return new HistorialVista.Fila(
                muestra.id(),
                FECHA.format(muestra.recordedAt()),
                HORA.format(muestra.recordedAt()),
                muestra.nodeId(),
                FormatosPanel.formatear(muestra.ph(), 2),
                FormatosPanel.formatear(muestra.turbidityNtu(), 1),
                FormatosPanel.formatear(muestra.tempC(), 1),
                FormatosPanel.formatear(muestra.tdsPpm(), 0),
                muestra.alert() ? EstadoMuestra.ALERTA : EstadoMuestra.DENTRO,
                muestra.alertReasons().stream().map(AlertReasonDto::message).toList());
    }

    /**
     * La fecha que escribe la persona es un día del calendario de la laguna, no
     * un instante UTC. Interpretarla en UTC desplazaría cinco horas el corte y
     * una muestra de las 23:30 aparecería en el día siguiente.
     */
    private static Instant limiteInferior(String texto, List<String> avisos) {
        LocalDate fecha = fecha(texto, "desde", avisos);
        return fecha == null ? null : fecha.atStartOfDay(PanelDashboardService.ZONA).toInstant();
    }

    private static Instant limiteSuperior(String texto, List<String> avisos) {
        LocalDate fecha = fecha(texto, "hasta", avisos);
        return fecha == null
                ? null
                : fecha.plusDays(1).atStartOfDay(PanelDashboardService.ZONA).toInstant().minusNanos(1);
    }

    private static LocalDate fecha(String texto, String campo, List<String> avisos) {
        if (texto == null || texto.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(texto.trim());
        } catch (DateTimeParseException excepcion) {
            // Se avisa y se ignora ese límite: un error de tecleo no debe
            // convertir el historial en una pantalla de error.
            avisos.add("La fecha «" + campo + "» no es válida; usa el formato AAAA-MM-DD.");
            return null;
        }
    }

    private static String textoONulo(String texto) {
        return texto == null || texto.isBlank() ? null : texto.trim();
    }

    /**
     * Números de página a dibujar: siempre la primera, la última y las vecinas
     * de la actual. La plantilla convierte los saltos en "…".
     */
    static List<Integer> paginasVisibles(int pagina, int totalPaginas) {
        if (totalPaginas <= 1) {
            return List.of();
        }
        TreeSet<Integer> paginas = new TreeSet<>();
        paginas.add(1);
        paginas.add(totalPaginas);
        for (int candidata = pagina - 1; candidata <= pagina + 1; candidata++) {
            if (candidata >= 1 && candidata <= totalPaginas) {
                paginas.add(candidata);
            }
        }
        return List.copyOf(paginas);
    }
}
