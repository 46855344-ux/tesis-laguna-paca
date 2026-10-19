package pe.edu.continental.lagunapaca.auth;

import jakarta.validation.constraints.NotBlank;

public record TokenRequest(

        @NotBlank(message = "username es obligatorio")
        String username,

        @NotBlank(message = "password es obligatorio")
        String password
) {
}
