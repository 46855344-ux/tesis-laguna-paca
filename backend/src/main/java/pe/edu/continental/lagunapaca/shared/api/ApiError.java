package pe.edu.continental.lagunapaca.shared.api;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.List;

/**
 * Cuerpo de error uniforme para toda la API.
 */
public record ApiError(

        Instant timestamp,
        int status,
        String error,
        String message,
        String path,

        @JsonProperty("field_errors")
        List<FieldError> fieldErrors
) {
    public record FieldError(String field, String message) {
    }
}
