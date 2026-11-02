package org.orebron.gdscript.lexer;

import java.util.Set;

/** GDScript reserved words, shared by the lexer and the parser. */
public final class Keywords {
  private static final Set<String> RESERVED = Set.of("if", "elif", "else", "for", "while", "match", "break",
      "continue", "pass", "return", "class", "class_name", "extends", "is", "in", "as", "self", "super", "signal",
      "func", "static", "const", "enum", "var", "breakpoint", "await", "and", "or", "not", "true", "false", "null",
      "void", "PI", "TAU", "INF", "NAN");
  private static final Set<String> OPERANDS = Set.of("self", "super", "true", "false", "null", "PI", "TAU", "INF", "NAN");

  private Keywords() {}

  public static boolean isReserved(String word) { return RESERVED.contains(word); }

  /** Reserved words that are values, so an expression may end with them. */
  public static boolean isOperand(String word) { return OPERANDS.contains(word); }
}
