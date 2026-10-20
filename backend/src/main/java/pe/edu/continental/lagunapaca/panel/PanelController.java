package pe.edu.continental.lagunapaca.panel;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * Páginas del panel web.
 *
 * <p>Solo renderiza vistas. El panel lee la telemetría por los servicios de
 * dominio, no por los controladores de la API REST: así el contrato de
 * {@code openapi.yaml} no arrastra responsabilidades de presentación y el
 * navegador no necesita un JWT (usa la sesión del formulario).</p>
 */
@Controller
public class PanelController {

    private final PanelDashboardService dashboardService;
    private final PanelHistorialService historialService;

    public PanelController(PanelDashboardService dashboardService,
                           PanelHistorialService historialService) {
        this.dashboardService = dashboardService;
        this.historialService = historialService;
    }

    /**
     * La raíz no pertenece a la API: quien escribe la dirección del servidor en
     * un navegador espera ver el panel, no un 401 en JSON. Si no hay sesión,
     * {@code /panel} se encarga de llevar al formulario de acceso.
     */
    @GetMapping("/")
    public String raiz() {
        return "redirect:/panel";
    }

    /**
     * Formulario de acceso. Spring Security lo referencia con
     * {@code formLogin().loginPage("/login")}, pero es este controlador el que
     * decide cómo se ve: así el login comparte el sistema de diseño del panel.
     */
    @GetMapping("/login")
    public String login() {
        return "auth/login";
    }

    /** Pantalla principal del panel, con el tablero ya renderizado en el servidor. */
    @GetMapping("/panel")
    public String panel(Model model) {
        model.addAttribute("tablero", dashboardService.construir());
        return "panel/panel";
    }

    /**
     * Los mismos datos en JSON, para el refresco en vivo y el gráfico.
     *
     * <p>Vive bajo {@code /panel/**}, así que lo protege la cadena de sesión con
     * CSRF: no es un endpoint público ni acepta JWT.</p>
     */
    @GetMapping("/panel/datos")
    @ResponseBody
    public PanelDashboard datos() {
        return dashboardService.construir();
    }

    /**
     * Historial paginado. Los filtros llegan como texto y no como tipos ya
     * convertidos: así una fecha mal escrita se puede volver a mostrar en el
     * formulario junto a un aviso, en lugar de responder un 400.
     */
    @GetMapping("/panel/historial")
    public String historial(
            @RequestParam(name = "estacion", defaultValue = "") String estacion,
            @RequestParam(name = "desde", defaultValue = "") String desde,
            @RequestParam(name = "hasta", defaultValue = "") String hasta,
            @RequestParam(name = "soloAlertas", defaultValue = "false") boolean soloAlertas,
            @RequestParam(name = "motivo", defaultValue = "") String motivo,
            @RequestParam(name = "page", defaultValue = "0") int pagina,
            Model model) {

        model.addAttribute("historial",
                historialService.construir(new Filtros(estacion, desde, hasta, soloAlertas, motivo, pagina)));
        model.addAttribute("estaciones", PanelDashboardService.ESTACIONES);
        return "panel/historial";
    }
}
