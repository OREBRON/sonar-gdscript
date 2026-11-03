package org.orebron.gdscript.checks;

import java.util.ArrayList;
import java.util.List;
import org.orebron.gdscript.parser.SyntaxKind;
import org.orebron.gdscript.parser.SyntaxNode;

/** Match branches after an unguarded branch with a wildcard or bind pattern, which catches everything first. */
public final class UnreachablePatternCheck extends GodotWarningCheck {
  @Override public String key() { return "unreachable-pattern"; }
  @Override public String name() { return "Match branches should not follow a catch-all branch"; }
  @Override public String severity() { return "MAJOR"; }
  @Override String warning() { return "unreachable_pattern"; }

  @Override public String htmlDescription() {
    return "<p>A match branch with <code>_</code> or a bare <code>var name</code> pattern and no <code>when</code> "
        + "guard matches every value, so the branches after it never run. Move the catch-all branch last.</p>"
        + godotNote(warning());
  }

  @Override List<Violation> find(SourceFile file) {
    var violations = new ArrayList<Violation>();
    if (file.tree() == null) return violations;
    file.tree().descendantsAndSelf().filter(node -> node.kind() == SyntaxKind.MATCH).forEach(match -> {
      boolean caught = false;
      for (SyntaxNode branch : match.children()) {
        if (branch.kind() != SyntaxKind.MATCH_BRANCH && branch.kind() != SyntaxKind.CATCH_ALL_BRANCH) continue;
        if (caught) violations.add(Violation.at(this, branch.token(), "Remove this branch, or move the catch-all branch before it to the end."));
        caught |= branch.kind() == SyntaxKind.CATCH_ALL_BRANCH;
      }
    });
    return violations;
  }
}
