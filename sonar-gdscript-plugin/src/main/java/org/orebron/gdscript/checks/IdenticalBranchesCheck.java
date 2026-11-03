package org.orebron.gdscript.checks;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.orebron.gdscript.lexer.Token;
import org.orebron.gdscript.parser.SyntaxKind;
import org.orebron.gdscript.parser.SyntaxNode;

/**
 * Two branches of one if/elif/else chain or one match with the same body. Bodies on a single line are left out, as
 * SonarSource does for other languages: short branches are often the same on purpose.
 */
public final class IdenticalBranchesCheck implements Check {
  private static final Set<SyntaxKind> MATCH_BRANCHES = EnumSet.of(SyntaxKind.MATCH_BRANCH, SyntaxKind.CATCH_ALL_BRANCH);

  @Override public String key() { return "identical-branches"; }
  @Override public String name() { return "Branches should not have the same implementation"; }
  @Override public String severity() { return "MAJOR"; }
  @Override public List<String> tags() { return List.of("design", "suspicious"); }

  @Override public String htmlDescription() {
    return "<p>Two branches of an <code>if</code>/<code>elif</code>/<code>else</code> chain or of a "
        + "<code>match</code> that do the same thing are duplicate code at best and a copy-paste mistake at worst. "
        + "Merge the conditions or patterns, or fix the branch that was meant to differ. Single-line bodies are "
        + "not reported.</p>";
  }

  @Override public List<Violation> check(SourceFile file, int unused) {
    var violations = new ArrayList<Violation>();
    if (file.tree() == null) return violations;
    file.tree().descendantsAndSelf().forEach(parent -> {
      List<SyntaxNode> children = parent.children();
      if (parent.kind() == SyntaxKind.MATCH) {
        compare(file, children.stream().filter(child -> MATCH_BRANCHES.contains(child.kind())).toList(), violations);
      }
      for (int i = 0; i < children.size(); i++) {
        if (children.get(i).kind() != SyntaxKind.IF) continue;
        int end = i + 1;
        while (end < children.size() && (children.get(end).kind() == SyntaxKind.ELIF || children.get(end).kind() == SyntaxKind.ELSE)) end++;
        compare(file, children.subList(i, end), violations);
      }
    });
    return violations;
  }

  private void compare(SourceFile file, List<SyntaxNode> branches, List<Violation> violations) {
    var bodies = branches.stream().map(branch -> shape(file.tokens(), branch)).toList();
    for (int j = 1; j < branches.size(); j++) {
      if (bodies.get(j) == null) continue;
      for (int i = 0; i < j; i++) {
        if (bodies.get(j).equals(bodies.get(i))) {
          violations.add(Violation.at(this, branches.get(j).token(), "This branch's code block is the same as the block for the branch on line "
              + branches.get(i).token().line() + "."));
          break;
        }
      }
    }
  }

  /** The body's tokens with line breaks and relative indentation; null for a single-line body. */
  private static String shape(List<Token> tokens, SyntaxNode branch) {
    List<Token> body = TokenRanges.significant(TokenRanges.body(tokens, branch));
    if (body.isEmpty() || body.get(0).line() == body.get(body.size() - 1).endLine()) return null;
    int base = body.get(0).lineIndent();
    var shape = new StringBuilder();
    int line = -1;
    for (Token token : body) {
      if (token.line() != line) shape.append('\n').append(token.lineIndent() - base).append(':');
      shape.append(token.text()).append(' ');
      line = token.endLine();
    }
    return shape.toString();
  }
}
