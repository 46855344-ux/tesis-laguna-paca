package pe.edu.continental.lagunapaca.telemetry.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pe.edu.continental.lagunapaca.telemetry.api.TelemetryRequest;
import pe.edu.continental.lagunapaca.telemetry.domain.AlertCode;
import pe.edu.continental.lagunapaca.telemetry.domain.AlertReason;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Regla ECA-Agua para el pH")
class EcaWaterQualityRuleTest {

    private final EcaWaterQualityRule rule = new EcaWaterQualityRule();

    private static TelemetryRequest sample(double ph) {
        return new TelemetryRequest("N1", ph, 15.4, 18.5, 320.0);
    }

    @Test
    @DisplayName("pH dentro de rango no dispara alerta")
    void phDentroDeRango_noDisparaAlerta() {
        AlertEvaluation result = rule.evaluate(sample(7.2));

        assertThat(result.alert()).isFalse();
        assertThat(result.reasons()).isEmpty();
    }

    @Test
    @DisplayName("pH mayor a 9.0 dispara alerta con código PH_HIGH")
    void phMayorAlLimiteSuperior_disparaAlerta() {
        AlertEvaluation result = rule.evaluate(sample(9.05));

        assertThat(result.alert()).isTrue();
        assertThat(result.reasons()).extracting(AlertReason::getCode).containsExactly(AlertCode.PH_HIGH);
        assertThat(result.reasons()).extracting(AlertReason::getMessage).allMatch(m -> !m.isBlank());
    }

    @Test
    @DisplayName("pH menor a 6.5 dispara alerta con código PH_LOW")
    void phMenorAlLimiteInferior_disparaAlerta() {
        AlertEvaluation result = rule.evaluate(sample(6.4));

        assertThat(result.alert()).isTrue();
        assertThat(result.reasons()).extracting(AlertReason::getCode).containsExactly(AlertCode.PH_LOW);
    }

    @Test
    @DisplayName("los límites exactos (6.5 y 9.0) NO disparan alerta")
    void limitesExactos_noDisparanAlerta() {
        assertThat(rule.evaluate(sample(6.5)).alert()).isFalse();
        assertThat(rule.evaluate(sample(9.0)).alert()).isFalse();
    }
}
