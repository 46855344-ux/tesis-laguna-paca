package pe.edu.continental.lagunapaca.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Reloj de la aplicación.
 *
 * <p>Se expone como bean para que los casos de uso que lo necesitan
 * (evaluación de predicciones, generación de predicciones) se puedan probar con
 * un reloj fijo sin tocar el código de producción.</p>
 */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
