package org.orebron.gdscript.parser;

import java.util.List;
import java.util.stream.Stream;
import org.orebron.gdscript.lexer.Token;

/**
 * A kept construct, the token that identifies it (a name or keyword), its last significant token, and the kept
 * constructs nested in it.
 */
public record SyntaxNode(SyntaxKind kind, Token token, Token last, List<SyntaxNode> children) {
  public Stream<SyntaxNode> descendantsAndSelf() {
    return Stream.concat(Stream.of(this), children.stream().flatMap(SyntaxNode::descendantsAndSelf));
  }
}
