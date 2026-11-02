package org.orebron.gdscript.lexer;

/** Source the lexer cannot tokenize. */
public final class LexException extends SourceException {
  private static final long serialVersionUID = 1L;

  LexException(String message, int line, int column) { super(message, line, column); }
}
