package pe.edu.continental.lagunapaca.telemetry.mqtt;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pe.edu.continental.lagunapaca.telemetry.api.TelemetryRequest;
import pe.edu.continental.lagunapaca.telemetry.api.TelemetryResponse;
import pe.edu.continental.lagunapaca.telemetry.service.TelemetryService;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Comportamiento del consumidor MQTT. No requiere un broker: se prueba el
 * handler de forma aislada.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Handler MQTT de telemetría")
class TelemetryMqttHandlerTest {

    private static final String VALID_PAYLOAD = """
            {"node_id":"N1","ph":7.2,"turbidity_ntu":15.4,"temp_c":18.5,"tds_ppm":320}
            """;

    @Mock
    private TelemetryService telemetryService;

    private TelemetryMqttHandler handler;

    @BeforeEach
    void setUp() {
        Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
        handler = new TelemetryMqttHandler(telemetryService, new ObjectMapper(), validator);
    }

    @Test
    @DisplayName("payload válido se delega al servicio de ingesta")
    void payloadValido_seIngesta() {
        when(telemetryService.ingest(any())).thenReturn(new TelemetryResponse(
                1L, "N1", 7.2, 15.4, 18.5, 320.0, false, List.of(),
                Instant.parse("2026-10-18T10:00:00Z")));

        handler.handle(VALID_PAYLOAD);

        ArgumentCaptor<TelemetryRequest> captor = ArgumentCaptor.forClass(TelemetryRequest.class);
        verify(telemetryService).ingest(captor.capture());
        assertThat(captor.getValue().nodeId()).isEqualTo("N1");
        assertThat(captor.getValue().ph()).isEqualTo(7.2);
        assertThat(captor.getValue().turbidityNtu()).isEqualTo(15.4);
        assertThat(captor.getValue().tempC()).isEqualTo(18.5);
        assertThat(captor.getValue().tdsPpm()).isEqualTo(320.0);
    }

    @Test
    @DisplayName("payload con pH fuera de rango se descarta sin llamar al servicio")
    void payloadInvalido_seDescarta() {
        String payload = """
                {"node_id":"N1","ph":15.0,"turbidity_ntu":15.4,"temp_c":18.5,"tds_ppm":320}
                """;

        handler.handle(payload);

        verifyNoInteractions(telemetryService);
    }

    @Test
    @DisplayName("JSON malformado se descarta sin propagar excepción")
    void jsonMalformado_seDescarta() {
        handler.handle("{esto no es json");

        verifyNoInteractions(telemetryService);
    }

    @Test
    @DisplayName("JSON nulo ('null') se descarta sin propagar excepción")
    void jsonNulo_seDescarta() {
        handler.handle("null");

        verifyNoInteractions(telemetryService);
    }
}
