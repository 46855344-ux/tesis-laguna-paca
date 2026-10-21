package pe.edu.continental.lagunapaca.prediction.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import pe.edu.continental.lagunapaca.prediction.api.AiStatusResponse;
import pe.edu.continental.lagunapaca.prediction.service.AiContractException;
import pe.edu.continental.lagunapaca.prediction.service.AiUnavailableException;
import pe.edu.continental.lagunapaca.station.StationCatalog;

/**
 * Cliente HTTP del servicio de inferencia (FastAPI).
 *
 * <p>Se usa {@link java.net.http.HttpClient} del JDK en lugar de un cliente de
 * Spring para que los tiempos de espera sean explícitos y para poder probar el
 * cliente contra un servidor HTTP real del JDK, sin dependencias nuevas.</p>
 *
 * <p>Regla de diseño: la indisponibilidad de la IA <strong>nunca</strong> puede
 * propagarse a la ingesta. Este cliente solo se invoca desde el caso de uso de
 * predicciones; la ingesta no lo conoce.</p>
 */
@Component
public class AiClient {

    private static final Logger log = LoggerFactory.getLogger(AiClient.class);

    private final AiProperties properties;
    private final ObjectMapper mapper;
    private final HttpClient http;

    public AiClient(AiProperties properties, ObjectMapper mapper) {
        this.properties = properties;
        this.mapper = mapper;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(properties.getConnectTimeoutMs()))
                .build();
    }

    /**
     * Consulta el estado del servicio. No lanza: un monitor debe poder informar
     * «no disponible» sin romperse.
     */
    public AiStatusResponse health() {
        Instant ahora = Instant.now();
        try {
            HttpResponse<String> response = send("GET", "/health", null);
            if (response.statusCode() != 200) {
                return AiStatusResponse.unavailable(
                        "El servicio de IA respondió HTTP " + response.statusCode(), ahora);
            }
            AiPredictionResult.HealthPayload body =
                    mapper.readValue(response.body(), AiPredictionResult.HealthPayload.class);
            boolean cargado = Boolean.TRUE.equals(body.modelLoaded());
            return new AiStatusResponse(
                    cargado,
                    body.modelVersion(),
                    body.horizons() == null ? List.of() : body.horizons(),
                    body.inputLen(),
                    body.dataSource(),
                    cargado ? null : "El servicio de IA no tiene un modelo cargado",
                    ahora);
        } catch (IOException | InterruptedException | RuntimeException ex) {
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            log.warn("Servicio de IA no disponible: {}", ex.getClass().getSimpleName());
            return AiStatusResponse.unavailable(
                    "No se pudo contactar al servicio de IA (" + ex.getClass().getSimpleName() + ")", ahora);
        }
    }

    /**
     * Ejecuta una inferencia y valida el contrato antes de devolverlo.
     *
     * @throws AiUnavailableException si no hay contacto, hay timeout o el
     *                                servicio responde 5xx/503
     * @throws AiContractException    si la respuesta no cumple la forma esperada
     */
    public AiPredictionResult predict(int horizonHours, Instant baseTime, double[][][] input) {
        String json;
        try {
            json = mapper.writeValueAsString(
                    new AiPredictionResult.PredictPayload(horizonHours, baseTime, input));
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("No se pudo serializar la ventana de inferencia", ex);
        }

        HttpResponse<String> response;
        try {
            response = send("POST", "/predict", json);
        } catch (IOException ex) {
            throw new AiUnavailableException("No se pudo contactar al servicio de IA", ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new AiUnavailableException("La llamada al servicio de IA fue interrumpida", ex);
        }

        if (response.statusCode() == 503) {
            throw new AiUnavailableException("El servicio de IA no tiene un modelo cargado");
        }
        if (response.statusCode() >= 500) {
            throw new AiUnavailableException(
                    "El servicio de IA respondió HTTP " + response.statusCode());
        }
        if (response.statusCode() != 200) {
            throw new AiContractException(
                    "El servicio de IA rechazó la ventana (HTTP " + response.statusCode() + "): "
                            + detail(response.body()));
        }

        AiPredictionResult.Payload body;
        try {
            body = mapper.readValue(response.body(), AiPredictionResult.Payload.class);
        } catch (JsonProcessingException ex) {
            throw new AiContractException("La respuesta del servicio de IA no es JSON válido", ex);
        }
        return validar(body, horizonHours, baseTime);
    }

    private AiPredictionResult validar(AiPredictionResult.Payload body, int horizonHours, Instant baseTime) {
        if (body == null) {
            throw new AiContractException("El servicio de IA devolvió una respuesta vacía");
        }
        if (body.modelVersion() == null || body.modelVersion().isBlank()) {
            throw new AiContractException("La respuesta no informa la versión del modelo");
        }
        if (body.dataSource() == null
                || !(body.dataSource().equals("synthetic") || body.dataSource().equals("field"))) {
            throw new AiContractException("La respuesta no identifica el origen de los datos del modelo");
        }
        if (body.targetAt() == null || !body.targetAt().isAfter(baseTime)) {
            throw new AiContractException("El instante objetivo debe ser posterior a la última observación");
        }
        if (body.horizonHours() == null || body.horizonHours() != horizonHours) {
            throw new AiContractException("El horizonte devuelto no coincide con el solicitado");
        }
        if (body.predictions() == null || body.predictions().size() != StationCatalog.IDS.size()) {
            throw new AiContractException("La respuesta debe traer una predicción por estación");
        }

        Set<String> estaciones = new HashSet<>();
        for (AiPredictionResult.Station prediction : body.predictions()) {
            if (prediction == null || prediction.station() == null
                    || !StationCatalog.exists(prediction.station())) {
                throw new AiContractException("La respuesta trae una estación desconocida");
            }
            if (prediction.ph() == null || !Double.isFinite(prediction.ph())) {
                throw new AiContractException("La respuesta trae un pH no finito");
            }
            estaciones.add(prediction.station());
        }
        if (!estaciones.containsAll(StationCatalog.IDS)) {
            throw new AiContractException("La respuesta no cubre las cuatro estaciones");
        }

        List<AiPredictionResult.StationPrediction> predicciones = body.predictions().stream()
                .map(p -> new AiPredictionResult.StationPrediction(p.station(), p.ph()))
                .toList();
        return new AiPredictionResult(
                body.modelVersion(),
                body.dataSource(),
                body.horizonHours(),
                body.generatedAt() == null ? Instant.now() : body.generatedAt(),
                body.targetAt(),
                predicciones);
    }

    private HttpResponse<String> send(String method, String path, String body)
            throws IOException, InterruptedException {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(baseUrl() + path))
                .timeout(Duration.ofMillis(properties.getReadTimeoutMs()))
                .header("Accept", "application/json");

        if (properties.getToken() != null && !properties.getToken().isBlank()) {
            builder.header("X-Internal-Token", properties.getToken());
        }
        if (body == null) {
            builder.GET();
        } else {
            builder.header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
        }
        return http.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private String baseUrl() {
        String url = properties.getBaseUrl() == null ? "" : properties.getBaseUrl().trim();
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    private String detail(String body) {
        if (body == null || body.isBlank()) {
            return "sin detalle";
        }
        try {
            String detail = mapper.readTree(body).path("detail").asText(null);
            if (detail != null && !detail.isBlank()) {
                return detail;
            }
        } catch (JsonProcessingException ignored) {
            // El cuerpo no era JSON: se recorta el texto.
        }
        return body.length() > 200 ? body.substring(0, 200) + "…" : body;
    }
}
