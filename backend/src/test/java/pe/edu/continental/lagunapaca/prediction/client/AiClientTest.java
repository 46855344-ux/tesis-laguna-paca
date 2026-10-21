package pe.edu.continental.lagunapaca.prediction.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pe.edu.continental.lagunapaca.prediction.api.AiStatusResponse;
import pe.edu.continental.lagunapaca.prediction.service.AiContractException;
import pe.edu.continental.lagunapaca.prediction.service.AiUnavailableException;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Prueba el cliente contra un servidor HTTP real del JDK. No usa mocks de red:
 * así se ejercitan de verdad los tiempos de espera, los códigos y el parseo.
 */
@DisplayName("Cliente del servicio de IA")
class AiClientTest {

    private static final Instant BASE = Instant.parse("2026-10-20T10:00:00Z");

    private HttpServer server;
    private AiClient client;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.start();

        AiProperties properties = new AiProperties();
        properties.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
        properties.setConnectTimeoutMs(500);
        properties.setReadTimeoutMs(800);
        client = new AiClient(properties, new ObjectMapper().findAndRegisterModules());
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    private void responder(String path, int status, String body) {
        server.createContext(path, exchange -> {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
    }

    private static String prediccionValida() {
        return """
                {
                  "model_version": "stgnn-test",
                  "data_source": "synthetic",
                  "horizon_hours": 6,
                  "generated_at": "2026-10-20T10:00:00Z",
                  "target_at": "2026-10-20T16:00:00Z",
                  "predictions": [
                    {"station":"N1","ph":7.10},
                    {"station":"N2","ph":7.20},
                    {"station":"N3","ph":7.30},
                    {"station":"N4","ph":7.40}
                  ]
                }
                """;
    }

    @Test
    @DisplayName("health: modelo cargado se informa como disponible")
    void health_conModelo_estaDisponible() {
        responder("/health", 200, """
                {"status":"ok","model_loaded":true,"model_version":"stgnn-test",
                 "horizons":[1,3,6],"input_len":24,"data_source":"synthetic"}
                """);

        AiStatusResponse estado = client.health();

        assertThat(estado.available()).isTrue();
        assertThat(estado.modelVersion()).isEqualTo("stgnn-test");
        assertThat(estado.inputLen()).isEqualTo(24);
        assertThat(estado.horizons()).containsExactly(1, 3, 6);
    }

    @Test
    @DisplayName("health: sin modelo se informa como no disponible, sin lanzar")
    void health_sinModelo_noLanza() {
        responder("/health", 200, """
                {"status":"degraded","model_loaded":false,"detail":"sin artefacto"}
                """);

        AiStatusResponse estado = client.health();

        assertThat(estado.available()).isFalse();
        assertThat(estado.detail()).contains("modelo");
    }

    @Test
    @DisplayName("health: servicio caído devuelve available=false con detalle")
    void health_caido_noLanza() {
        server.stop(0);

        AiStatusResponse estado = client.health();

        assertThat(estado.available()).isFalse();
        assertThat(estado.detail()).contains("No se pudo contactar");
    }

    @Test
    @DisplayName("predict: respuesta válida se convierte en cuatro estaciones")
    void predict_valida_devuelveLasCuatroEstaciones() {
        responder("/predict", 200, prediccionValida());

        AiPredictionResult resultado = client.predict(6, BASE, new double[4][24][4]);

        assertThat(resultado.modelVersion()).isEqualTo("stgnn-test");
        assertThat(resultado.dataSource()).isEqualTo("synthetic");
        assertThat(resultado.targetAt()).isEqualTo(Instant.parse("2026-10-20T16:00:00Z"));
        assertThat(resultado.predictions()).extracting(AiPredictionResult.StationPrediction::station)
                .containsExactly("N1", "N2", "N3", "N4");
    }

    @Test
    @DisplayName("predict: 503 se traduce a servicio no disponible")
    void predict_503_esNoDisponible() {
        responder("/predict", 503, "{\"detail\":\"modelo no cargado\"}");

        assertThatThrownBy(() -> client.predict(6, BASE, new double[4][24][4]))
                .isInstanceOf(AiUnavailableException.class)
                .hasMessageContaining("modelo");
    }

    @Test
    @DisplayName("predict: 422 se traduce a contrato inválido, no a caída")
    void predict_422_esContratoInvalido() {
        responder("/predict", 422, "{\"detail\":\"la ventana debe ser [4,24,4]\"}");

        assertThatThrownBy(() -> client.predict(6, BASE, new double[4][24][4]))
                .isInstanceOf(AiContractException.class)
                .hasMessageContaining("[4,24,4]");
    }

    @Test
    @DisplayName("predict: faltar una estación rompe el contrato")
    void predict_sinUnaEstacion_esContratoInvalido() {
        responder("/predict", 200, """
                {"model_version":"stgnn-test","data_source":"synthetic","horizon_hours":6,
                 "generated_at":"2026-10-20T10:00:00Z","target_at":"2026-10-20T16:00:00Z",
                 "predictions":[{"station":"N1","ph":7.1},{"station":"N2","ph":7.2},
                                {"station":"N3","ph":7.3}]}
                """);

        assertThatThrownBy(() -> client.predict(6, BASE, new double[4][24][4]))
                .isInstanceOf(AiContractException.class)
                .hasMessageContaining("una predicción por estación");
    }

    @Test
    @DisplayName("predict: un pH no finito rompe el contrato")
    void predict_conPhNoFinito_esContratoInvalido() {
        responder("/predict", 200, """
                {"model_version":"stgnn-test","data_source":"synthetic","horizon_hours":6,
                 "generated_at":"2026-10-20T10:00:00Z","target_at":"2026-10-20T16:00:00Z",
                 "predictions":[{"station":"N1","ph":null},{"station":"N2","ph":7.2},
                                {"station":"N3","ph":7.3},{"station":"N4","ph":7.4}]}
                """);

        assertThatThrownBy(() -> client.predict(6, BASE, new double[4][24][4]))
                .isInstanceOf(AiContractException.class);
    }

    @Test
    @DisplayName("predict: objetivo anterior a la base rompe el contrato")
    void predict_objetivoEnElPasado_esContratoInvalido() {
        responder("/predict", 200, """
                {"model_version":"stgnn-test","data_source":"synthetic","horizon_hours":6,
                 "generated_at":"2026-10-20T10:00:00Z","target_at":"2026-10-20T09:00:00Z",
                 "predictions":[{"station":"N1","ph":7.1},{"station":"N2","ph":7.2},
                                {"station":"N3","ph":7.3},{"station":"N4","ph":7.4}]}
                """);

        assertThatThrownBy(() -> client.predict(6, BASE, new double[4][24][4]))
                .isInstanceOf(AiContractException.class)
                .hasMessageContaining("posterior");
    }

    @Test
    @DisplayName("predict: una respuesta más lenta que el timeout no bloquea al backend")
    void predict_lento_esNoDisponible() {
        server.createContext("/predict", exchange -> {
            try {
                Thread.sleep(1500);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });

        assertThatThrownBy(() -> client.predict(6, BASE, new double[4][24][4]))
                .isInstanceOf(AiUnavailableException.class);
    }
}
