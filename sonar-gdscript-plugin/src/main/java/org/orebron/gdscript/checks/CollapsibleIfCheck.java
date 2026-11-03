package org.orebron.gdscript.checks;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.orebron.gdscript.lexer.Token;
import org.orebron.gdscript.parser.SyntaxKind;
import org.orebron.gdscript.parser.SyntaxNode;

/** An if without elif or else whose whole body is another if without elif or else. */
public final class CollapsibleIfCheck implements Check {
  @Override public String key() { return "collapsible-if"; }
  @Override public String name() { return "Mergeable if statements should be combined"; }
  @Override public String severity() { return "MAJOR"; }
  @Override public List<String> tags() { return List.of("clumsy"); }

  @Override public String htmlDescription() {
    return "<p>When an <code>if</code> has no <code>elif</code> or <code>else</code> and its only statement is "
        + "another such <code>if</code>, the two conditions can be joined with <code>and</code>, which removes a "
        + "level of nesting.</p>";
  }

  @Override public List<Violation> check(SourceFile file, int unused) {
    var violations = new ArrayList<Violation>();
    if (file.tree() == null) return violations;
    file.tree().descendantsAndSelf().forEach(parent -> {
      for (int i = 0; i < parent.children().size(); i++) {
        SyntaxNode outer = parent.children().get(i);
        if (isLoneIf(parent.children(), i)) innerIf(file.tokens(), outer)
            .ifPresent(inner -> violations.add(Violation.at(this, inner.token(), "Merge this if statement with the enclosing one.")));
      }
    });
    return violations;
  }

  /** The inner if when it is the outer if's first and last statement. */
  private static Optional<SyntaxNode> innerIf(List<Token> tokens, SyntaxNode outer) {
    List<Token> body = TokenRanges.significant(TokenRanges.body(tokens, outer));
    for (int i = 0; i < outer.children().size(); i++) {
      SyntaxNode inner = outer.children().get(i);
      if (isLoneIf(outer.children(), i) && !body.isEmpty() && body.get(0).equals(inner.token())
          && inner.last().equals(outer.last())) {
        return Optional.of(inner);
      }
    }
    return Optional.empty();
  }

  /** An if whose next sibling is not its own elif or else. */
  private static boolean isLoneIf(List<SyntaxNode> siblings, int index) {
    if (siblings.get(index).kind() != SyntaxKind.IF) return false;
    if (index + 1 == siblings.size()) return true;
    SyntaxKind next = siblings.get(index + 1).kind();
    return next != SyntaxKind.ELIF && next != SyntaxKind.ELSE;
  }
}
