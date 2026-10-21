package pe.edu.continental.lagunapaca.prediction.api;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.List;

/**
 * Estado del servicio de inferencia, tal como lo ve el backend.
 *
 * <p>Nunca lanza: si FastAPI está caído se responde con {@code available=false}
 * y un detalle. Un monitor de salud no debe fallar solo porque el servicio que
 * vigila esté caído.</p>
 */
public record AiStatusResponse(

        boolean available,

        @JsonProperty("model_version")
        String modelVersion,

        List<Integer> horizons,

        @JsonProperty("input_len")
        Integer inputLen,

        @JsonProperty("data_source")
        String dataSource,

        /** Explicación legible cuando no está disponible. */
        String detail,

        @JsonProperty("checked_at")
        Instant checkedAt
) {

    public static AiStatusResponse unavailable(String detail, Instant checkedAt) {
        return new AiStatusResponse(false, null, List.of(), null, null, detail, checkedAt);
    }
}
