package com.gambyte.common;

import org.springframework.http.HttpStatus;

/**
 * An error that should reach the client as a clean JSON response with a specific HTTP status and a
 * machine-readable {@code code} (e.g. {@code "username_taken"}) the frontend can react to.
 *
 * <p>Services throw these; {@link GlobalExceptionHandler} turns them into responses. Use the static
 * factories so call sites read naturally: {@code throw ApiException.notFound("game")}.
 */
public class ApiException extends RuntimeException {

  private final HttpStatus status;
  private final String code;

  public ApiException(HttpStatus status, String code, String message) {
    super(message);
    this.status = status;
    this.code = code;
  }

  public HttpStatus status() {
    return status;
  }

  public String code() {
    return code;
  }

  public static ApiException notFound(String what) {
    return new ApiException(HttpStatus.NOT_FOUND, what + "_not_found", what + " not found");
  }

  public static ApiException badRequest(String code, String message) {
    return new ApiException(HttpStatus.BAD_REQUEST, code, message);
  }

  public static ApiException conflict(String code, String message) {
    return new ApiException(HttpStatus.CONFLICT, code, message);
  }

  public static ApiException forbidden(String code, String message) {
    return new ApiException(HttpStatus.FORBIDDEN, code, message);
  }

  public static ApiException tooManyRequests(String message) {
    return new ApiException(HttpStatus.TOO_MANY_REQUESTS, "rate_limited", message);
  }

  /** A paid feature was used by a free account (HTTP 402 Payment Required). */
  public static ApiException premiumRequired(String message) {
    return new ApiException(HttpStatus.PAYMENT_REQUIRED, "premium_required", message);
  }
}
