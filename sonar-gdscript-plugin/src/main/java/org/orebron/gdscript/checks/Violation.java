package org.orebron.gdscript.checks;

import org.orebron.gdscript.lexer.Token;
import org.sonar.api.rule.RuleKey;

/** An issue on a whole line when column is negative, otherwise on [column, endColumn) of that line. */
public record Violation(RuleKey rule, int line, int column, int endColumn, String message) {
  static Violation onLine(Check check, int line, String message) {
    return new Violation(check.ruleKey(), line, -1, -1, message);
  }

  /** On a single-line token such as a name, keyword or comment. */
  static Violation at(Check check, Token token, String message) {
    return new Violation(check.ruleKey(), token.line(), token.column(), token.endColumn(), message);
  }
}
