package pe.edu.continental.lagunapaca.telemetry.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

import java.util.Objects;

/**
 * Motivo de alerta: {@link #code} para lógica y {@link #message} para mostrar.
 * Se persiste como fila hija en la tabla {@code alert_reason}.
 */
@Embeddable
public class AlertReason {

    @Enumerated(EnumType.STRING)
    @Column(name = "code", nullable = false, length = 20)
    private AlertCode code;

    @Column(name = "message", nullable = false, length = 255)
    private String message;

    protected AlertReason() {
        // Requerido por JPA
    }

    public AlertReason(AlertCode code, String message) {
        this.code = code;
        this.message = message;
    }

    public AlertCode getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof AlertReason other)) {
            return false;
        }
        return code == other.code && Objects.equals(message, other.message);
    }

    @Override
    public int hashCode() {
        return Objects.hash(code, message);
    }

    @Override
    public String toString() {
        return "AlertReason[code=%s, message=%s]".formatted(code, message);
    }
}
