package org.orebron.gdscript.metrics;

import java.util.EnumSet;
import java.util.Set;
import org.orebron.gdscript.parser.SyntaxKind;
import org.orebron.gdscript.parser.SyntaxNode;

/**
 * classes, functions and cyclomatic complexity as defined in docs/metrics-plan.md. The file complexity is the sum
 * over all bodies, so it is a count of node kinds: +1 per function-like body and per decision point.
 */
public record StructureMetrics(int classes, int functions, int complexity) {
  private static final Set<SyntaxKind> CLASSES = EnumSet.of(SyntaxKind.SCRIPT, SyntaxKind.CLASS);
  private static final Set<SyntaxKind> FUNCTIONS = EnumSet.of(SyntaxKind.FUNCTION, SyntaxKind.ACCESSOR);
  private static final Set<SyntaxKind> COMPLEXITY = EnumSet.of(SyntaxKind.FUNCTION, SyntaxKind.ACCESSOR, SyntaxKind.LAMBDA,
      SyntaxKind.IF, SyntaxKind.ELIF, SyntaxKind.WHILE, SyntaxKind.FOR, SyntaxKind.CONDITIONAL_EXPRESSION,
      SyntaxKind.MATCH_BRANCH, SyntaxKind.LOGICAL_OPERATOR);

  public static StructureMetrics of(SyntaxNode script) {
    return new StructureMetrics(count(script, CLASSES), count(script, FUNCTIONS), count(script, COMPLEXITY));
  }

  private static int count(SyntaxNode root, Set<SyntaxKind> kinds) {
    return (int) root.descendantsAndSelf().filter(node -> kinds.contains(node.kind())).count();
  }
}
