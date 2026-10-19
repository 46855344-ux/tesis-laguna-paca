package pe.edu.continental.lagunapaca.telemetry.service;

import org.springframework.stereotype.Component;
import pe.edu.continental.lagunapaca.telemetry.api.TelemetryRequest;
import pe.edu.continental.lagunapaca.telemetry.domain.AlertCode;
import pe.edu.continental.lagunapaca.telemetry.domain.AlertReason;

import java.util.ArrayList;
import java.util.List;

/**
 * Regla de negocio (ECA-Agua, Categoría 4-E1): se dispara una alerta cuando el
 * pH excede 9.0 o cae por debajo de 6.5 (límites estrictos: los valores exactos
 * 6.5 y 9.0 se consideran aptos).
 */
@Component
public class EcaWaterQualityRule {

    static final double PH_MIN = 6.5;
    static final double PH_MAX = 9.0;

    public AlertEvaluation evaluate(TelemetryRequest request) {
        double ph = request.ph();
        List<AlertReason> reasons = new ArrayList<>(1);

        if (ph > PH_MAX) {
            reasons.add(new AlertReason(AlertCode.PH_HIGH,
                    "pH %.2f supera el límite superior ECA-Agua (%.1f)".formatted(ph, PH_MAX)));
        } else if (ph < PH_MIN) {
            reasons.add(new AlertReason(AlertCode.PH_LOW,
                    "pH %.2f está por debajo del límite inferior ECA-Agua (%.1f)".formatted(ph, PH_MIN)));
        }

        return reasons.isEmpty() ? AlertEvaluation.none() : AlertEvaluation.of(reasons);
    }
}
