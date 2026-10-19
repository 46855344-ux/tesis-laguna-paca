package pe.edu.continental.lagunapaca.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Servicio JWT")
class JwtServiceTest {

    private static final String SECRET = "0123456789abcdef0123456789abcdef0123456789abcdef";
    private static final String ISSUER = "laguna-paca-test";

    private static JwtService service(Duration ttl) {
        return new JwtService(SECRET, ISSUER, ttl);
    }

    @Test
    @DisplayName("un token recién emitido es válido y conserva el sujeto")
    void tokenEmitido_esValido() {
        JwtService jwtService = service(Duration.ofHours(2));

        Optional<JwtPrincipal> parsed = jwtService.parse(jwtService.generate("admin"));

        assertThat(parsed).isPresent();
        assertThat(parsed.get().subject()).isEqualTo("admin");
    }

    @Test
    @DisplayName("un token con el payload alterado es rechazado")
    void tokenConPayloadAlterado_esRechazado() {
        JwtService jwtService = service(Duration.ofHours(2));
        String valid = jwtService.generate("admin");

        String[] parts = valid.split("\\.");
        String tamperedPayload = parts[1].substring(0, parts[1].length() - 1)
                + (parts[1].endsWith("A") ? "B" : "A");
        String tampered = parts[0] + "." + tamperedPayload + "." + parts[2];

        assertThat(jwtService.parse(tampered)).isEmpty();
    }

    @Test
    @DisplayName("un token firmado con otro secreto es rechazado")
    void tokenConOtroSecreto_esRechazado() {
        JwtService legitimo = service(Duration.ofHours(2));
        JwtService atacante = new JwtService(
                "otro-secreto-distinto-y-suficientemente-largo-0000", ISSUER, Duration.ofHours(2));

        assertThat(legitimo.parse(atacante.generate("admin"))).isEmpty();
    }

    @Test
    @DisplayName("un token expirado es rechazado")
    void tokenExpirado_esRechazado() {
        JwtService jwtService = service(Duration.ofSeconds(-60));

        assertThat(jwtService.parse(jwtService.generate("admin"))).isEmpty();
    }

    @Test
    @DisplayName("un token de otro emisor es rechazado")
    void tokenDeOtroEmisor_esRechazado() {
        JwtService emisorA = new JwtService(SECRET, "emisor-a", Duration.ofHours(2));
        JwtService emisorB = new JwtService(SECRET, "emisor-b", Duration.ofHours(2));

        assertThat(emisorB.parse(emisorA.generate("admin"))).isEmpty();
    }
}
