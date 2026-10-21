package pe.edu.continental.lagunapaca.prediction.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import pe.edu.continental.lagunapaca.station.StationCatalog;
import pe.edu.continental.lagunapaca.telemetry.domain.WaterQualitySample;
import pe.edu.continental.lagunapaca.telemetry.domain.WaterQualitySampleRepository;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@DisplayName("Constructor de ventanas del ST-GNN")
class PredictionWindowBuilderTest {

    private static final Instant BASE = Instant.parse("2026-10-20T10:00:00Z");

    @Autowired
    private WaterQualitySampleRepository repository;

    private PredictionWindowBuilder builder;

    @BeforeEach
    void setUp() {
        builder = new PredictionWindowBuilder(repository);
    }

    private void sembrarEstacion(String nodeId, int muestras, Instant ultima) {
        for (int i = 0; i < muestras; i++) {
            Instant cuando = ultima.minus(muestras - 1 - i, ChronoUnit.HOURS);
            repository.save(new WaterQualitySample(nodeId, 7.0 + i * 0.01, 15.0, 18.0, 320.0, cuando));
        }
    }

    private void sembrarTodas(int muestras, Instant ultima) {
        for (String estacion : StationCatalog.IDS) {
            sembrarEstacion(estacion, muestras, ultima);
        }
    }

    @Test
    @DisplayName("arma [4 estaciones][24][4] en orden cronológico")
    void construyeElTensorEnOrdenCronologico() {
        sembrarTodas(24, BASE);

        PredictionWindow ventana = builder.construir(24);

        assertThat(ventana.estaciones()).containsExactly("N1", "N2", "N3", "N4");
        assertThat(ventana.inputLen()).isEqualTo(24);
        assertThat(ventana.valores().length).isEqualTo(4);
        assertThat(ventana.valores()[0].length).isEqualTo(24);
        assertThat(ventana.valores()[0][0].length).isEqualTo(4);
        // La primera fila es la muestra más antigua, no la más reciente.
        assertThat(ventana.valores()[0][0][0]).isEqualTo(7.0);
        assertThat(ventana.valores()[0][23][0]).isCloseTo(7.23, org.assertj.core.data.Offset.offset(1e-9));
        assertThat(ventana.baseTime()).isEqualTo(BASE);
    }

    @Test
    @DisplayName("la base es la última muestra más reciente entre estaciones")
    void baseTimeEsLaUltimaMuestra() {
        sembrarTodas(24, BASE);
        repository.save(new WaterQualitySample("N2", 7.5, 15.0, 18.0, 320.0,
                BASE.plus(5, ChronoUnit.MINUTES)));

        PredictionWindow ventana = builder.construir(24);

        assertThat(ventana.baseTime()).isEqualTo(BASE.plus(5, ChronoUnit.MINUTES));
    }

    @Test
    @DisplayName("si una estación no tiene historia suficiente, falla con un mensaje claro")
    void ventanaIncompleta_lanzaExcepcion() {
        sembrarTodas(24, BASE);
        // N3 se queda con solo 10 muestras: se borra el resto.
        repository.deleteAll(repository.findByNodeIdOrderByRecordedAtDesc("N3").stream()
                .skip(10)
                .toList());

        assertThatThrownBy(() -> builder.construir(24))
                .isInstanceOf(IncompleteWindowException.class)
                .hasMessageContaining("N3")
                .hasMessageContaining("necesita 24");
    }

    @Test
    @DisplayName("no inventa ceros: una estación sin datos también falla")
    void estacionSinDatos_lanzaExcepcion() {
        sembrarEstacion("N1", 24, BASE);
        sembrarEstacion("N2", 24, BASE);
        sembrarEstacion("N3", 24, BASE);

        assertThatThrownBy(() -> builder.construir(24))
                .isInstanceOf(IncompleteWindowException.class)
                .hasMessageContaining("N4");
    }
}
