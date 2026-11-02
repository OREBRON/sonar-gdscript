package org.orebron.gdscript.lexer;

/** Source the lexer cannot tokenize, located by a 1-based line and a 0-based UTF-16 column. */
public final class LexException extends Exception {
  private static final long serialVersionUID = 1L;
  private final int line;
  private final int column;

  LexException(String message, int line, int column) {
    super(message + " at line " + line + ", column " + (column + 1));
    this.line = line;
    this.column = column;
  }

  public int line() { return line; }
  public int column() { return column; }
}
