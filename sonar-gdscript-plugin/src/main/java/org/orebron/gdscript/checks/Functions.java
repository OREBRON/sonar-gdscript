package org.orebron.gdscript.checks;

import java.util.stream.Stream;
import org.orebron.gdscript.parser.SyntaxKind;
import org.orebron.gdscript.parser.SyntaxNode;

/** Named callables with a body (functions and inline accessors), as the function rules see them. */
final class Functions {
  private Functions() {}

  /** Empty when the file could not be parsed. */
  static Stream<SyntaxNode> withBodies(SourceFile file) {
    if (file.tree() == null) return Stream.empty();
    return file.tree().descendantsAndSelf().filter(node -> node.kind() == SyntaxKind.FUNCTION || node.kind() == SyntaxKind.ACCESSOR);
  }
}
