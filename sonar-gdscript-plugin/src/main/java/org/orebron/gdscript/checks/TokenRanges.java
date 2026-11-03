package org.orebron.gdscript.checks;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import org.orebron.gdscript.lexer.Token;
import org.orebron.gdscript.lexer.TokenKind;
import org.orebron.gdscript.parser.SyntaxKind;
import org.orebron.gdscript.parser.SyntaxNode;

/** Token views of tree nodes, for checks that look at what a body contains. */
final class TokenRanges {
  private TokenRanges() {}

  /** Tokens are ordered by offset, and every node starts and ends at a token of the same file. */
  static int indexOf(List<Token> tokens, Token token) {
    int index = Collections.binarySearch(tokens, token, Comparator.comparingInt(Token::offset));
    if (index < 0) throw new IllegalArgumentException("Token outside the file: " + token);
    return index;
  }

  /** A node's tokens, from its first to its last token. */
  static List<Token> of(List<Token> tokens, SyntaxNode node) {
    return tokens.subList(indexOf(tokens, node.token()), indexOf(tokens, node.last()) + 1);
  }

  /**
   * A block's body: the tokens after its header's ':' up to its last token. The header ':' is the first one outside
   * brackets; in a for loop it comes after {@code in}, since the loop variable may have a type.
   */
  static List<Token> body(List<Token> tokens, SyntaxNode node) {
    int start = indexOf(tokens, node.token());
    int end = indexOf(tokens, node.last());
    boolean headerEnds = node.kind() != SyntaxKind.FOR;
    int depth = 0;
    for (int i = start; i <= end; i++) {
      Token token = tokens.get(i);
      if (depth == 0 && token.kind() == TokenKind.IDENTIFIER && token.text().equals("in")) headerEnds = true;
      if (token.kind() != TokenKind.OPERATOR) continue;
      switch (token.text()) {
        case "(", "[", "{" -> depth++;
        case ")", "]", "}" -> depth--;
        case ":" -> {
          if (depth == 0 && headerEnds) return tokens.subList(i + 1, end + 1);
        }
        default -> { }
      }
    }
    return List.of();
  }

  /** Without comments, continuations and line breaks. */
  static List<Token> significant(List<Token> tokens) {
    return tokens.stream().filter(TokenRanges::isSignificant).toList();
  }

  static boolean isSignificant(Token token) {
    return switch (token.kind()) {
      case COMMENT, CONTINUATION, NEWLINE, EOF -> false;
      default -> true;
    };
  }

  static boolean is(Token token, String text) {
    return (token.kind() == TokenKind.IDENTIFIER || token.kind() == TokenKind.OPERATOR) && token.text().equals(text);
  }
}
