package pe.edu.continental.lagunapaca.prediction.service;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.continental.lagunapaca.station.StationCatalog;
import pe.edu.continental.lagunapaca.telemetry.domain.WaterQualitySample;
import pe.edu.continental.lagunapaca.telemetry.domain.WaterQualitySampleRepository;

import java.time.Instant;
import java.util.List;

/**
 * Arma el tensor {@code [estaciones, tiempo, variables]} con las últimas
 * muestras de cada estación.
 *
 * <p><strong>Limitación documentada:</strong> el modelo se entrenó con pasos
 * horarios. Este constructor no remuestrea: toma las últimas {@code inputLen}
 * muestras. Con datos de campo a una muestra por hora coincide exactamente con
 * el entrenamiento; con series de simulación separadas por segundos, la ventana
 * sirve para probar la integración, no para afirmar precisión científica.</p>
 *
 * <p>Si una estación no tiene historia suficiente se lanza
 * {@link IncompleteWindowException}: es preferible a rellenar con ceros o con
 * la última muestra repetida, que inventaría información.</p>
 */
@Component
public class PredictionWindowBuilder {

    private final WaterQualitySampleRepository repository;

    public PredictionWindowBuilder(WaterQualitySampleRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public PredictionWindow construir(int inputLen) {
        if (inputLen <= 0) {
            throw new IllegalArgumentException("inputLen debe ser positivo");
        }

        List<String> estaciones = StationCatalog.IDS;
        double[][][] valores = new double[estaciones.size()][inputLen][4];
        Instant baseTime = null;

        for (int i = 0; i < estaciones.size(); i++) {
            String estacion = estaciones.get(i);
            List<WaterQualitySample> ultimas = repository.findByNodeIdOrderByRecordedAtDesc(
                    estacion, PageRequest.of(0, inputLen));

            if (ultimas.size() < inputLen) {
                throw new IncompleteWindowException(estacion, ultimas.size(), inputLen);
            }

            // El repositorio devuelve de la más reciente a la más antigua; el
            // modelo espera orden cronológico.
            for (int t = 0; t < inputLen; t++) {
                WaterQualitySample muestra = ultimas.get(inputLen - 1 - t);
                valores[i][t][0] = muestra.getPh();
                valores[i][t][1] = muestra.getTurbidityNtu();
                valores[i][t][2] = muestra.getTempC();
                valores[i][t][3] = muestra.getTdsPpm();
            }

            Instant ultima = ultimas.get(0).getRecordedAt();
            if (baseTime == null || ultima.isAfter(baseTime)) {
                baseTime = ultima;
            }
        }

        return new PredictionWindow(estaciones, inputLen, baseTime, valores);
    }
}
