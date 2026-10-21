package pe.edu.continental.lagunapaca.prediction.service;

/** El servicio de IA respondió, pero con un contrato que no se puede aceptar. */
public class AiContractException extends RuntimeException {

    public AiContractException(String message) {
        super(message);
    }

    public AiContractException(String message, Throwable cause) {
        super(message, cause);
    }
}
