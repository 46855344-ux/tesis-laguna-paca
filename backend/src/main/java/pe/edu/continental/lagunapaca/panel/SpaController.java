package pe.edu.continental.lagunapaca.panel;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * Entrada de la SPA React.
 *
 * <p>Las rutas del navegador ({@code /login}, {@code /panel}, métricas, historial
 * y alertas) devuelven el {@code index.html} que Vite genera dentro de
 * {@code static/panel}. El recurso viaja en el JAR, así que la demo no necesita
 * un servidor Node en ejecución.</p>
 *
 * <p>Se devuelve el recurso directamente, y no con {@code forward:}, para que
 * las pruebas de integración reciban el HTML y para no depender de una segunda
 * pasada por el servlet de recursos estáticos.</p>
 */
@Controller
public class SpaController {

    private final Resource indice = new ClassPathResource("static/panel/index.html");

    @GetMapping({"/login", "/panel", "/panel/", "/panel/metricas", "/panel/historial", "/panel/alertas"})
    @ResponseBody
    public ResponseEntity<Resource> entrada() {
        if (!indice.exists()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_HTML)
                // El índice apunta a assets con hash: debe revalidarse para no
                // servir una versión vieja después de un despliegue.
                .cacheControl(CacheControl.noCache())
                .body(indice);
    }

    /** La raíz no pertenece a la API: en un navegador se espera el panel. */
    @GetMapping("/")
    public String raiz() {
        return "redirect:/panel";
    }
}
