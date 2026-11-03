package org.orebron.gdscript.checks;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.orebron.gdscript.lexer.Token;
import org.orebron.gdscript.parser.SyntaxKind;
import org.orebron.gdscript.parser.SyntaxNode;

/**
 * The first statement of a block after the block has returned, as Godot's parser decides it: a return statement, an
 * if whose every branch including an else returns, or a match with a catch-all branch whose every branch returns.
 * Accessor bodies are left out, as in Godot 4.7.
 */
public final class UnreachableCodeCheck extends GodotWarningCheck {
  private static final Set<SyntaxKind> STATEMENT_BLOCKS = EnumSet.of(SyntaxKind.FUNCTION, SyntaxKind.LAMBDA, SyntaxKind.IF,
      SyntaxKind.ELIF, SyntaxKind.ELSE, SyntaxKind.FOR, SyntaxKind.WHILE, SyntaxKind.MATCH_BRANCH, SyntaxKind.CATCH_ALL_BRANCH);

  @Override public String key() { return "unreachable-code"; }
  @Override public String name() { return "All code should be reachable"; }
  @Override public String severity() { return "MAJOR"; }
  @Override String warning() { return "unreachable_code"; }

  @Override public String htmlDescription() {
    return "<p>Statements after a <code>return</code>, or after an <code>if</code>/<code>else</code> or a "
        + "<code>match</code> whose every branch returns, never run. Remove them, or fix the control flow that was "
        + "meant to reach them.</p>" + godotNote(warning());
  }

  @Override List<Violation> find(SourceFile file) {
    if (file.tree() == null) return List.of();
    var scopes = new Scopes(file);
    var violations = new ArrayList<Violation>();
    file.tree().descendantsAndSelf().filter(block -> STATEMENT_BLOCKS.contains(block.kind()))
        .filter(block -> {
          SyntaxNode function = scopes.functionOf(block);
          return function != null && function.kind() != SyntaxKind.ACCESSOR;
        })
        .forEach(block -> {
          boolean returned = false;
          for (int start : scopes.statements(block)) {
            if (returned) {
              violations.add(Violation.at(this, scopes.tokens.get(start), "Remove this unreachable code; the block has already returned."));
              break;
            }
            returned = returns(scopes, block, start);
          }
        });
    return violations;
  }

  private static boolean blockReturns(Scopes scopes, SyntaxNode block) {
    return scopes.statements(block).stream().anyMatch(start -> returns(scopes, block, start));
  }

  private static boolean returns(Scopes scopes, SyntaxNode block, int start) {
    Token first = scopes.tokens.get(start);
    if (TokenRanges.is(first, "return")) return true;
    List<SyntaxNode> children = block.children();
    for (int i = 0; i < children.size(); i++) {
      SyntaxNode child = children.get(i);
      if (!child.token().equals(first)) continue;
      if (child.kind() == SyntaxKind.IF) return ifReturns(scopes, children, i);
      if (child.kind() == SyntaxKind.MATCH) return matchReturns(scopes, child);
    }
    return false;
  }

  private static boolean ifReturns(Scopes scopes, List<SyntaxNode> siblings, int index) {
    boolean all = blockReturns(scopes, siblings.get(index));
    for (int i = index + 1; i < siblings.size(); i++) {
      SyntaxNode branch = siblings.get(i);
      if (branch.kind() == SyntaxKind.ELIF) all &= blockReturns(scopes, branch);
      else if (branch.kind() == SyntaxKind.ELSE) return all && blockReturns(scopes, branch);
      else break;
    }
    return false;
  }

  private static boolean matchReturns(Scopes scopes, SyntaxNode match) {
    var branches = match.children().stream()
        .filter(branch -> branch.kind() == SyntaxKind.MATCH_BRANCH || branch.kind() == SyntaxKind.CATCH_ALL_BRANCH).toList();
    return branches.stream().anyMatch(branch -> branch.kind() == SyntaxKind.CATCH_ALL_BRANCH)
        && branches.stream().allMatch(branch -> blockReturns(scopes, branch));
  }
}
