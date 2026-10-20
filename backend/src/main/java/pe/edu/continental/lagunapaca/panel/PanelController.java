package pe.edu.continental.lagunapaca.panel;

import java.util.List;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Páginas del panel web.
 *
 * <p>Solo renderiza vistas: los datos de telemetría los seguirá sirviendo la API
 * REST. El panel y la API no comparten controladores para que el contrato de la
 * API ({@code openapi.yaml}) no arrastre responsabilidades de presentación.</p>
 */
@Controller
public class PanelController {

    /**
     * Estaciones definidas para la laguna. Coinciden con el {@code node_id} que
     * envía el ESP32 y con las coordenadas de {@code ia-model/lagunapaca_gnn/stations.py}.
     *
     * <p>Viven aquí como constante porque todavía no existe una entidad
     * {@code Estacion}: cuando exista, esta lista saldrá de la base de datos y
     * el panel no cambiará.</p>
     */
    private static final List<String> ESTACIONES = List.of("N1", "N2", "N3", "N4");

    /**
     * Formulario de acceso. Spring Security lo referencia con
     * {@code formLogin().loginPage("/login")}, pero es este controlador el que
     * decide cómo se ve: así el login comparte el sistema de diseño del panel.
     */
    @GetMapping("/login")
    public String login() {
        return "auth/login";
    }

    /** Pantalla principal del panel. */
    @GetMapping("/panel")
    public String panel(Model model) {
        model.addAttribute("estaciones", ESTACIONES);
        return "panel/panel";
    }
}
