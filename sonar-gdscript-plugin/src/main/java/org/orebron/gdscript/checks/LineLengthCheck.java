package org.orebron.gdscript.checks;

import java.util.ArrayList;
import java.util.List;
import org.sonar.api.batch.rule.ActiveRule;
import org.sonar.api.config.Configuration;

/** The text-only line-length rule: counts Unicode code points per line; comments and strings are not exempt. */
public final class LineLengthCheck implements Check {
  public static final String MAX_LINE_LENGTH = "sonar.gdscript.maxLineLength";
  public static final int DEFAULT_MAXIMUM = 120;

  @Override public String key() { return "line-length"; }
  @Override public String name() { return "Lines should not be too long"; }
  @Override public String severity() { return "MINOR"; }
  @Override public List<String> tags() { return List.of("convention"); }

  @Override public String htmlDescription() {
    return "<p>Keep lines within the configured sonar.gdscript.maxLineLength limit (default 120).</p>"
        + "<p>This text-only check counts Unicode code points, including whitespace; tabs count as one. "
        + "Comments and strings are not exempt. No syntax or security analysis is performed.</p>";
  }

  /** The limit is a project setting, kept for compatibility, rather than a rule parameter. */
  @Override public int configuredLimit(ActiveRule rule, Configuration configuration) {
    return Limit.parse(MAX_LINE_LENGTH, configuration.get(MAX_LINE_LENGTH).orElse(null), DEFAULT_MAXIMUM);
  }

  @Override public List<Violation> check(SourceFile file, int maximum) {
    var violations = new ArrayList<Violation>();
    List<String> lines = file.content().lines().toList();
    for (int i = 0; i < lines.size(); i++) {
      int length = lines.get(i).codePointCount(0, lines.get(i).length());
      if (length > maximum) {
        violations.add(Violation.onLine(this, i + 1, "Shorten this line from " + length + " to at most " + maximum + " characters."));
      }
    }
    return violations;
  }
}
