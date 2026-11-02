package org.orebron.gdscript.lexer;

/**
 * A token with a 1-based line range and 0-based UTF-16 columns (end exclusive). {@code lineIndent} is the
 * indentation of the token's line; on a NEWLINE, {@code nextIndent} is the indentation of the next code line.
 */
public record Token(TokenKind kind, String text, int offset, int line, int column, int endLine, int endColumn,
    int lineIndent, int nextIndent) {
  public int endOffset() { return offset + text.length(); }

  Token withNextIndent(int indent) {
    return new Token(kind, text, offset, line, column, endLine, endColumn, lineIndent, indent);
  }
}
