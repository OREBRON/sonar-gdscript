package org.orebron.gdscript.lexer;

/** Measures line indentation and enforces one indentation character per file. */
final class Indentation {
  static final int NOT_A_CODE_LINE = -1;
  private char indentChar;

  /** Skips leading tabs and spaces; returns their count, or NOT_A_CODE_LINE for blank and comment-only lines. */
  int read(Cursor cursor, boolean checkConsistency) throws LexException {
    int start = cursor.position();
    cursor.skipWhile(c -> c == ' ' || c == '\t');
    if (cursor.atEnd() || cursor.isLineBreak(0) || cursor.peek() == '#') return NOT_A_CODE_LINE;
    if (checkConsistency) check(cursor, start);
    return cursor.position() - start;
  }

  private void check(Cursor cursor, int start) throws LexException {
    for (int offset = start; offset < cursor.position(); offset++) {
      char c = cursor.charAt(offset);
      if (indentChar == 0) indentChar = c;
      else if (c != indentChar) throw new LexException("Mixed tabs and spaces in indentation", cursor.line(), cursor.columnOf(offset));
    }
  }
}
