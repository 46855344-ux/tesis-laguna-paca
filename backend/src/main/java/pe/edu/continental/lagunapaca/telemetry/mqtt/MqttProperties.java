package pe.edu.continental.lagunapaca.telemetry.mqtt;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Configuración de la ingesta MQTT ({@code app.mqtt.*}).
 *
 * <p>Los valores por defecto se declaran con {@link DefaultValue} para que
 * apliquen también cuando el prefijo {@code app.mqtt} no está presente. La
 * normalización en el constructor compacto cubre valores fuera de rango.</p>
 */
@ConfigurationProperties(prefix = "app.mqtt")
public record MqttProperties(
        boolean enabled,
        @DefaultValue("tcp://localhost:1883") String brokerUrl,
        @DefaultValue("laguna-paca-backend") String clientId,
        String username,
        String password,
        @DefaultValue("laguna-paca/telemetry") String topic,
        @DefaultValue("1") int qos,
        @DefaultValue("5000") long completionTimeoutMs
) {

    public MqttProperties {
        if (topic == null || topic.isBlank()) {
            topic = "laguna-paca/telemetry";
        }
        if (qos < 0 || qos > 2) {
            qos = 1;
        }
        if (completionTimeoutMs <= 0) {
            completionTimeoutMs = 5000L;
        }
    }
}
