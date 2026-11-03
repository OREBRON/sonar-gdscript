package org.orebron.gdscript.checks;

import java.util.ArrayList;
import java.util.List;
import org.orebron.gdscript.lexer.Token;

/** {@code == true}, {@code != false} and the like, where the boolean literal adds nothing. */
public final class BooleanLiteralCheck implements Check {
  @Override public String key() { return "boolean-literal-comparison"; }
  @Override public String name() { return "Boolean literals should not be compared with ==/!="; }
  @Override public String severity() { return "MINOR"; }
  @Override public List<String> tags() { return List.of("clumsy"); }

  @Override public String htmlDescription() {
    return "<p>Comparing with <code>true</code> or <code>false</code> makes a condition longer without changing it: "
        + "write <code>if done:</code> instead of <code>if done == true:</code> and <code>if not done:</code> "
        + "instead of <code>if done == false:</code>. Keep the comparison only when the value may be something other "
        + "than a <code>bool</code> and the difference matters; comparisons with the result of a <code>get</code>* "
        + "call such as <code>Dictionary.get()</code> or <code>ConfigFile.get_value()</code> are not reported.</p>";
  }

  @Override public List<Violation> check(SourceFile file, int unused) {
    List<Token> tokens = TokenRanges.significant(file.tokens());
    var violations = new ArrayList<Violation>();
    for (int i = 1; i + 1 < tokens.size(); i++) {
      if (!TokenRanges.is(tokens.get(i), "==") && !TokenRanges.is(tokens.get(i), "!=")) continue;
      Token literal = isBoolean(tokens.get(i + 1)) ? tokens.get(i + 1) : isBoolean(tokens.get(i - 1)) ? tokens.get(i - 1) : null;
      if (literal != null && !comparesAGetter(tokens, i)) violations.add(Violation.at(this, literal, "Remove the unnecessary boolean literal."));
    }
    return violations;
  }

  /**
   * {@code dict.get("key", false) == true} and {@code config.get_value(...) == true} compare a Variant that may not be
   * a bool, where the comparison is deliberate: the left operand ends with a call to a function named get*.
   */
  private static boolean comparesAGetter(List<Token> tokens, int operator) {
    if (!TokenRanges.is(tokens.get(operator - 1), ")")) return false;
    int depth = 0;
    for (int i = operator - 1; i > 0; i--) {
      if (TokenRanges.is(tokens.get(i), ")")) depth++;
      else if (TokenRanges.is(tokens.get(i), "(") && --depth == 0) return tokens.get(i - 1).text().startsWith("get");
    }
    return false;
  }

  private static boolean isBoolean(Token token) { return TokenRanges.is(token, "true") || TokenRanges.is(token, "false"); }
}
