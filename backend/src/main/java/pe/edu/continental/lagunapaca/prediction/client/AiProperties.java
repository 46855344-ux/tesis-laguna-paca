package pe.edu.continental.lagunapaca.prediction.client;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuración del servicio de inferencia (FastAPI).
 *
 * <p>La URL y el token interno se leen del entorno para no versionarlos. En
 * Docker Compose la URL es {@code http://ia:8000}, una dirección de la red
 * interna: el servicio no se publica a internet.</p>
 */
@Component
@ConfigurationProperties(prefix = "app.ai")
public class AiProperties {

    /** URL base del servicio FastAPI. */
    private String baseUrl = "http://localhost:8000";

    /** Token compartido para la llamada interna; vacío = sin autenticación (solo desarrollo). */
    private String token = "";

    /** Tiempo máximo para establecer la conexión. */
    private int connectTimeoutMs = 2000;

    /** Tiempo máximo de espera de una inferencia. */
    private int readTimeoutMs = 15000;

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public int getConnectTimeoutMs() {
        return connectTimeoutMs;
    }

    public void setConnectTimeoutMs(int connectTimeoutMs) {
        this.connectTimeoutMs = connectTimeoutMs;
    }

    public int getReadTimeoutMs() {
        return readTimeoutMs;
    }

    public void setReadTimeoutMs(int readTimeoutMs) {
        this.readTimeoutMs = readTimeoutMs;
    }
}
