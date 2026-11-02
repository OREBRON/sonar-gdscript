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
  static boolean isIdentifierStart(int codePoint) { return codePoint == '_' || Character.isUnicodeIdentifierStart(codePoint); }

  static boolean isIdentifierPart(int codePoint) {
    return Character.isUnicodeIdentifierPart(codePoint) && !Character.isIdentifierIgnorable(codePoint);
  }
}
