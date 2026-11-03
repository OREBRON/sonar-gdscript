package org.orebron.gdscript.metrics;

import java.util.EnumSet;
import java.util.Set;
import org.orebron.gdscript.parser.SyntaxKind;
import org.orebron.gdscript.parser.SyntaxNode;

/** classes, functions and cyclomatic complexity as defined in docs/metrics-plan.md. */
public record StructureMetrics(int classes, int functions, int complexity) {
  private static final Set<SyntaxKind> CLASSES = EnumSet.of(SyntaxKind.SCRIPT, SyntaxKind.CLASS);
  private static final Set<SyntaxKind> FUNCTIONS = EnumSet.of(SyntaxKind.FUNCTION, SyntaxKind.ACCESSOR);

  public static StructureMetrics of(SyntaxNode script) {
    return new StructureMetrics(count(script, CLASSES), count(script, FUNCTIONS), CyclomaticComplexity.ofFile(script));
  }

  private static int count(SyntaxNode root, Set<SyntaxKind> kinds) {
    return (int) root.descendantsAndSelf().filter(node -> kinds.contains(node.kind())).count();
  }
}
