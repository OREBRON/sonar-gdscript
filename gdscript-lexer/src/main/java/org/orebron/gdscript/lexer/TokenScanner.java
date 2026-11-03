package org.orebron.gdscript.lexer;

import java.util.function.BooleanSupplier;
import java.util.function.IntPredicate;

/** Scans one non-trivia token at the cursor and reports its kind; it knows nothing about lines or blocks. */
final class TokenScanner {
  private static final IntPredicate DECIMAL = c -> Syntax.isDigit((char) c);
  private static final String SIMPLE_ESCAPES = "abfnrtv'\"\\";
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
    boolean raw = cursor.peek() == 'r';
    if (!Syntax.isQuote(cursor.peek())) cursor.advance(1);
    quoted(raw);
    return TokenKind.STRING;
  }

  /**
   * Godot 4.7 accepts raw line breaks in every string kind, not only in triple-quoted ones. Outside raw strings a
   * backslash must start one of Godot's escapes; in raw strings it still protects the next character.
   */
  private void quoted(boolean raw) throws LexException {
    int line = cursor.line();
    int column = cursor.column();
    String quote = String.valueOf(cursor.peek());
    String close = cursor.startsWith(quote.repeat(3)) ? quote.repeat(3) : quote;
    cursor.advance(close.length());
    while (!cursor.startsWith(close)) stringCharacter(line, column, raw);
    cursor.advance(close.length());
  }

  private void stringCharacter(int line, int column, boolean raw) throws LexException {
    if (cursor.atEnd()) throw new LexException("Unterminated string", line, column);
    if (Syntax.isTextDirectionControl(cursor.peek())) throw cursor.error("Invisible text direction control character in a string");
    if (cursor.peek() == '\\') {
      if (!raw) {
        escape(line, column);
        return;
      }
      cursor.advance(1);
      if (cursor.atEnd()) return;
    }
    if (cursor.isLineBreak(0)) cursor.newline(); else cursor.advance(1);
  }

  /** One escape after a backslash, as Godot's tokenizer reads it; an escaped line break continues the string. */
  private void escape(int line, int column) throws LexException {
    int escapeColumn = cursor.column();
    cursor.advance(1);
    if (cursor.atEnd()) return; // the string is unterminated
    if (cursor.isLineBreak(0)) {
      cursor.newline();
      return;
    }
    char code = cursor.peek();
    cursor.advance(1);
    if (code == 'u' || code == 'U') hexEscape(code == 'U' ? 6 : 4, line, column);
    else if (SIMPLE_ESCAPES.indexOf(code) < 0) throw new LexException("Invalid escape in string", cursor.line(), escapeColumn);
  }

  private void hexEscape(int digits, int line, int column) throws LexException {
    for (int i = 0; i < digits; i++) {
      if (cursor.atEnd()) throw new LexException("Unterminated string", line, column);
      if (!Syntax.isHexDigit(cursor.peek())) throw cursor.error("Invalid hexadecimal digit in unicode escape sequence");
      cursor.advance(1);
    }
  }

  /** Follows Godot's tokenizer: a {@code .} after digits is a decimal point, so {@code 5.abs()} is an error. */
  private TokenKind number() throws LexException {
    if (cursor.startsWithIgnoreCase("0x")) return prefixed("0x", "hexadecimal", Syntax::isHexDigit);
    if (cursor.startsWithIgnoreCase("0b")) return prefixed("0b", "binary", c -> c == '0' || c == '1');
    boolean fraction = cursor.peek() == '.';
    if (fraction) cursor.advance(1);
    digits(DECIMAL);
    if (!fraction && startsFraction()) {
      cursor.advance(1);
      if (cursor.peek() == '_') throw cursor.error("Unexpected underscore after decimal point");
      digits(DECIMAL);
      fraction = true;
    }
    exponent();
    if (fraction && startsFraction()) throw cursor.error("Cannot use a decimal point twice in a number");
    return endOfNumber();
  }

  /** A {@code .} not starting {@code ..}; Godot reads it as a decimal point even before a letter. */
  private boolean startsFraction() { return cursor.peek() == '.' && cursor.peek(1) != '.'; }

  private TokenKind prefixed(String prefix, String base, IntPredicate digit) throws LexException {
    cursor.advance(prefix.length());
    if (cursor.peek() == '_') throw cursor.error("Unexpected underscore after \"" + prefix + "\"");
    if (!digits(digit)) throw cursor.error("Expected " + base + " digit after \"" + prefix + "\"");
    if (startsFraction()) throw cursor.error("Cannot use a decimal point in a " + base + " number");
    return endOfNumber();
  }

  /** Digits with single {@code _} separators; returns whether there was a digit. */
  private boolean digits(IntPredicate digit) throws LexException {
    boolean any = false;
    boolean underscore = false;
    while (digit.test(cursor.peek()) || cursor.peek() == '_') {
      boolean separator = cursor.peek() == '_';
      if (separator && underscore) throw cursor.error("Multiple underscores cannot be adjacent in a numeric literal");
      underscore = separator;
      any |= !separator;
      cursor.advance(1);
    }
    return any;
  }

  private void exponent() throws LexException {
    if (cursor.peek() != 'e' && cursor.peek() != 'E') return;
    cursor.advance(cursor.peek(1) == '+' || cursor.peek(1) == '-' ? 2 : 1);
    if (!Syntax.isDigit(cursor.peek())) throw cursor.error("Expected exponent value after \"e\"");
    digits(DECIMAL);
  }

  private TokenKind endOfNumber() throws LexException {
    if (!cursor.atEnd() && Syntax.isIdentifierPart(cursor.codePoint())) throw cursor.error("Invalid numeric notation");
    return TokenKind.NUMBER;
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

  /**
   * {@code $Path/To/Node}, {@code %Unique/%Inner} or quoted segments, as Godot's parser reads them from tokens: spaces
   * may follow {@code $} and surround {@code /}, and every {@code /} except {@code /=} continues the path.
   */
  private TokenKind nodePath() throws LexException {
    cursor.advance(1);
    cursor.advance(spacesAhead(0));
    do {
      if (cursor.peek() == '%') cursor.advance(1);
      if (Syntax.isQuote(cursor.peek())) quoted(false); else name();
    } while (continuesNodePath());
    return TokenKind.NODE_PATH;
  }

  private boolean continuesNodePath() {
    int slash = spacesAhead(0);
    boolean continues = cursor.peek(slash) == '/' && cursor.peek(slash + 1) != '=';
    if (continues) cursor.advance(slash + 1 + spacesAhead(slash + 1));
    return continues;
  }

  private int spacesAhead(int from) {
    int ahead = from;
    while (cursor.peek(ahead) == ' ' || cursor.peek(ahead) == '\t') ahead++;
    return ahead - from;
  }

  private TokenKind operator() throws LexException {
    String operator = Syntax.operatorAt(cursor);
    if (operator == null) throw cursor.error("Unexpected character '" + Character.toString(cursor.codePoint()) + "'");
    cursor.advance(operator.length());
    return TokenKind.OPERATOR;
  }
}
