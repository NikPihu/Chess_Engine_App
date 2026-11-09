package com.gambyte.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gambyte.game.api.GameDtos.GameView;
import com.gambyte.game.domain.GameStatus;
import com.gambyte.game.live.LiveGame;
import com.gambyte.game.service.GameService;
import com.gambyte.user.domain.Perf;
import com.gambyte.user.domain.User;
import com.gambyte.user.service.UserService;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import phase1.FenParser;

/**
 * Runs the real application against real PostgreSQL and Redis (started in Docker by
 * Testcontainers). Skipped automatically when Docker is not available.
 *
 * <p>The engine path points nowhere on purpose, so the Java fallback engine is used: the test does
 * not depend on the C++ build.
 */
@SpringBootTest(properties = {"gambyte.engine.path=does-not-exist", "ANTHROPIC_API_KEY="})
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class GambyteIntegrationTest {

  @Container @ServiceConnection
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

  @Container
  @ServiceConnection(name = "redis")
  static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

  @Autowired MockMvc mvc;
  @Autowired UserService users;
  @Autowired GameService games;

  @Test
  void registerLoginAndCsrf() throws Exception {
    String body = """
        {"username":"itest1","email":"itest1@example.test","password":"password-123"}""";
    // Without the CSRF token the request is refused.
    mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isForbidden());
    mvc.perform(post("/api/auth/register").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.username").value("itest1"));
    // Same name, different case: taken.
    mvc.perform(post("/api/auth/register").with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content(body.replace("itest1@", "other@").replace("\"itest1\"", "\"ITEST1\"")))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("username_taken"));
    mvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content("{\"login\":\"itest1\",\"password\":\"wrong-password\"}"))
        .andExpect(status().isUnauthorized());
    mvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content("{\"login\":\"ITEST1@example.test\",\"password\":\"password-123\"}"))
        .andExpect(status().isOk());
  }

  @Test
  void aGameAgainstTheEngineIsPlayedAndSaved() {
    User u = users.register("itest2", "itest2@example.test", "password-123");
    GameView g = games.createComputerGame(u.getId(), 1, "white", 0, 0, null);
    games.move(g.id(), u.getId(), "e2e4", 0);
    // The engine answers on a background thread.
    await().atMost(Duration.ofSeconds(20)).until(() -> games.view(g.id()).moves().size() == 2);
    games.resign(g.id(), u.getId());
    GameView done = games.view(g.id());
    assertThat(done.status()).isEqualTo("RESIGN");
    assertThat(done.winner()).isEqualTo("black");
    assertThat(games.record(g.id()).getStatus()).isEqualTo(GameStatus.RESIGN);
    assertThat(games.pgn(g.id())).contains("[Result \"0-1\"]").contains("1. e4");
  }

  @Test
  void aRatedGameMovesBothRatings() {
    User a = users.register("itest3", "itest3@example.test", "password-123");
    User b = users.register("itest4", "itest4@example.test", "password-123");
    LiveGame g = games.start(a.getId(), b.getId(), 180, 0, true, "lobby", null, FenParser.START_POSITION, null);
    games.move(g.id(), a.getId(), "f2f3", 0);
    games.move(g.id(), b.getId(), "e7e5", 0);
    games.move(g.id(), a.getId(), "g2g4", 0);
    games.move(g.id(), b.getId(), "d8h4", 0); // mate
    assertThat(games.record(g.id()).getStatus()).isEqualTo(GameStatus.MATE);
    assertThat(users.ratingFor(a.getId(), Perf.BLITZ).getRating()).isLessThan(1500);
    assertThat(users.ratingFor(b.getId(), Perf.BLITZ).getRating()).isGreaterThan(1500);
  }
}
