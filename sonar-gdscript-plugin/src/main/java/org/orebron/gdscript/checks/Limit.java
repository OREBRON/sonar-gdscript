package org.orebron.gdscript.checks;

/** A rule's positive integer parameter, such as a maximum. */
public record Limit(String key, int defaultValue, String description) {
  /** A missing value means the default; an invalid one fails the analysis instead of being ignored. */
  static int parse(String setting, String value, int defaultValue) {
    if (value == null || value.isBlank()) return defaultValue;
    int parsed;
    try {
      parsed = Integer.parseInt(value.trim());
    } catch (NumberFormatException e) {
      throw invalid(setting, e);
    }
    if (parsed <= 0) throw invalid(setting, null);
    return parsed;
  }

  private static IllegalArgumentException invalid(String setting, Throwable cause) {
    return new IllegalArgumentException(setting + " must be a positive integer", cause);
  }
}
