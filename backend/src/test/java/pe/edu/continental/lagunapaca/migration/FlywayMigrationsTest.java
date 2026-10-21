package pe.edu.continental.lagunapaca.migration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guardia de las migraciones Flyway.
 *
 * <p><strong>Limitación honesta:</strong> sin Docker ni PostgreSQL no se puede
 * ejecutar Flyway contra el motor real; el perfil de pruebas usa H2 y genera el
 * esquema con Hibernate. Esta prueba no sustituye una prueba de migración: fija
 * las propiedades que sí se pueden verificar sin motor (versiones contiguas y
 * ausencia de operaciones destructivas). La ejecución real queda documentada
 * como pendiente en {@code docs/SDD/28-registro-de-evidencias.md}.</p>
 */
@DisplayName("Migraciones Flyway")
class FlywayMigrationsTest {

    private static final Path DIRECTORIO = Path.of("src/main/resources/db/migration");
    private static final Pattern NOMBRE = Pattern.compile("V(\\d+)__.+\\.sql");

    @Test
    @DisplayName("las versiones son contiguas, únicas y empiezan en 1")
    void versionesContiguas() throws IOException {
        List<Integer> versiones = Files.list(DIRECTORIO)
                .map(p -> p.getFileName().toString())
                .map(NOMBRE::matcher)
                .filter(Matcher::matches)
                .map(m -> Integer.parseInt(m.group(1)))
                .sorted()
                .toList();

        assertThat(versiones).isNotEmpty();
        assertThat(versiones).doesNotHaveDuplicates();
        assertThat(versiones).containsExactlyElementsOf(
                java.util.stream.IntStream.rangeClosed(1, versiones.size()).boxed().toList());
    }

    @Test
    @DisplayName("la V5 es aditiva: no borra ni trunca datos existentes")
    void v5EsAditiva() throws IOException {
        String sql = Files.readString(DIRECTORIO.resolve("V5__predictions_and_idempotency.sql"))
                .toUpperCase(Locale.ROOT);

        assertThat(sql)
                .contains("CREATE TABLE PREDICTION")
                .contains("ADD COLUMN SAMPLE_ID")
                .contains("CREATE UNIQUE INDEX")
                .doesNotContain("DROP TABLE")
                .doesNotContain("TRUNCATE")
                .doesNotContain("DELETE FROM")
                .doesNotContain("ALTER COLUMN");
    }

    @Test
    @DisplayName("las predicciones no se guardan en la tabla de mediciones")
    void prediccionesSeparadasDeMediciones() {
        assertThat(Files.exists(DIRECTORIO.resolve("V5__predictions_and_idempotency.sql"))).isTrue();

        // La separación es una regla de negocio (RN-06), no un detalle de esquema:
        // la tabla water_quality_sample no debe ganar columnas de predicción.
        assertThat(contenidoV5()).doesNotContain("ALTER TABLE WATER_QUALITY_SAMPLE ADD COLUMN PREDICTED");
    }

    private static String contenidoV5() {
        try {
            return Files.readString(DIRECTORIO.resolve("V5__predictions_and_idempotency.sql"))
                    .toUpperCase(Locale.ROOT);
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
