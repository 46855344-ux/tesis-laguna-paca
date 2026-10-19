package pe.edu.continental.lagunapaca.telemetry.domain;

import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.util.Locale;

/**
 * Filtros reutilizables para consultar {@link WaterQualitySample}. Cada uno
 * devuelve una conjunción (no-op) cuando su criterio es {@code null}.
 */
public final class WaterQualitySampleSpecifications {

    private WaterQualitySampleSpecifications() {
    }

    public static Specification<WaterQualitySample> hasNodeId(String nodeId) {
        return (root, query, cb) -> (nodeId == null || nodeId.isBlank())
                ? cb.conjunction()
                : cb.equal(root.get("nodeId"), nodeId);
    }

    public static Specification<WaterQualitySample> recordedAtFrom(Instant from) {
        return (root, query, cb) -> (from == null)
                ? cb.conjunction()
                : cb.greaterThanOrEqualTo(root.get("recordedAt"), from);
    }

    public static Specification<WaterQualitySample> recordedAtTo(Instant to) {
        return (root, query, cb) -> (to == null)
                ? cb.conjunction()
                : cb.lessThanOrEqualTo(root.get("recordedAt"), to);
    }

    /** Filtra por el resumen {@code alert}. {@code null} = sin filtro. */
    public static Specification<WaterQualitySample> hasAlert(Boolean alert) {
        return (root, query, cb) -> (alert == null)
                ? cb.conjunction()
                : cb.equal(root.get("alert"), alert);
    }

    /**
     * Filtra por coincidencia (case-insensitive) dentro del mensaje del motivo.
     * {@code null}/vacío = sin filtro. Usa {@code EXISTS} para no duplicar filas.
     */
    public static Specification<WaterQualitySample> hasReasonContaining(String fragment) {
        return (root, query, cb) -> {
            if (fragment == null || fragment.isBlank()) {
                return cb.conjunction();
            }
            String pattern = "%" + fragment.toLowerCase(Locale.ROOT) + "%";
            Subquery<Integer> subquery = query.subquery(Integer.class);
            Root<WaterQualitySample> correlated = subquery.correlate(root);
            Join<WaterQualitySample, AlertReason> reasons = correlated.join("alertReasons");
            subquery.select(cb.literal(1))
                    .where(cb.like(cb.lower(reasons.<String>get("message")), pattern));
            return cb.exists(subquery);
        };
    }

    /**
     * Filtra por código exacto de motivo ({@code PH_HIGH} / {@code PH_LOW}).
     * {@code null} = sin filtro. Usa {@code EXISTS} para no duplicar filas.
     */
    public static Specification<WaterQualitySample> hasCode(AlertCode code) {
        return (root, query, cb) -> {
            if (code == null) {
                return cb.conjunction();
            }
            Subquery<Integer> subquery = query.subquery(Integer.class);
            Root<WaterQualitySample> correlated = subquery.correlate(root);
            Join<WaterQualitySample, AlertReason> reasons = correlated.join("alertReasons");
            subquery.select(cb.literal(1))
                    .where(cb.equal(reasons.get("code"), code));
            return cb.exists(subquery);
        };
    }
}
