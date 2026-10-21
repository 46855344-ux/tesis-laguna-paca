package pe.edu.continental.lagunapaca.migration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verificación de las migraciones contra un PostgreSQL real.
 *
 * <p>No corre en {@code mvn test}: el sufijo {@code IT} queda fuera de las
 * includes de Surefire y, además, exige {@code POSTGRES_URL}. El job
 * {@code backend-postgres} del CI lo ejecuta con un servicio
 * {@code postgres:16-alpine}; en local se puede apuntar a cualquier PostgreSQL
 * 16 con las variables de entorno.</p>
 *
 * <p>La suite normal sigue usando H2; este test cubre lo que H2 no puede:
 * el índice único parcial, {@code TIMESTAMPTZ} y las restricciones
 * {@code CHECK} de {@code prediction}.</p>
 */
@EnabledIfEnvironmentVariable(named = "POSTGRES_URL", matches = ".+")
class PostgresMigrationIT {

    private static final String URL = System.getenv("POSTGRES_URL");
    private static final String USER = System.getenv().getOrDefault("POSTGRES_USER", "laguna");
    private static final String PASSWORD = System.getenv().getOrDefault("POSTGRES_PASSWORD", "laguna");

    @Test
    void aplicaMigracionesYValidaElEsquema() throws Exception {
        Flyway flyway = Flyway.configure()
                .dataSource(URL, USER, PASSWORD)
                .locations("classpath:db/migration")
                .baselineOnMigrate(true)
                .load();
        flyway.migrate();

        try (Connection connection = DriverManager.getConnection(URL, USER, PASSWORD);
             Statement statement = connection.createStatement()) {

            assertTrue(versionesAplicadas(statement).containsAll(List.of("1", "2", "3", "4", "5")),
                    "Faltan migraciones aplicadas");

            for (String column : List.of("sample_id", "acknowledged_at", "acknowledged_by",
                    "acknowledge_note")) {
                assertTrue(existeColumna(statement, "water_quality_sample", column),
                        "Falta water_quality_sample." + column);
            }
            for (String column : List.of("model_run_id", "target_at", "predicted_value",
                    "data_source", "status", "observed_value", "prediction_error",
                    "evaluated_at")) {
                assertTrue(existeColumna(statement, "prediction", column),
                        "Falta prediction." + column);
            }

            assertTrue(tipoDeColumna(statement, "water_quality_sample", "recorded_at")
                            .contains("timestamp with time zone"),
                    "recorded_at debe ser TIMESTAMPTZ");

            String indexDefinition = definicionIndice(statement);
            assertTrue(indexDefinition.contains("WHERE (sample_id IS NOT NULL)"),
                    "El índice de sample_id debe ser parcial: " + indexDefinition);

            assertEquals(4, restriccionesDePrediccion(statement),
                    "Faltan restricciones CHECK en prediction");

            comprobarIdempotencia(statement);
            comprobarEvaluacionIncompleta(statement);
        }
    }

    private List<String> versionesAplicadas(Statement statement) throws SQLException {
        List<String> versions = new ArrayList<>();
        try (ResultSet result = statement.executeQuery(
                "SELECT version FROM flyway_schema_history WHERE success ORDER BY installed_rank")) {
            while (result.next()) {
                versions.add(result.getString(1));
            }
        }
        return versions;
    }

    private boolean existeColumna(Statement statement, String table, String column) throws SQLException {
        try (ResultSet result = statement.executeQuery(
                "SELECT 1 FROM information_schema.columns WHERE table_schema = 'public' "
                        + "AND table_name = '" + table + "' AND column_name = '" + column + "'")) {
            return result.next();
        }
    }

    private String tipoDeColumna(Statement statement, String table, String column) throws SQLException {
        try (ResultSet result = statement.executeQuery(
                "SELECT data_type FROM information_schema.columns WHERE table_schema = 'public' "
                        + "AND table_name = '" + table + "' AND column_name = '" + column + "'")) {
            return result.next() ? result.getString(1) : "";
        }
    }

    private String definicionIndice(Statement statement) throws SQLException {
        try (ResultSet result = statement.executeQuery(
                "SELECT indexdef FROM pg_indexes WHERE tablename = 'water_quality_sample' "
                        + "AND indexname = 'ux_water_quality_sample_sample_id'")) {
            return result.next() ? result.getString(1) : "";
        }
    }

    private int restriccionesDePrediccion(Statement statement) throws SQLException {
        try (ResultSet result = statement.executeQuery(
                "SELECT count(*) FROM pg_constraint WHERE conrelid = 'prediction'::regclass "
                        + "AND conname IN ('ck_prediction_horizon', 'ck_prediction_status', "
                        + "'ck_prediction_target', 'ck_prediction_evaluation')")) {
            result.next();
            return result.getInt(1);
        }
    }

    private void comprobarIdempotencia(Statement statement) throws SQLException {
        statement.executeUpdate("DELETE FROM water_quality_sample WHERE sample_id LIKE 'N1-IT-%'");

        String insert = "INSERT INTO water_quality_sample "
                + "(node_id, ph, turbidity_ntu, temp_c, tds_ppm, recorded_at, sample_id) "
                + "VALUES ('N1', 7.20, 15.40, 18.50, 320.00, now(), ";

        statement.executeUpdate(insert + "'N1-IT-0001')");
        SQLException duplicate = assertThrows(SQLException.class,
                () -> statement.executeUpdate(insert + "'N1-IT-0001')"));
        assertEquals("23505", duplicate.getSQLState(), "El duplicado debe violar el índice único");

        statement.executeUpdate(insert + "NULL)");
        statement.executeUpdate(insert + "NULL)");
    }

    private void comprobarEvaluacionIncompleta(Statement statement) throws SQLException {
        SQLException invalid = assertThrows(SQLException.class, () -> statement.executeUpdate(
                "INSERT INTO prediction (model_run_id, node_id, variable, horizon_hours, "
                        + "generated_at, target_at, predicted_value, model_version, data_source, status) "
                        + "VALUES (gen_random_uuid(), 'N1', 'ph', 6, now(), "
                        + "now() + interval '6 hours', 7.31, 'stgnn-it', 'synthetic', 'EVALUATED')"));
        assertEquals("23514", invalid.getSQLState(),
                "Una predicción EVALUATED incompleta debe violar el CHECK");
    }
}
