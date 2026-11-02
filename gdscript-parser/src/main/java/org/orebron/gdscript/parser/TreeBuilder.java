package org.orebron.gdscript.parser;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import org.orebron.gdscript.lexer.Token;

/** Builds the immutable tree bottom-up: a node exists once it is closed. */
final class TreeBuilder {
  private record Open(SyntaxKind kind, Token token, List<SyntaxNode> children) {}

  private final Deque<Open> open = new ArrayDeque<>();

  void open(SyntaxKind kind, Token token) { open.push(new Open(kind, token, new ArrayList<>())); }

  SyntaxNode close() {
    Open node = open.pop();
    var built = new SyntaxNode(node.kind(), node.token(), List.copyOf(node.children()));
    if (!open.isEmpty()) open.peek().children().add(built);
    return built;
  }

  void leaf(SyntaxKind kind, Token token) {
    open(kind, token);
    close();
  }
}
