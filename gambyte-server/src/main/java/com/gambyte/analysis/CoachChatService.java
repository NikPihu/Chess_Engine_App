package com.gambyte.analysis;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.errors.AnthropicServiceException;
import com.anthropic.errors.RateLimitException;
import com.anthropic.models.beta.AnthropicBeta;
import com.anthropic.models.beta.messages.BetaContentBlock;
import com.anthropic.models.beta.messages.BetaFallbacksParam;
import com.anthropic.models.beta.messages.BetaMessage;
import com.anthropic.models.beta.messages.BetaOutputConfig;
import com.anthropic.models.beta.messages.BetaStopReason;
import com.anthropic.models.beta.messages.MessageCreateParams;
import com.gambyte.analysis.ReviewService.MoveReview;
import com.gambyte.analysis.ReviewService.Review;
import com.gambyte.common.ApiException;
import com.gambyte.config.GambyteProperties;
import com.gambyte.user.service.UserService;
import jakarta.annotation.PreDestroy;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * The optional AI coach ("Ask the coach"): answers a player's question about a reviewed game in
 * plain language, using Claude through the official Anthropic Java SDK.
 *
 * <p>It is switched off unless {@code ANTHROPIC_API_KEY} is set, and only premium accounts can use
 * it (every answer costs money). The model never invents analysis: the prompt hands it the
 * engine's verdicts from the Game Review (evaluations, best moves, the free coach's notes), and
 * the system prompt tells it to explain those facts, not to calculate on its own.
 *
 * <p>Model: {@code gambyte.coach.model} (default {@code claude-opus-5-5}, at low effort since the
 * answers are short; set {@code COACH_MODEL=claude-haiku-5-5} for a much cheaper coach). The
 * request opts into the server-side refusal fallback, so a declined request is retried on a
 * fallback model by the API instead of failing.
 */
@Service
public class CoachChatService {

  private static final Logger log = LoggerFactory.getLogger(CoachChatService.class);

  private static final String SYSTEM = """
      You are Gambyte's chess coach. A player asks about one of their games. You get the engine's \
      review of the game: per-move evaluations (centipawns from White's side), the engine's best \
      move and line, a label (best, excellent, good, inaccuracy, mistake, blunder) and short notes.

      Explain using only these facts; never invent variations or evaluations that are not given. \
      Speak to the player as "you", warmly and concretely, in at most 150 words. Prefer ideas a \
      club player can reuse (loose pieces, king safety, piece activity, pawn structure) over long \
      move lists. If the question is not about chess or this game, say briefly that you can only \
      help with their chess.""";

  private final GambyteProperties.Coach config;
  private final ReviewService reviews;
  private final UserService users;
  private final AnthropicClient client;

  public CoachChatService(GambyteProperties props, ReviewService reviews, UserService users) {
    this.config = props.coach();
    this.reviews = reviews;
    this.users = users;
    this.client = config.enabled()
        ? AnthropicOkHttpClient.builder().apiKey(config.anthropicApiKey()).build()
        : null;
  }

  public boolean enabled() {
    return client != null;
  }

  /**
   * Answers a question about a reviewed game, optionally focused on one move.
   *
   * @param ply the move the player is looking at (1-based), or null for the whole game
   */
  public String ask(long userId, String gameId, Integer ply, String question) {
    if (client == null) {
      throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "coach_off", "The AI coach is not enabled on this server");
    }
    if (!users.get(userId).isPremium()) {
      throw ApiException.premiumRequired("Ask the coach is a Premium feature");
    }
    var status = reviews.status(gameId);
    if (status == null || status.review() == null) {
      throw ApiException.badRequest("no_review", "Run a Game Review first");
    }

    MessageCreateParams params = MessageCreateParams.builder()
        .model(config.model())
        .maxTokens(config.maxTokens())
        .system(SYSTEM)
        .outputConfig(BetaOutputConfig.builder().effort(BetaOutputConfig.Effort.LOW).build())
        .addBeta(AnthropicBeta.SERVER_SIDE_FALLBACK_2026_07_01)
        .fallbacks(BetaFallbacksParam.ofDefault())
        .addUserMessage(facts(status.review(), ply) + "\n\nQuestion: " + question)
        .build();
    try {
      BetaMessage response = client.beta().messages().create(params);
      if (response.stopReason().map(BetaStopReason.REFUSAL::equals).orElse(false)) {
        return "Sorry, I can't help with that one. Ask me about the moves in this game.";
      }
      String text = response.content().stream()
          .flatMap(block -> block.text().stream())
          .map(t -> t.text())
          .collect(Collectors.joining("\n"))
          .trim();
      return text.isEmpty() ? "I don't have anything useful to add here." : text;
    } catch (RateLimitException e) {
      throw ApiException.tooManyRequests("The coach is busy, try again in a minute");
    } catch (AnthropicServiceException e) {
      log.warn("Coach request failed with HTTP {}: {}", e.statusCode(), e.getMessage());
      throw new ApiException(HttpStatus.BAD_GATEWAY, "coach_failed", "The coach could not answer right now");
    } catch (RuntimeException e) {
      log.warn("Coach request failed: {}", e.toString());
      throw new ApiException(HttpStatus.BAD_GATEWAY, "coach_failed", "The coach could not answer right now");
    }
  }

  /** The engine facts the model may use, as compact text. */
  private static String facts(Review r, Integer ply) {
    StringBuilder s = new StringBuilder();
    s.append("Accuracy: White ").append(r.white().accuracy()).append("%, Black ")
        .append(r.black().accuracy()).append("%.\nMoves (ply, side, move, label, eval after, best, note):\n");
    for (MoveReview m : r.moves()) {
      boolean focus = ply != null && Math.abs(m.ply() - ply) <= 3;
      boolean notable = m.comment() != null;
      if (ply != null && !focus) continue;
      if (ply == null && !notable && m.ply() > 2) continue; // whole-game question: mistakes only
      s.append(m.ply()).append(' ').append(m.color()).append(' ').append(m.san()).append(' ')
          .append(m.classification()).append(' ').append(m.evalAfter());
      if (m.bestSan() != null) s.append(" best=").append(m.bestSan()).append(" line=").append(String.join(" ", m.bestLine()));
      if (m.comment() != null) s.append(" note=\"").append(m.comment()).append('"');
      s.append('\n');
    }
    if (ply != null) s.append("The player is looking at ply ").append(ply).append(".\n");
    return s.toString();
  }

  @PreDestroy
  void close() {
    if (client != null) client.close();
  }

  /** Visible for the UI: whether a block is text (keeps the SDK types out of controllers). */
  static boolean isText(BetaContentBlock block) {
    return block.text().isPresent();
  }
}
