package pe.edu.continental.lagunapaca.telemetry.mqtt;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Caracteriza el contrato de {@code app.mqtt.*}: deshabilitado por defecto y
 * con valores por defecto seguros, sobreescribibles desde propiedades.
 */
@DisplayName("Propiedades de la ingesta MQTT")
class MqttPropertiesTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfig.class);

    @Configuration
    @EnableConfigurationProperties(MqttProperties.class)
    static class TestConfig {
    }

    @Test
    @DisplayName("deshabilitada por defecto, con tópico/QoS/timeout por defecto")
    void porDefecto_deshabilitada_conDefaults() {
        runner.run(context -> {
            MqttProperties props = context.getBean(MqttProperties.class);
            assertThat(props.enabled()).isFalse();
            assertThat(props.topic()).isEqualTo("laguna-paca/telemetry");
            assertThat(props.qos()).isEqualTo(1);
            assertThat(props.completionTimeoutMs()).isEqualTo(5000L);
        });
    }

    @Test
    @DisplayName("enlaza broker, credenciales y tópico desde app.mqtt.*")
    void enlazaLaConfiguracion() {
        runner.withPropertyValues(
                        "app.mqtt.enabled=true",
                        "app.mqtt.broker-url=tcp://broker:1883",
                        "app.mqtt.client-id=backend-1",
                        "app.mqtt.username=iot",
                        "app.mqtt.password=secreto",
                        "app.mqtt.topic=laguna/telemetria",
                        "app.mqtt.qos=2",
                        "app.mqtt.completion-timeout-ms=1500")
                .run(context -> {
                    MqttProperties props = context.getBean(MqttProperties.class);
                    assertThat(props.enabled()).isTrue();
                    assertThat(props.brokerUrl()).isEqualTo("tcp://broker:1883");
                    assertThat(props.clientId()).isEqualTo("backend-1");
                    assertThat(props.username()).isEqualTo("iot");
                    assertThat(props.password()).isEqualTo("secreto");
                    assertThat(props.topic()).isEqualTo("laguna/telemetria");
                    assertThat(props.qos()).isEqualTo(2);
                    assertThat(props.completionTimeoutMs()).isEqualTo(1500L);
                });
    }

    @Test
    @DisplayName("corrige valores fuera de rango (QoS y timeout)")
    void corrigeValoresFueraDeRango() {
        runner.withPropertyValues("app.mqtt.qos=9", "app.mqtt.completion-timeout-ms=-1")
                .run(context -> {
                    MqttProperties props = context.getBean(MqttProperties.class);
                    assertThat(props.qos()).isEqualTo(1);
                    assertThat(props.completionTimeoutMs()).isEqualTo(5000L);
                });
    }
}
