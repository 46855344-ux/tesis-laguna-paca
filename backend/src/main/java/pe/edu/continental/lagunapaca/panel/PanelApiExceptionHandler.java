package pe.edu.continental.lagunapaca.panel;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

/**
 * Errores de la API del panel en el mismo formato que consume React.
 *
 * <p>El {@code ResponseStatusException} que usa el servicio de alertas no
 * incluye el motivo en el JSON por defecto. Sin este consejo, un 404 llegaría
 * como un cuerpo vacío y la interfaz no podría explicar qué pasó.</p>
 */
@RestControllerAdvice(assignableTypes = PanelApiController.class)
public class PanelApiExceptionHandler {

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<PanelApiController.ErrorResponse> responseStatus(ResponseStatusException ex) {
        HttpStatus status = HttpStatus.resolve(ex.getStatusCode().value());
        String mensaje = ex.getReason() == null ? "No se pudo completar la operación." : ex.getReason();
        return ResponseEntity.status(status == null ? HttpStatus.INTERNAL_SERVER_ERROR : status)
                .body(new PanelApiController.ErrorResponse(mensaje));
    }
}
