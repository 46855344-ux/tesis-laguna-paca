package pe.edu.continental.lagunapaca;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Punto de entrada del backend del sistema de monitoreo de la Laguna de Paca.
 *
 * <p>Monolito modular: cada dominio (p. ej. {@code telemetry}, {@code security})
 * vive en su propio paquete con capas api/domain/service.</p>
 */
@SpringBootApplication
public class LagunaPacaApplication {

    public static void main(String[] args) {
        SpringApplication.run(LagunaPacaApplication.class, args);
    }
}
