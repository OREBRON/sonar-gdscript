package org.orebron.gdscript.parser;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.function.Supplier;
import org.orebron.gdscript.lexer.Token;

/** Builds the immutable tree bottom-up: a node exists once it is closed, ending at the last consumed token. */
final class TreeBuilder {
  private record Open(SyntaxKind kind, Token token, List<SyntaxNode> children) {}

  private final Deque<Open> open = new ArrayDeque<>();
  private final Supplier<Token> lastConsumed;

  TreeBuilder(Supplier<Token> lastConsumed) { this.lastConsumed = lastConsumed; }

  void open(SyntaxKind kind, Token token) { open.push(new Open(kind, token, new ArrayList<>())); }

  SyntaxNode close() { return close(lastConsumed.get()); }

  void leaf(SyntaxKind kind, Token token) {
    open(kind, token);
    close(token);
  }

  private SyntaxNode close(Token last) {
    Open node = open.pop();
    var built = new SyntaxNode(node.kind(), node.token(), last, List.copyOf(node.children()));
    if (!open.isEmpty()) open.peek().children().add(built);
    return built;
  }
}
