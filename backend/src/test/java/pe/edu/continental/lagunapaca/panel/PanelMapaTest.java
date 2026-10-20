package pe.edu.continental.lagunapaca.panel;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * El modelo del mapa se sirve por dos vías: la plantilla (HTML) y
 * {@code /panel/datos} (JSON). La clave de Google solo debe viajar en la
 * primera, porque el navegador la necesita para pedir el mapa y el refresco no.
 */
@DisplayName("Modelo del mapa")
class PanelMapaTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("la clave de Google no se serializa al JSON")
    void laClaveNoSeSerializa() throws Exception {
        PanelMapa mapa = new PanelMapa(
                "0 0 720 460", "M 0 0 Z", List.of(), "clave-secreta");

        String json = objectMapper.writeValueAsString(mapa);

        assertThat(json)
                .as("la clave se pinta en el HTML, pero no tiene por qué viajar en cada refresco")
                .doesNotContain("clave-secreta")
                .doesNotContain("googleKey");
        assertThat(json).contains("\"viewBox\"").contains("\"puntos\"");
    }

    @Test
    @DisplayName("los puntos llevan coordenadas reales para el mapa de Google")
    void losPuntosLlevanCoordenadas() throws Exception {
        PanelMapa mapa = new PanelMapa("0 0 720 460", "M 0 0 Z", List.of(
                new PanelMapa.Punto("N1", 318, 40, -11.71703, -75.506971,
                        "exito", "7.20", "N1 · pH 7.20 · Dentro del rango")),
                "");

        String json = objectMapper.writeValueAsString(mapa);

        assertThat(json)
                .contains("\"lat\":-11.71703")
                .contains("\"lon\":-75.506971")
                .contains("\"clase\":\"exito\"");
    }
}
