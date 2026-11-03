package org.orebron.gdscript.lexer;

import java.util.List;

/** GDScript lexical data: new syntax is added here or in Keywords, not in the scanning code. */
final class Syntax {
  /** Longest first, so the first match is the longest operator. */
  private static final List<String> OPERATORS = List.of("**=", "<<=", ">>=", "...", "**", "<<", ">>", "==", "!=",
      "<=", ">=", "&&", "||", "+=", "-=", "*=", "/=", "%=", "&=", "|=", "^=", "->", ":=", "..",
      "+", "-", "*", "/", "%", "<", ">", "=", "!", "~", "&", "|", "^", "(", ")", "[", "]", "{", "}", ",", ":", ";", ".");

  private Syntax() {}

  static String operatorAt(Cursor cursor) {
    for (String operator : OPERATORS) {
      if (cursor.startsWith(operator)) return operator;
    }
    return null;
  }

  static boolean opensBracket(String operator) { return operator.equals("(") || operator.equals("[") || operator.equals("{"); }
  static boolean closesBracket(String operator) { return operator.equals(")") || operator.equals("]") || operator.equals("}"); }

  /** Whether an expression may end with this token, which makes a following {@code %} a modulo operator. */
  static boolean endsOperand(Token token) {
    return switch (token.kind()) {
      case NUMBER, STRING, NODE_PATH -> true;
      case IDENTIFIER -> !Keywords.isReserved(token.text()) || Keywords.isOperand(token.text());
      case OPERATOR -> closesBracket(token.text());
      default -> false;
    };
  }

  static boolean isQuote(char c) { return c == '"' || c == '\''; }
  static boolean isStringPrefix(char c) { return c == 'r' || c == '&' || c == '^'; }
  static boolean isDigit(char c) { return c >= '0' && c <= '9'; }
  static boolean isHexDigit(int c) { return (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F'); }

  /** Bidirectional controls that Godot rejects inside strings because they hide what the code does. */
  static boolean isTextDirectionControl(char c) {
    return c == 0x200E || c == 0x200F || (c >= 0x202A && c <= 0x202E) || (c >= 0x2066 && c <= 0x2069);
  }
  static boolean isIdentifierStart(int codePoint) { return codePoint == '_' || Character.isUnicodeIdentifierStart(codePoint); }

  static boolean isIdentifierPart(int codePoint) {
    return Character.isUnicodeIdentifierPart(codePoint) && !Character.isIdentifierIgnorable(codePoint);
  }
}
