package pe.edu.continental.lagunapaca.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.NegatedRequestMatcher;

/**
 * Seguridad del sistema, con <strong>dos superficies y dos mecanismos</strong>.
 *
 * <ul>
 *   <li><strong>Panel web</strong> ({@code /panel/**}): personas en un navegador.
 *       Sesión con formulario, {@code JSESSIONID} y CSRF activo. Es lo que
 *       permite cerrar sesión de verdad y proteger los formularios.</li>
 *   <li><strong>API</strong> ({@code /api/**}): nodos ESP32 y clientes móviles.
 *       Stateless con JWT. Sin sesión, sin cookies y sin CSRF, porque no hay
 *       navegador que las use.</li>
 * </ul>
 *
 * <p>Mezclar ambos sería peor que elegir uno: un JWT guardado en el navegador
 * queda expuesto a XSS, y una sesión con cookies obliga a CSRF en cada
 * petición del ESP32. Por eso hay dos cadenas de filtros y cada una hace lo que
 * su cliente necesita. Un JWT válido <em>no</em> abre el panel.</p>
 *
 * <p>Las credenciales se inyectan por configuración
 * ({@code app.security.user.*}) y en esta iteración viven en memoria; el
 * registro de usuarios es trabajo futuro.</p>
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Value("${app.security.user.username}")
    private String username;

    @Value("${app.security.user.password}")
    private String password;

    /**
     * Cadena del panel web. Va primero porque su matcher es más específico:
     * si la API se evaluara antes, su regla {@code anyRequest().authenticated()}
     * se comería el panel y lo respondería con un 401 en JSON.
     */
    @Bean
    @Order(1)
    SecurityFilterChain panelSecurityFilterChain(HttpSecurity http,
                                                 ObjectMapper objectMapper) throws Exception {
        http
                // La raíz entra aquí porque redirige al panel: si la manejara la
                // cadena de la API, el navegador recibiría un 401 en JSON al
                // escribir la dirección del servidor.
                .securityMatcher("/", "/panel/**", "/login", "/logout")
                .authorizeHttpRequests(auth -> auth
                        // Sin esto no se podría ni ver la SPA de acceso ni pedir
                        // el token CSRF que necesita su formulario.
                        .requestMatchers("/", "/login", "/panel/csrf",
                                "/panel/assets/**", "/panel/favicon.svg").permitAll()
                        .anyRequest().hasRole("ADMIN"))
                // La SPA pide JSON: si la sesión caducó, un 401 explícito es más
                // fácil de detectar que un 302 que termina en el HTML del login.
                // Las páginas normales conservan la redirección al formulario.
                // Hacen falta las dos entradas: con una sola, Spring Security la
                // aplica a todas las peticiones e ignora su matcher.
                .exceptionHandling(ex -> ex
                        .defaultAuthenticationEntryPointFor(
                                new PanelApiAuthenticationEntryPoint(objectMapper),
                                new AntPathRequestMatcher("/panel/api/**"))
                        .defaultAuthenticationEntryPointFor(
                                new LoginUrlAuthenticationEntryPoint("/login"),
                                new NegatedRequestMatcher(
                                        new AntPathRequestMatcher("/panel/api/**"))))
                .formLogin(form -> form
                        .loginPage("/login")
                        .loginProcessingUrl("/login")
                        .defaultSuccessUrl("/panel", true)
                        .failureUrl("/login?error")
                        .permitAll())
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessUrl("/login?logout")
                        .invalidateHttpSession(true)
                        .deleteCookies("JSESSIONID"));
        return http.build();
    }

    /** Cadena de la API: exactamente el contrato que ya estaba, sin cambios. */
    @Bean
    @Order(2)
    SecurityFilterChain apiSecurityFilterChain(HttpSecurity http,
                                               JwtAuthenticationFilter jwtAuthenticationFilter,
                                               AuthenticationEntryPoint restAuthenticationEntryPoint) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/v1/auth/token").permitAll()
                        // Documentación interactiva: expone el contrato, no datos.
                        // Se abre para poder demostrar la API sin pedir un token antes.
                        // En producción conviene restringirla o desactivarla.
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                        .anyRequest().authenticated())
                // 401 (no el 403 por defecto) cuando falta el JWT o no es válido.
                .exceptionHandling(ex -> ex.authenticationEntryPoint(restAuthenticationEntryPoint))
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    AuthenticationEntryPoint restAuthenticationEntryPoint(ObjectMapper objectMapper) {
        return new RestAuthenticationEntryPoint(objectMapper);
    }

    @Bean
    JwtAuthenticationFilter jwtAuthenticationFilter(JwtService jwtService) {
        return new JwtAuthenticationFilter(jwtService);
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    UserDetailsService userDetailsService(PasswordEncoder passwordEncoder) {
        UserDetails user = User.withUsername(username)
                .password(passwordEncoder.encode(password))
                .roles("ADMIN")
                .build();
        return new InMemoryUserDetailsManager(user);
    }

    @Bean
    AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }
}
