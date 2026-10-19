package pe.edu.continental.lagunapaca.security;

import java.time.Instant;

/**
 * Identidad extraída de un JWT válido.
 */
public record JwtPrincipal(String subject, Instant expiresAt) {
}
