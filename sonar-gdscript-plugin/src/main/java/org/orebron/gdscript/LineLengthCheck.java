package org.orebron.gdscript;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;
import org.sonar.api.batch.sensor.SensorContext;
import org.sonar.api.rule.RuleKey;

/** The text-only line-length rule: counts Unicode code points per line; comments and strings are not exempt. */
final class LineLengthCheck {
  static final String MAX_LINE_LENGTH = "sonar.gdscript.maxLineLength";
  static final RuleKey RULE = RuleKey.of("gdscript", "line-length");
  static final int DEFAULT_MAXIMUM = 120;

  record Violation(int line, String message) {}

  private LineLengthCheck() {}

  /** The configured maximum, or empty when the rule is inactive; the setting is validated only for an active rule. */
  static OptionalInt maximum(SensorContext context) {
    if (context.activeRules().find(RULE) == null) return OptionalInt.empty();
    int maximum;
    try {
      maximum = Integer.parseInt(context.config().get(MAX_LINE_LENGTH).orElse(String.valueOf(DEFAULT_MAXIMUM)));
    } catch (NumberFormatException e) {
      throw invalidMaximum(e);
    }
    if (maximum <= 0) throw invalidMaximum(null);
    return OptionalInt.of(maximum);
  }

  static List<Violation> violations(String content, int maximum) {
    var violations = new ArrayList<Violation>();
    List<String> lines = content.lines().toList();
    for (int i = 0; i < lines.size(); i++) {
      int length = lines.get(i).codePointCount(0, lines.get(i).length());
      if (length > maximum) {
        violations.add(new Violation(i + 1, "Shorten this line from " + length + " to at most " + maximum + " characters."));
      }
    }
    return violations;
  }

  private static IllegalArgumentException invalidMaximum(Throwable cause) {
    return new IllegalArgumentException(MAX_LINE_LENGTH + " must be a positive integer", cause);
  }
}
