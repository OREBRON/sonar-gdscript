package org.orebron.gdscript.checks;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.orebron.gdscript.parser.SyntaxKind;
import org.orebron.gdscript.parser.SyntaxNode;

/**
 * Control flow nested deeper than the limit. if, elif, else, for, while and match branches each add a level;
 * the rule reports the outermost if, for or while beyond the limit, and every function body starts again at 0.
 */
public final class NestingDepthCheck implements Check {
  private static final Set<SyntaxKind> LEVELS = EnumSet.of(SyntaxKind.IF, SyntaxKind.ELIF, SyntaxKind.ELSE,
      SyntaxKind.FOR, SyntaxKind.WHILE, SyntaxKind.MATCH_BRANCH, SyntaxKind.CATCH_ALL_BRANCH);
  private static final Set<SyntaxKind> REPORTED = EnumSet.of(SyntaxKind.IF, SyntaxKind.FOR, SyntaxKind.WHILE);
  private static final Set<SyntaxKind> SCOPES = EnumSet.of(SyntaxKind.CLASS, SyntaxKind.FUNCTION, SyntaxKind.ACCESSOR, SyntaxKind.LAMBDA);

  @Override public String key() { return "nesting-depth"; }
  @Override public String name() { return "Control flow statements should not be nested too deeply"; }
  @Override public String severity() { return "MAJOR"; }
  @Override public List<String> tags() { return List.of("brain-overload"); }
  @Override public Optional<Limit> limit() { return Optional.of(new Limit("max", 3, "Maximum nesting of if, for, while and match")); }

  @Override public String htmlDescription() {
    return "<p>Nested <code>if</code>, <code>elif</code>, <code>else</code>, <code>for</code>, <code>while</code> and "
        + "<code>match</code> statements should not go deeper than the limit (default 3). Deep nesting makes code "
        + "hard to follow; return early or extract functions instead.</p>";
  }

  @Override public List<Violation> check(SourceFile file, int maximum) {
    var violations = new ArrayList<Violation>();
    if (file.tree() != null) visit(file.tree(), 0, maximum, violations);
    return violations;
  }

  private void visit(SyntaxNode node, int depth, int maximum, List<Violation> violations) {
    for (SyntaxNode child : node.children()) {
      int childDepth = SCOPES.contains(child.kind()) ? 0 : depth + (LEVELS.contains(child.kind()) ? 1 : 0);
      if (REPORTED.contains(child.kind()) && depth == maximum) {
        violations.add(Violation.at(this, child.token(), "Refactor this code to not nest more than " + maximum
            + " if/for/while/match statements."));
      }
      visit(child, childDepth, maximum, violations);
    }
  }
}
