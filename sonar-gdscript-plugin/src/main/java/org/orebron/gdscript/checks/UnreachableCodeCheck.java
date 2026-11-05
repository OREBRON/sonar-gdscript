package org.orebron.gdscript.checks;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
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
    var scopes = file.scopes();
    var flow = new Flow(scopes);
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
            returned = flow.returns(block, start);
          }
        });
    return violations;
  }

  /**
   * Which blocks return. Each block's answer and its if and match children are found once, since every enclosing
   * block asks again and a block may have thousands of statements.
   */
  private static final class Flow {
    private final Scopes scopes;
    private final Map<SyntaxNode, Boolean> returns = new IdentityHashMap<>();
    private final Map<SyntaxNode, Map<Token, Integer>> branches = new IdentityHashMap<>();

    Flow(Scopes scopes) { this.scopes = scopes; }

    boolean blockReturns(SyntaxNode block) {
      Boolean known = returns.get(block);
      if (known == null) {
        known = scopes.statements(block).stream().anyMatch(start -> returns(block, start));
        returns.put(block, known);
      }
      return known;
    }

    boolean returns(SyntaxNode block, int start) {
      Token first = scopes.tokens.get(start);
      if (TokenRanges.is(first, "return")) return true;
      Integer index = branches.computeIfAbsent(block, Flow::branchesOf).get(first);
      if (index == null) return false;
      List<SyntaxNode> children = block.children();
      return children.get(index).kind() == SyntaxKind.IF ? ifReturns(children, index) : matchReturns(children.get(index));
    }

    /** The index of the first if or match child at each token. */
    private static Map<Token, Integer> branchesOf(SyntaxNode block) {
      var indexes = new HashMap<Token, Integer>();
      List<SyntaxNode> children = block.children();
      for (int i = 0; i < children.size(); i++) {
        SyntaxKind kind = children.get(i).kind();
        if (kind == SyntaxKind.IF || kind == SyntaxKind.MATCH) indexes.putIfAbsent(children.get(i).token(), i);
      }
      return indexes;
    }

    /** Without an else the if may fall through, so its branches are only looked at when the else is there. */
    private boolean ifReturns(List<SyntaxNode> siblings, int index) {
      int end = index + 1;
      while (end < siblings.size() && siblings.get(end).kind() == SyntaxKind.ELIF) end++;
      if (end == siblings.size() || siblings.get(end).kind() != SyntaxKind.ELSE) return false;
      for (int i = index; i <= end; i++) if (!blockReturns(siblings.get(i))) return false;
      return true;
    }

    private boolean matchReturns(SyntaxNode match) {
      var branches = match.children().stream()
          .filter(branch -> branch.kind() == SyntaxKind.MATCH_BRANCH || branch.kind() == SyntaxKind.CATCH_ALL_BRANCH).toList();
      return branches.stream().anyMatch(branch -> branch.kind() == SyntaxKind.CATCH_ALL_BRANCH)
          && branches.stream().allMatch(this::blockReturns);
    }
  }
}
