package pe.edu.continental.lagunapaca.prediction.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import pe.edu.continental.lagunapaca.prediction.domain.Prediction;
import pe.edu.continental.lagunapaca.prediction.domain.PredictionRepository;
import pe.edu.continental.lagunapaca.prediction.domain.PredictionStatus;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@DisplayName("Evaluación posterior de predicciones")
class PredictionEvaluatorTest {

    private static final Instant AHORA = Instant.parse("2026-10-20T16:00:00Z");

    @Autowired
    private PredictionRepository repository;

    private PredictionEvaluator evaluator;

    @BeforeEach
    void setUp() {
        evaluator = new PredictionEvaluator(repository, Clock.fixed(AHORA, ZoneOffset.UTC));
    }

    private Prediction pendiente(String nodeId, Instant target, double previsto) {
        return repository.save(new Prediction(
                UUID.randomUUID(), nodeId, "ph", 6,
                target.minus(6, ChronoUnit.HOURS), target, previsto,
                "stgnn-test", "synthetic"));
    }

    @Test
    @DisplayName("una muestra en el objetivo evalúa y guarda observado y error")
    void muestraEnElObjetivo_evalua() {
        Prediction prediccion = pendiente("N1", AHORA, 7.40);

        int evaluadas = evaluator.evaluar("N1", AHORA, 7.20);

        assertThat(evaluadas).isEqualTo(1);
        Prediction guardada = repository.findById(prediccion.getId()).orElseThrow();
        assertThat(guardada.getStatus()).isEqualTo(PredictionStatus.EVALUATED);
        assertThat(guardada.getObservedValue()).isEqualTo(7.20);
        assertThat(guardada.getError()).isCloseTo(0.20, org.assertj.core.data.Offset.offset(1e-9));
        assertThat(guardada.getEvaluatedAt()).isEqualTo(AHORA);
        // El valor previsto es evidencia y no se reescribe.
        assertThat(guardada.getPredictedValue()).isEqualTo(7.40);
    }

    @Test
    @DisplayName("acepta una muestra dentro de la tolerancia de ±30 min")
    void muestraDentroDeLaTolerancia_evalua() {
        Prediction prediccion = pendiente("N1", AHORA, 7.40);

        int evaluadas = evaluator.evaluar("N1", AHORA.plus(25, ChronoUnit.MINUTES), 7.30);

        assertThat(evaluadas).isEqualTo(1);
        assertThat(repository.findById(prediccion.getId()).orElseThrow().getStatus())
                .isEqualTo(PredictionStatus.EVALUATED);
    }

    @Test
    @DisplayName("una muestra fuera de la tolerancia no evalúa nada")
    void muestraFueraDeLaTolerancia_noEvalua() {
        Prediction prediccion = pendiente("N1", AHORA, 7.40);

        int evaluadas = evaluator.evaluar("N1", AHORA.plus(2, ChronoUnit.HOURS), 7.30);

        assertThat(evaluadas).isZero();
        assertThat(repository.findById(prediccion.getId()).orElseThrow().getStatus())
                .isEqualTo(PredictionStatus.PENDING);
    }

    @Test
    @DisplayName("no evalúa predicciones de otra estación")
    void otraEstacion_noEvalua() {
        Prediction prediccion = pendiente("N2", AHORA, 7.40);

        assertThat(evaluator.evaluar("N1", AHORA, 7.20)).isZero();
        assertThat(repository.findById(prediccion.getId()).orElseThrow().getStatus())
                .isEqualTo(PredictionStatus.PENDING);
    }

    @Test
    @DisplayName("la variante segura absorbe errores y no rompe la ingesta")
    void varianteSegura_noPropagaErrores() {
        // Repositorio nulo a propósito: el método debe absorber la excepción.
        PredictionEvaluator roto = new PredictionEvaluator(null, Clock.systemUTC());

        roto.evaluarSinRomperIngesta("N1", AHORA, 7.2);
    }
}
