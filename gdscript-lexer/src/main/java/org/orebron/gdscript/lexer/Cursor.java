package org.orebron.gdscript.lexer;

import java.util.function.IntPredicate;

/** Position, line and column bookkeeping; the only place that knows the line-break rules (CRLF, LF, lone CR). */
final class Cursor {
  private static final char NONE = '\0';
  private final String source;
  private int position;
  private int line = 1;
  private int lineStart;

  Cursor(String source) {
    this.source = source;
    if (source.startsWith("﻿")) position = lineStart = 1;
  }

  boolean atEnd() { return position >= source.length(); }
  char peek() { return peek(0); }

  char peek(int ahead) {
    int index = position + ahead;
    return index < source.length() ? source.charAt(index) : NONE;
  }

  int codePoint() { return codePoint(0); }

  int codePoint(int ahead) {
    int index = position + ahead;
    return index < source.length() ? source.codePointAt(index) : -1;
  }

  char charAt(int offset) { return source.charAt(offset); }
  boolean startsWith(String text) { return source.startsWith(text, position); }
  boolean startsWithIgnoreCase(String text) { return source.regionMatches(true, position, text, 0, text.length()); }

  boolean isLineBreak(int ahead) {
    char c = peek(ahead);
    return c == '\n' || c == '\r';
  }

  int lineBreakLength() { return peek() == '\r' && peek(1) == '\n' ? 2 : 1; }

  int restOfLineLength() {
    int end = position;
    while (end < source.length() && source.charAt(end) != '\n' && source.charAt(end) != '\r') end++;
    return end - position;
  }

  void advance(int count) { position += count; }
  void advanceCodePoint() { position += Character.charCount(source.codePointAt(position)); }
  void skipWhile(IntPredicate predicate) { while (!atEnd() && predicate.test(peek())) position++; }

  void newline() {
    position += lineBreakLength();
    line++;
    lineStart = position;
  }

  int position() { return position; }
  int line() { return line; }
  int column() { return columnOf(position); }
  int columnOf(int offset) { return offset - lineStart; }
  String text(int start, int end) { return source.substring(start, end); }
  LexException error(String message) { return new LexException(message, line, column()); }
}
