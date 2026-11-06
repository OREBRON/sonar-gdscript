package org.orebron.gdscript.checks;

import org.orebron.gdscript.lexer.Token;
import org.sonar.api.rule.RuleKey;

/** An issue on the whole file when line is 0, on a whole line when column is negative, otherwise on [column, endColumn) of that line. */
public record Violation(RuleKey rule, int line, int column, int endColumn, String message) {
  static Violation onLine(Check check, int line, String message) {
    return new Violation(check.ruleKey(), line, -1, -1, message);
  }

  /** On the token; on its whole first line when it spans lines, as a string or node path may (issue #12). */
  static Violation at(Check check, Token token, String message) {
    // endColumn belongs to endLine: paired with the start line it can make an empty, reversed or past-line-end range.
    if (token.endLine() != token.line()) return onLine(check, token.line(), message);
    return new Violation(check.ruleKey(), token.line(), token.column(), token.endColumn(), message);
  }
}
