package pe.edu.continental.lagunapaca.telemetry.mqtt;

import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.mqtt.core.DefaultMqttPahoClientFactory;
import org.springframework.integration.mqtt.core.MqttPahoClientFactory;
import org.springframework.integration.mqtt.inbound.MqttPahoMessageDrivenChannelAdapter;

/**
 * Segunda vía de ingesta: un adaptador MQTT que escucha el tópico configurado y
 * entrega cada mensaje al {@link TelemetryMqttHandler}.
 *
 * <p>Se activa solo con {@code app.mqtt.enabled=true} para que la aplicación
 * pueda arrancar (y testearse) sin un broker disponible.</p>
 */
@Configuration
@EnableConfigurationProperties(MqttProperties.class)
@ConditionalOnProperty(prefix = "app.mqtt", name = "enabled", havingValue = "true")
public class MqttIngestionConfig {

    @Bean
    public IntegrationFlow mqttTelemetryInboundFlow(MqttProperties properties, TelemetryMqttHandler handler) {
        return IntegrationFlow
                .from(mqttInboundAdapter(properties))
                .handle(handler, "handle")
                .get();
    }

    private MqttPahoMessageDrivenChannelAdapter mqttInboundAdapter(MqttProperties properties) {
        MqttPahoMessageDrivenChannelAdapter adapter = new MqttPahoMessageDrivenChannelAdapter(
                properties.brokerUrl(),
                properties.clientId(),
                clientFactory(properties),
                properties.topic());
        adapter.setQos(properties.qos());
        adapter.setCompletionTimeout(properties.completionTimeoutMs());
        return adapter;
    }

    private MqttPahoClientFactory clientFactory(MqttProperties properties) {
        DefaultMqttPahoClientFactory factory = new DefaultMqttPahoClientFactory();
        if (properties.username() != null && !properties.username().isBlank()) {
            MqttConnectOptions options = new MqttConnectOptions();
            options.setUserName(properties.username());
            if (properties.password() != null) {
                options.setPassword(properties.password().toCharArray());
            }
            options.setAutomaticReconnect(true);
            factory.setConnectionOptions(options);
        }
        return factory;
    }
}
