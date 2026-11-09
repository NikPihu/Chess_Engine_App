package com.gambyte.config;

import com.gambyte.bot.BotTokenFilter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.function.Supplier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.csrf.CsrfTokenRequestHandler;
import org.springframework.security.web.csrf.XorCsrfTokenRequestAttributeHandler;
import org.springframework.util.StringUtils;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Who may call what, and how users prove who they are.
 *
 * <ul>
 *   <li><b>Browsers</b> use a session cookie (HttpOnly, stored in Redis by Spring Session) plus a
 *       CSRF token: the server sets an {@code XSRF-TOKEN} cookie, the frontend copies it into an
 *       {@code X-XSRF-TOKEN} header on every change. See ADR-0004.
 *   <li><b>Bots</b> use {@code Authorization: Bearer <api token>} on {@code /api/bot/**} and
 *       {@code /api/account}; {@link BotTokenFilter} handles it. No cookies, so no CSRF there.
 *   <li><b>Payment webhooks</b> are verified by signature in the payment feature, not by login.
 * </ul>
 *
 * Fine-grained rules ("only a player of this game may move") live in the services.
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

  @Bean
  SecurityFilterChain filterChain(
      HttpSecurity http, BotTokenFilter botTokenFilter, GambyteProperties props) throws Exception {
    http.cors(cors -> cors.configurationSource(corsSource(props)))
        .csrf(
            csrf ->
                csrf.csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                    .csrfTokenRequestHandler(new SpaCsrfTokenRequestHandler())
                    // Token-authenticated calls carry no cookies, so CSRF cannot apply to them.
                    .ignoringRequestMatchers(
                        request -> {
                          String auth = request.getHeader("Authorization");
                          return auth != null && auth.startsWith("Bearer ");
                        })
                    .ignoringRequestMatchers("/api/payments/webhook", "/ws/**"))
        .addFilterAfter(new CsrfCookieFilter(), BasicAuthenticationFilter.class)
        .addFilterBefore(botTokenFilter, BasicAuthenticationFilter.class)
        .authorizeHttpRequests(
            auth ->
                auth
                    // Public pages and data
                    .requestMatchers(
                        "/api/auth/**",
                        "/actuator/health/**",
                        "/actuator/info",
                        "/swagger-ui.html",
                        "/swagger-ui/**",
                        "/v3/api-docs/**",
                        "/ws/**",
                        "/api/payments/webhook")
                    .permitAll()
                    .requestMatchers(
                        HttpMethod.GET,
                        "/api/users/**",
                        "/api/games/**",
                        "/api/leaderboard/**",
                        "/api/puzzles/daily",
                        "/api/lessons/**",
                        "/api/tv/**",
                        "/api/tournaments/**",
                        "/api/premium/plans",
                        "/api/lobby/**",
                        "/api/stats/**")
                    .permitAll()
                    // Staff only
                    .requestMatchers("/api/admin/**")
                    .hasRole("MOD")
                    .requestMatchers("/actuator/**")
                    .hasRole("ADMIN")
                    // Everything else needs a login
                    .anyRequest()
                    .authenticated())
        // A JSON API answers 401 instead of redirecting to a login page.
        .exceptionHandling(e -> e.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
        .formLogin(f -> f.disable())
        .httpBasic(b -> b.disable())
        .logout(l -> l.disable()) // done by AuthController so the response is JSON
        .headers(
            h ->
                h.contentSecurityPolicy(
                        csp ->
                            csp.policyDirectives(
                                "default-src 'self'; frame-ancestors 'none'; object-src 'none'"))
                    .referrerPolicy(
                        r ->
                            r.policy(
                                org.springframework.security.web.header.writers
                                    .ReferrerPolicyHeaderWriter.ReferrerPolicy
                                    .STRICT_ORIGIN_WHEN_CROSS_ORIGIN)));
    return http.build();
  }

  /**
   * Argon2id for new passwords (memory-hard, resists GPU cracking); the delegating encoder also
   * reads older formats (bcrypt...) so hashes can be upgraded later without forcing resets.
   */
  @Bean
  PasswordEncoder passwordEncoder() {
    var encoders = new java.util.HashMap<String, PasswordEncoder>();
    encoders.put("argon2", Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8());
    encoders.put("bcrypt", new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder());
    return new DelegatingPasswordEncoder("argon2", encoders);
  }

  @Bean
  AuthenticationManager authenticationManager(
      UserDetailsService userDetailsService, PasswordEncoder passwordEncoder) {
    DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
    provider.setPasswordEncoder(passwordEncoder);
    return new ProviderManager(provider);
  }

  /**
   * {@link BotTokenFilter} is a Spring bean, and Spring Boot would also register every filter bean
   * with the servlet container; this stops that, so it runs once, inside the security chain.
   */
  @Bean
  org.springframework.boot.web.servlet.FilterRegistrationBean<BotTokenFilter> botFilterRegistration(
      BotTokenFilter filter) {
    var registration = new org.springframework.boot.web.servlet.FilterRegistrationBean<>(filter);
    registration.setEnabled(false);
    return registration;
  }

  /** Where a logged-in SecurityContext is stored: the HTTP session (which lives in Redis). */
  @Bean
  SecurityContextRepository securityContextRepository() {
    return new HttpSessionSecurityContextRepository();
  }

  /**
   * In development the frontend runs on Vite's port (5173) and calls the API on 8080; in
   * production both are served from one origin by Caddy, so this mostly matters locally.
   */
  private static CorsConfigurationSource corsSource(GambyteProperties props) {
    CorsConfiguration cors = new CorsConfiguration();
    cors.setAllowedOrigins(List.of(props.siteUrl()));
    cors.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
    cors.setAllowedHeaders(List.of("*"));
    cors.setAllowCredentials(true);
    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", cors);
    return source;
  }

  /**
   * The CSRF handler recommended by Spring for single-page apps: accepts the raw token from the
   * header (what the SPA sends) while still protecting server-rendered forms against BREACH.
   */
  static final class SpaCsrfTokenRequestHandler implements CsrfTokenRequestHandler {
    private final CsrfTokenRequestHandler plain = new CsrfTokenRequestAttributeHandler();
    private final CsrfTokenRequestHandler xor = new XorCsrfTokenRequestAttributeHandler();

    @Override
    public void handle(
        HttpServletRequest request, HttpServletResponse response, Supplier<CsrfToken> token) {
      xor.handle(request, response, token);
      token.get(); // render the token so the cookie is written
    }

    @Override
    public String resolveCsrfTokenValue(HttpServletRequest request, CsrfToken csrfToken) {
      String header = request.getHeader(csrfToken.getHeaderName());
      return (StringUtils.hasText(header) ? plain : xor).resolveCsrfTokenValue(request, csrfToken);
    }
  }

  /** Makes sure the XSRF-TOKEN cookie is sent on every response (Spring creates it lazily). */
  static final class CsrfCookieFilter extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(
        HttpServletRequest request, HttpServletResponse response, FilterChain chain)
        throws ServletException, IOException {
      CsrfToken token = (CsrfToken) request.getAttribute("_csrf");
      if (token != null) token.getToken();
      chain.doFilter(request, response);
    }
  }
}
