package com.gambyte.auth;

import com.gambyte.common.ApiException;
import com.gambyte.common.ClientIp;
import com.gambyte.common.RateLimiter;
import com.gambyte.user.api.UserDtos.ForgotPasswordRequest;
import com.gambyte.user.api.UserDtos.LoginRequest;
import com.gambyte.user.api.UserDtos.Me;
import com.gambyte.user.api.UserDtos.RegisterRequest;
import com.gambyte.user.api.UserDtos.ResetPasswordRequest;
import com.gambyte.user.api.UserDtos.TokenRequest;
import com.gambyte.user.domain.User;
import com.gambyte.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import java.time.Duration;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Login, logout, registration and the email-link flows.
 *
 * <p>Login is done here (instead of Spring's form login) so the API speaks JSON. After a successful
 * login the session id is changed (prevents "session fixation") and the security context is saved
 * into the session, which Spring Session keeps in Redis.
 */
@RestController
@RequestMapping("/api/auth")
@Tag(name = "Auth", description = "Accounts, login and sessions")
public class AuthController {

  private final AuthenticationManager authManager;
  private final SecurityContextRepository contextRepository;
  private final UserService users;
  private final RateLimiter limiter;

  public AuthController(
      AuthenticationManager authManager,
      SecurityContextRepository contextRepository,
      UserService users,
      RateLimiter limiter) {
    this.authManager = authManager;
    this.contextRepository = contextRepository;
    this.users = users;
    this.limiter = limiter;
  }

  @Operation(summary = "Get a CSRF token (also sets the XSRF-TOKEN cookie)")
  @GetMapping("/csrf")
  public Map<String, String> csrf(CsrfToken token) {
    return Map.of("headerName", token.getHeaderName(), "token", token.getToken());
  }

  @Operation(summary = "The logged-in account, or 401")
  @GetMapping("/me")
  public ResponseEntity<Me> me(@AuthenticationPrincipal AuthUser me) {
    if (me == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    return ResponseEntity.ok(users.me(me.id()));
  }

  @Operation(summary = "Create an account and log in")
  @PostMapping("/register")
  public Me register(
      @Valid @RequestBody RegisterRequest body,
      HttpServletRequest request,
      HttpServletResponse response) {
    limiter.check("register", ClientIp.of(request), 5, Duration.ofHours(1));
    User user = users.register(body.username(), body.email(), body.password());
    AuthUser principal = AuthUser.of(user).withoutPassword();
    startSession(
        new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()),
        request,
        response);
    return users.me(user.getId());
  }

  @Operation(summary = "Log in with username (or email) and password")
  @PostMapping("/login")
  public Me login(
      @Valid @RequestBody LoginRequest body,
      HttpServletRequest request,
      HttpServletResponse response) {
    // Two limits: per IP (stops one machine guessing many accounts) and per account (stops many
    // machines guessing one account).
    limiter.check("login-ip", ClientIp.of(request), 20, Duration.ofMinutes(10));
    limiter.check("login-account", body.login().toLowerCase(), 10, Duration.ofMinutes(10));
    Authentication auth;
    try {
      auth =
          authManager.authenticate(
              new UsernamePasswordAuthenticationToken(body.login(), body.password()));
    } catch (LockedException e) {
      throw ApiException.forbidden("account_closed", "This account is closed");
    } catch (BadCredentialsException e) {
      throw new ApiException(
          HttpStatus.UNAUTHORIZED, "bad_credentials", "Wrong username or password");
    } catch (AuthenticationException e) {
      throw new ApiException(
          HttpStatus.UNAUTHORIZED, "bad_credentials", "Wrong username or password");
    }
    AuthUser principal = ((AuthUser) auth.getPrincipal()).withoutPassword();
    startSession(
        new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()),
        request,
        response);
    users.touchLastSeen(principal.id());
    return users.me(principal.id());
  }

  @Operation(summary = "Log out (ends the session)")
  @PostMapping("/logout")
  public ResponseEntity<Void> logout(HttpServletRequest request) {
    HttpSession session = request.getSession(false);
    if (session != null) session.invalidate();
    SecurityContextHolder.clearContext();
    return ResponseEntity.noContent().build();
  }

  @Operation(summary = "Confirm an email address with the token from the email")
  @PostMapping("/verify-email")
  public ResponseEntity<Void> verify(@Valid @RequestBody TokenRequest body) {
    users.verifyEmail(body.token());
    return ResponseEntity.noContent().build();
  }

  @Operation(summary = "Send the verification email again")
  @PostMapping("/resend-verification")
  public ResponseEntity<Void> resend(
      @AuthenticationPrincipal AuthUser me, HttpServletRequest request) {
    if (me == null) throw new ApiException(HttpStatus.UNAUTHORIZED, "login_required", "Log in");
    limiter.check("resend", String.valueOf(me.id()), 3, Duration.ofHours(1));
    users.sendVerification(users.get(me.id()));
    return ResponseEntity.noContent().build();
  }

  @Operation(summary = "Email a password-reset link")
  @PostMapping("/forgot-password")
  public ResponseEntity<Void> forgot(
      @Valid @RequestBody ForgotPasswordRequest body, HttpServletRequest request) {
    limiter.check("forgot", ClientIp.of(request), 5, Duration.ofHours(1));
    users.forgotPassword(body.email());
    return ResponseEntity.noContent().build();
  }

  @Operation(summary = "Set a new password with the token from the email")
  @PostMapping("/reset-password")
  public ResponseEntity<Void> reset(@Valid @RequestBody ResetPasswordRequest body) {
    users.resetPassword(body.token(), body.password());
    return ResponseEntity.noContent().build();
  }

  private void startSession(
      Authentication auth, HttpServletRequest request, HttpServletResponse response) {
    request.getSession(true);
    request.changeSessionId(); // new id after login: a session id planted earlier becomes useless
    SecurityContext context = SecurityContextHolder.createEmptyContext();
    context.setAuthentication(auth);
    SecurityContextHolder.setContext(context);
    contextRepository.saveContext(context, request, response);
  }
}
