package org.orebron.gdscript.parser;

import org.orebron.gdscript.lexer.SourceException;

/** Tokens the parser cannot consume completely. */
public final class ParseException extends SourceException {
  private static final long serialVersionUID = 1L;

  ParseException(String message, int line, int column) { super(message, line, column); }
}
