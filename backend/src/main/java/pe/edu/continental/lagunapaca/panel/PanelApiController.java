package pe.edu.continental.lagunapaca.panel;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pe.edu.continental.lagunapaca.prediction.service.AiContractException;
import pe.edu.continental.lagunapaca.prediction.service.AiUnavailableException;
import pe.edu.continental.lagunapaca.prediction.service.IncompleteWindowException;
import pe.edu.continental.lagunapaca.prediction.service.PredictionService;

import java.security.Principal;

/**
 * API JSON del panel web.
 *
 * <p>El frontend es una SPA React y ya no se renderiza en el servidor. Estos
 * endpoints viven bajo {@code /panel/api/**}, así que los protege la cadena de
 * sesión con formulario y CSRF, no la cadena JWT de la API pública. Un JWT
 * válido no abre el panel.</p>
 *
 * <p>Los datos se siguen leyendo de los servicios de dominio, no de los
 * controladores de {@code /api/v1}: el contrato de {@code openapi.yaml} no
 * arrastra responsabilidades de presentación.</p>
 */
@RestController
public class PanelApiController {

    private final PanelDashboardService dashboardService;
    private final PanelHistorialService historialService;
    private final PanelAlertasService alertasService;
    private final PredictionService predictionService;
    private final MapaLagunaService mapaService;

    public PanelApiController(PanelDashboardService dashboardService,
                              PanelHistorialService historialService,
                              PanelAlertasService alertasService,
                              PredictionService predictionService,
                              MapaLagunaService mapaService) {
        this.dashboardService = dashboardService;
        this.historialService = historialService;
        this.alertasService = alertasService;
        this.predictionService = predictionService;
        this.mapaService = mapaService;
    }

    /** Configuración de la sesión, estaciones y mapa opcional. */
    @GetMapping("/panel/api/sesion")
    public PanelConfig sesion(Authentication authentication) {
        return PanelConfig.desde(authentication,
                PanelDashboardService.ESTACIONES,
                mapaService.getGoogleKey());
    }

    /**
     * Tablero completo. Se conserva también {@code /panel/datos} porque era el
     * contrato del panel anterior y no cuesta mantener el alias.
     */
    @GetMapping({"/panel/api/datos", "/panel/datos"})
    public PanelDashboard datos() {
        return dashboardService.construir();
    }

    @GetMapping("/panel/api/historial")
    public HistorialVista historial(
            @RequestParam(name = "estacion", defaultValue = "") String estacion,
            @RequestParam(name = "desde", defaultValue = "") String desde,
            @RequestParam(name = "hasta", defaultValue = "") String hasta,
            @RequestParam(name = "soloAlertas", defaultValue = "false") boolean soloAlertas,
            @RequestParam(name = "motivo", defaultValue = "") String motivo,
            @RequestParam(name = "page", defaultValue = "0") int pagina) {

        return historialService.construir(
                new Filtros(estacion, desde, hasta, soloAlertas, motivo, pagina));
    }

    @GetMapping("/panel/api/alertas")
    public AlertasVista alertas(
            @RequestParam(name = "estacion", defaultValue = "") String estacion,
            @RequestParam(name = "estado", defaultValue = FiltrosAlertas.PENDIENTES) String estado,
            @RequestParam(name = "desde", defaultValue = "") String desde,
            @RequestParam(name = "hasta", defaultValue = "") String hasta,
            @RequestParam(name = "page", defaultValue = "0") int pagina) {

        return alertasService.construir(new FiltrosAlertas(
                estacion, FiltrosAlertas.estadoValido(estado), desde, hasta, pagina));
    }

    /** Marca una alerta como atendida. Es POST con CSRF, igual que antes. */
    @PostMapping("/panel/api/alertas/{id}/atender")
    public Mensaje atender(@PathVariable("id") long id,
                           @RequestBody(required = false) NotaRequest cuerpo,
                           Principal principal) {
        alertasService.atender(id, cuerpo == null ? null : cuerpo.nota(), principal.getName());
        return new Mensaje("Alerta atendida. Queda registrada con tu usuario y la hora.");
    }

    @PostMapping("/panel/api/alertas/{id}/reabrir")
    public Mensaje reabrir(@PathVariable("id") long id) {
        alertasService.reabrir(id);
        return new Mensaje("La alerta vuelve a la lista de pendientes.");
    }

    /**
     * Genera una predicción. La IA caída no se disfraza de éxito: se traduce a
     * 503/502/409/400 y el frontend muestra el motivo sin inventar valores.
     */
    @PostMapping("/panel/api/predicciones")
    public ResponseEntity<?> predecir(@RequestBody(required = false) HorizonteRequest cuerpo) {
        int horas = cuerpo == null || cuerpo.horizonte() == null ? -1 : cuerpo.horizonte();
        try {
            predictionService.generar(horas);
            return ResponseEntity.ok(new Mensaje(
                    "Predicción generada para las cuatro estaciones a " + horas + " h."));
        } catch (IncompleteWindowException ex) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(new ErrorResponse(ex.getMessage()));
        } catch (AiUnavailableException ex) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(new ErrorResponse(ex.getMessage()));
        } catch (AiContractException ex) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(new ErrorResponse(ex.getMessage()));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(new ErrorResponse(ex.getMessage()));
        }
    }

    public record NotaRequest(String nota) {
    }

    public record HorizonteRequest(Integer horizonte) {
    }

    public record Mensaje(String mensaje) {
    }

    public record ErrorResponse(String error) {
    }
}
