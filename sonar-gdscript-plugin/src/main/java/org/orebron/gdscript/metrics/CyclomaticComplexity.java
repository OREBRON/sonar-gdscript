package org.orebron.gdscript.metrics;

import java.util.EnumSet;
import java.util.Set;
import org.orebron.gdscript.parser.SyntaxKind;
import org.orebron.gdscript.parser.SyntaxNode;

/** Cyclomatic complexity as defined in docs/metrics-plan.md, shared by the metric and the function-complexity rule. */
public final class CyclomaticComplexity {
  /** Function-like bodies: each adds 1 and owns the decisions inside it. */
  private static final Set<SyntaxKind> BODIES = EnumSet.of(SyntaxKind.FUNCTION, SyntaxKind.ACCESSOR, SyntaxKind.LAMBDA);
  private static final Set<SyntaxKind> DECISIONS = EnumSet.of(SyntaxKind.IF, SyntaxKind.ELIF, SyntaxKind.WHILE,
      SyntaxKind.FOR, SyntaxKind.CONDITIONAL_EXPRESSION, SyntaxKind.MATCH_BRANCH, SyntaxKind.LOGICAL_OPERATOR);

  private CyclomaticComplexity() {}

  /** The file measure: every body and every decision, each counted once. */
  public static int ofFile(SyntaxNode script) {
    return (int) script.descendantsAndSelf().filter(node -> BODIES.contains(node.kind()) || DECISIONS.contains(node.kind())).count();
  }

  /** 1 plus the decisions this body owns; nested lambdas, functions and classes own theirs. */
  public static int ofBody(SyntaxNode body) { return 1 + ownedDecisions(body); }

  private static int ownedDecisions(SyntaxNode node) {
    int count = 0;
    for (SyntaxNode child : node.children()) {
      if (BODIES.contains(child.kind()) || child.kind() == SyntaxKind.CLASS) continue;
      if (DECISIONS.contains(child.kind())) count++;
      count += ownedDecisions(child);
    }
    return count;
  }
}
