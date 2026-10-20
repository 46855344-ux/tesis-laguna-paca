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
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

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

        Instant desde = FechasPanel.desde(filtros.getDesde(), "desde", avisos);
        Instant hasta = FechasPanel.hasta(filtros.getHasta(), "hasta", avisos);

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
                Paginas.visibles(pagina + 1, resultado.totalPages()),
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

    private static String textoONulo(String texto) {
        return texto == null || texto.isBlank() ? null : texto.trim();
    }
}
