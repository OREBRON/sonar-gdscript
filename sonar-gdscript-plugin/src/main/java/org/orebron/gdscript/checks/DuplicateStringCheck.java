package org.orebron.gdscript.checks;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.orebron.gdscript.lexer.Token;
import org.orebron.gdscript.lexer.TokenKind;

/**
 * The same string literal written at least {@code threshold} times in a file. StringName ({@code &"..."}) and NodePath
 * ({@code ^"..."}) literals name things and are left out, as are strings shorter than five characters. The message
 * gives the count, not the literal; the issue location highlights it.
 */
public final class DuplicateStringCheck implements Check {
  private static final int MINIMUM_LENGTH = 5;

  @Override public String key() { return "duplicate-string"; }
  @Override public String name() { return "String literals should not be duplicated"; }
  @Override public String severity() { return "CRITICAL"; }
  @Override public List<String> tags() { return List.of("design"); }
  @Override public Optional<Limit> limit() { return Optional.of(new Limit("threshold", 3, "Number of times a literal must be duplicated to trigger an issue")); }

  @Override public String htmlDescription() {
    return "<p>A string literal repeated several times in a file, such as a dictionary key or a resource path, has "
        + "to be changed in every place at once. Define a constant and use it instead. Literals shorter than five "
        + "characters, StringName and NodePath literals are not reported.</p>";
  }

  @Override public List<Violation> check(SourceFile file, int threshold) {
    var occurrences = new LinkedHashMap<String, List<Token>>();
    for (Token token : file.tokens()) {
      String value = value(token);
      if (value != null && value.length() >= MINIMUM_LENGTH) occurrences.computeIfAbsent(value, v -> new ArrayList<>()).add(token);
    }
    var violations = new ArrayList<Violation>();
    for (Map.Entry<String, List<Token>> entry : occurrences.entrySet()) {
      int count = entry.getValue().size();
      if (count < threshold) continue;
      Token first = entry.getValue().get(0);
      // Never the literal itself: it may be a secret, and messages reach people who cannot read the source.
      String message = "Define a constant instead of duplicating this literal " + count + " times.";
      violations.add(first.line() == first.endLine() ? Violation.at(this, first, message) : Violation.onLine(this, first.line(), message));
    }
    return violations;
  }

  /** The text between the quotes of a regular or raw string; null for other tokens. */
  private static String value(Token token) {
    if (token.kind() != TokenKind.STRING) return null;
    String text = token.text().startsWith("r") ? token.text().substring(1) : token.text();
    if (text.startsWith("&") || text.startsWith("^")) return null;
    int quote = text.startsWith("\"\"\"") || text.startsWith("'''") ? 3 : 1;
    return text.substring(quote, text.length() - quote);
  }
}
