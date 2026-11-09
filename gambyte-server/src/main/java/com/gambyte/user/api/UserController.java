package com.gambyte.user.api;

import com.gambyte.auth.AuthUser;
import com.gambyte.bot.ApiTokenService;
import com.gambyte.bot.ApiTokenService.CreatedToken;
import com.gambyte.bot.ApiTokenService.TokenView;
import com.gambyte.common.ApiException;
import com.gambyte.user.api.UserDtos.ChangePasswordRequest;
import com.gambyte.user.api.UserDtos.Leaderboard;
import com.gambyte.user.api.UserDtos.Profile;
import com.gambyte.user.api.UserDtos.UpdateProfileRequest;
import com.gambyte.user.api.UserDtos.UserRef;
import com.gambyte.user.domain.Perf;
import com.gambyte.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Profiles, search, leaderboards, and the logged-in user's own settings and API tokens. */
@RestController
@Tag(name = "Users", description = "Profiles, leaderboards and account settings")
public class UserController {

  private final UserService users;
  private final ApiTokenService tokens;

  public UserController(UserService users, ApiTokenService tokens) {
    this.users = users;
    this.tokens = tokens;
  }

  @Operation(summary = "A player's public profile")
  @GetMapping("/api/users/{username}")
  public Profile profile(@PathVariable String username) {
    return users.profile(username);
  }

  @Operation(summary = "Find players by the start of their name (at least 2 letters)")
  @GetMapping("/api/users")
  public List<UserRef> search(@RequestParam("q") String prefix) {
    return users.search(prefix);
  }

  @Operation(summary = "Top 100 of a rating pool (bullet, blitz, rapid, classical, puzzle)")
  @GetMapping("/api/leaderboard/{perf}")
  public Leaderboard leaderboard(@PathVariable String perf) {
    Perf p;
    try {
      p = Perf.fromKey(perf);
    } catch (IllegalArgumentException e) {
      throw ApiException.notFound("perf");
    }
    return new Leaderboard(p.key(), users.leaderboard(p));
  }

  // ------------------------------------------------------------------ own account

  @Operation(summary = "Update your bio and country")
  @PutMapping("/api/me/profile")
  public ResponseEntity<Void> updateProfile(
      @AuthenticationPrincipal AuthUser me, @Valid @RequestBody UpdateProfileRequest body) {
    users.updateProfile(me.id(), body.bio(), body.country());
    return ResponseEntity.noContent().build();
  }

  @Operation(summary = "Save UI preferences (any small JSON object)")
  @PutMapping("/api/me/settings")
  public Map<String, Object> updateSettings(
      @AuthenticationPrincipal AuthUser me, @RequestBody Map<String, Object> settings) {
    return users.updateSettings(me.id(), settings);
  }

  @Operation(summary = "Change your password")
  @PostMapping("/api/me/password")
  public ResponseEntity<Void> changePassword(
      @AuthenticationPrincipal AuthUser me, @Valid @RequestBody ChangePasswordRequest body) {
    users.changePassword(me.id(), body.currentPassword(), body.newPassword());
    return ResponseEntity.noContent().build();
  }

  // ------------------------------------------------------------------ API tokens (for bots)

  public record NewTokenRequest(@NotBlank @Size(max = 60) String name) {}

  @Operation(summary = "Your API tokens (the secret is never shown again)")
  @GetMapping("/api/me/tokens")
  public List<TokenView> listTokens(@AuthenticationPrincipal AuthUser me) {
    return tokens.list(me.id());
  }

  @Operation(summary = "Create an API token; copy it now, it is shown only once")
  @PostMapping("/api/me/tokens")
  public CreatedToken createToken(
      @AuthenticationPrincipal AuthUser me, @Valid @RequestBody NewTokenRequest body) {
    return tokens.create(me.id(), body.name());
  }

  @Operation(summary = "Revoke an API token")
  @DeleteMapping("/api/me/tokens/{id}")
  public ResponseEntity<Void> revokeToken(
      @AuthenticationPrincipal AuthUser me, @PathVariable long id) {
    tokens.revoke(me.id(), id);
    return ResponseEntity.noContent().build();
  }
}
