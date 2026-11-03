package org.orebron.gdscript.lexer;

import java.util.function.BooleanSupplier;
import java.util.function.IntPredicate;

/** Scans one non-trivia token at the cursor and reports its kind; it knows nothing about lines or blocks. */
final class TokenScanner {
  private final Cursor cursor;
  private final BooleanSupplier previousIsOperand;

  TokenScanner(Cursor cursor, BooleanSupplier previousIsOperand) {
    this.cursor = cursor;
    this.previousIsOperand = previousIsOperand;
  }

  TokenKind scan() throws LexException {
    if (startsString()) return string();
    if (startsNumber()) return number();
    if (Syntax.isIdentifierStart(cursor.codePoint())) return identifier();
    if (cursor.peek() == '@') return annotation();
    if (startsNodePath()) return nodePath();
    return operator();
  }

  private boolean startsString() {
    return Syntax.isQuote(cursor.peek()) || (Syntax.isStringPrefix(cursor.peek()) && Syntax.isQuote(cursor.peek(1)));
  }

  private boolean startsNumber() {
    char c = cursor.peek();
    return Syntax.isDigit(c) || (c == '.' && Syntax.isDigit(cursor.peek(1)) && !previousIsOperand.getAsBoolean());
  }

  /** {@code $} always starts a node path; {@code %} only where an operand may start, otherwise it is modulo. */
  private boolean startsNodePath() {
    char c = cursor.peek();
    boolean nextStartsName = Syntax.isQuote(cursor.peek(1)) || Syntax.isIdentifierStart(cursor.codePoint(1));
    return c == '$' || (c == '%' && nextStartsName && !previousIsOperand.getAsBoolean());
  }

  private TokenKind string() throws LexException {
    if (!Syntax.isQuote(cursor.peek())) cursor.advance(1);
    quoted();
    return TokenKind.STRING;
  }

  /**
   * Raw strings scan like regular ones: a backslash still protects the next character from ending the string.
   * Godot 4.7 accepts raw line breaks in every string kind, not only in triple-quoted ones.
   */
  private void quoted() throws LexException {
    int line = cursor.line();
    int column = cursor.column();
    String quote = String.valueOf(cursor.peek());
    String close = cursor.startsWith(quote.repeat(3)) ? quote.repeat(3) : quote;
    cursor.advance(close.length());
    while (!cursor.startsWith(close)) stringCharacter(line, column);
    cursor.advance(close.length());
  }

  private void stringCharacter(int line, int column) throws LexException {
    if (cursor.atEnd()) throw new LexException("Unterminated string", line, column);
    if (cursor.peek() == '\\') {
      cursor.advance(1);
      if (cursor.atEnd()) return;
    }
    if (cursor.isLineBreak(0)) cursor.newline(); else cursor.advance(1);
  }

  private TokenKind number() {
    if (cursor.startsWithIgnoreCase("0x")) return digits(2, c -> Character.digit(c, 16) >= 0 || c == '_');
    if (cursor.startsWithIgnoreCase("0b")) return digits(2, c -> c == '0' || c == '1' || c == '_');
    decimalDigits();
    if (startsFraction()) {
      cursor.advance(1);
      decimalDigits();
    }
    exponent();
    return TokenKind.NUMBER;
  }

  /** {@code .5} after digits, or a trailing {@code .} as in {@code 123.}; {@code 5.abs()} stays a member access. */
  private boolean startsFraction() {
    return cursor.peek() == '.' && cursor.peek(1) != '.' && !Syntax.isIdentifierStart(cursor.codePoint(1));
  }

  private TokenKind digits(int prefixLength, IntPredicate digit) {
    cursor.advance(prefixLength);
    cursor.skipWhile(digit);
    return TokenKind.NUMBER;
  }

  private void decimalDigits() { cursor.skipWhile(c -> Syntax.isDigit((char) c) || c == '_'); }

  private void exponent() {
    if (cursor.peek() != 'e' && cursor.peek() != 'E') return;
    int sign = cursor.peek(1) == '+' || cursor.peek(1) == '-' ? 1 : 0;
    if (!Syntax.isDigit(cursor.peek(1 + sign))) return;
    cursor.advance(1 + sign);
    decimalDigits();
  }

  private TokenKind identifier() throws LexException {
    name();
    return TokenKind.IDENTIFIER;
  }

  private void name() throws LexException {
    if (!Syntax.isIdentifierStart(cursor.codePoint())) throw cursor.error("Identifier expected");
    do cursor.advanceCodePoint(); while (Syntax.isIdentifierPart(cursor.codePoint()));
  }

  private TokenKind annotation() throws LexException {
    cursor.advance(1);
    name();
    return TokenKind.ANNOTATION;
  }

  /** {@code $Path/To/Node}, {@code %Unique/%Inner}, or a quoted path after {@code $}, {@code %} or {@code $%}. */
  private TokenKind nodePath() throws LexException {
    cursor.advance(1);
    do {
      if (cursor.peek() == '%') cursor.advance(1);
      if (Syntax.isQuote(cursor.peek())) {
        quoted();
        return TokenKind.NODE_PATH;
      }
      name();
    } while (continuesNodePath());
    return TokenKind.NODE_PATH;
  }

  private boolean continuesNodePath() {
    boolean continues = cursor.peek() == '/' && (cursor.peek(1) == '%' || Syntax.isIdentifierStart(cursor.codePoint(1)));
    if (continues) cursor.advance(1);
    return continues;
  }

  private TokenKind operator() throws LexException {
    String operator = Syntax.operatorAt(cursor);
    if (operator == null) throw cursor.error("Unexpected character '" + Character.toString(cursor.codePoint()) + "'");
    cursor.advance(operator.length());
    return TokenKind.OPERATOR;
  }
}
