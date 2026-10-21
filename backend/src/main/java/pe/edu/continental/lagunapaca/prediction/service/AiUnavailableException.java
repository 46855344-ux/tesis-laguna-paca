package pe.edu.continental.lagunapaca.prediction.service;

/** El servicio de IA no responde o no tiene un modelo cargado. */
public class AiUnavailableException extends RuntimeException {

    public AiUnavailableException(String message) {
        super(message);
    }

    public AiUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
