package pe.edu.continental.lagunapaca.panel;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.security.Principal;

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
    private final PanelAlertasService alertasService;

    public PanelController(PanelDashboardService dashboardService,
                           PanelHistorialService historialService,
                           PanelAlertasService alertasService) {
        this.dashboardService = dashboardService;
        this.historialService = historialService;
        this.alertasService = alertasService;
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

    /** Gestión de alertas: pendientes y atendidas, con sus contadores. */
    @GetMapping("/panel/alertas")
    public String alertas(
            @RequestParam(name = "estacion", defaultValue = "") String estacion,
            @RequestParam(name = "estado", defaultValue = FiltrosAlertas.PENDIENTES) String estado,
            @RequestParam(name = "desde", defaultValue = "") String desde,
            @RequestParam(name = "hasta", defaultValue = "") String hasta,
            @RequestParam(name = "page", defaultValue = "0") int pagina,
            Model model) {

        model.addAttribute("alertas", alertasService.construir(
                new FiltrosAlertas(estacion, FiltrosAlertas.estadoValido(estado), desde, hasta, pagina)));
        model.addAttribute("estaciones", PanelDashboardService.ESTACIONES);
        return "panel/alertas";
    }

    /**
     * Marca una alerta como atendida.
     *
     * <p>Es un POST con CSRF: cambia estado persistido y el usuario queda
     * registrado, así que no puede ser un enlace GET que cualquiera dispare.</p>
     */
    @PostMapping("/panel/alertas/{id}/atender")
    public String atender(@PathVariable("id") long id,
                          @RequestParam(name = "nota", defaultValue = "") String nota,
                          @RequestParam(name = "estacion", defaultValue = "") String estacion,
                          @RequestParam(name = "estado", defaultValue = FiltrosAlertas.PENDIENTES) String estado,
                          @RequestParam(name = "desde", defaultValue = "") String desde,
                          @RequestParam(name = "hasta", defaultValue = "") String hasta,
                          @RequestParam(name = "page", defaultValue = "0") int pagina,
                          Principal principal,
                          RedirectAttributes redirect) {

        alertasService.atender(id, nota, principal.getName());
        redirect.addFlashAttribute("mensaje",
                "Alerta atendida. Queda registrada con tu usuario y la hora.");
        return volverAFiltros(estacion, estado, desde, hasta, pagina, redirect);
    }

    /** Devuelve una alerta atendida a la lista de pendientes. */
    @PostMapping("/panel/alertas/{id}/reabrir")
    public String reabrir(@PathVariable("id") long id,
                          @RequestParam(name = "estacion", defaultValue = "") String estacion,
                          @RequestParam(name = "estado", defaultValue = FiltrosAlertas.PENDIENTES) String estado,
                          @RequestParam(name = "desde", defaultValue = "") String desde,
                          @RequestParam(name = "hasta", defaultValue = "") String hasta,
                          @RequestParam(name = "page", defaultValue = "0") int pagina,
                          RedirectAttributes redirect) {

        alertasService.reabrir(id);
        redirect.addFlashAttribute("mensaje", "La alerta vuelve a la lista de pendientes.");
        return volverAFiltros(estacion, estado, desde, hasta, pagina, redirect);
    }

    /**
     * Vuelve a la lista conservando los filtros con los que se estaba trabajando.
     * Se reenvían como atributos de redirección (Spring los codifica) en vez de
     * recibir una URL de retorno: una URL arbitraria del cliente sería un
     * redirect abierto.
     */
    private static String volverAFiltros(String estacion, String estado, String desde,
                                         String hasta, int pagina, RedirectAttributes redirect) {
        redirect.addAttribute("estacion", estacion);
        redirect.addAttribute("estado", estado);
        redirect.addAttribute("desde", desde);
        redirect.addAttribute("hasta", hasta);
        redirect.addAttribute("page", pagina);
        return "redirect:/panel/alertas";
    }
}
