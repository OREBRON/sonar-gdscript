package org.orebron.gdscript.parser;

import org.orebron.gdscript.lexer.Token;

/** The match statement: branches with pattern lists, optional {@code when} guards and bodies. */
final class Matches {
  private static final int CATCH_ALL = 1;
  private static final int BINDS = 2;
  private static final int REST = 4;
  private final Grammar grammar;

  Matches(Grammar grammar) { this.grammar = grammar; }

  void match(int indent) throws ParseException {
    grammar.tree.open(SyntaxKind.MATCH, grammar.tokens.expect("match"));
    grammar.expressions.expression();
    grammar.tokens.expect(":");
    grammar.blocks.indented(indent, this::branch);
    grammar.tree.close();
  }

  /** A branch, a standalone annotation line before one, or {@code pass} in a match without branches. */
  private void branch(int indent) throws ParseException {
    TokenStream tokens = grammar.tokens;
    if (grammar.declarations.annotationsEndLine() || tokens.accept("pass")) return;
    Token start = tokens.peek();
    boolean catchAll = patternList();
    boolean guarded = tokens.at("when");
    grammar.tree.open(catchAll && !guarded ? SyntaxKind.CATCH_ALL_BRANCH : SyntaxKind.MATCH_BRANCH, start);
    if (tokens.accept("when")) grammar.expressions.expression();
    tokens.expect(":");
    grammar.blocks.body(indent, grammar.statements::statement);
    grammar.tree.close();
  }

  /** Whether any pattern in the list matches everything; Godot allows variable binds only in a single pattern. */
  private boolean patternList() throws ParseException {
    int flags = topPattern();
    while (grammar.tokens.accept(",")) {
      Token next = grammar.tokens.peek();
      flags |= topPattern();
      if ((flags & BINDS) != 0) throw TokenStream.error(next, "Cannot use a variable bind with multiple patterns");
    }
    return (flags & CATCH_ALL) != 0;
  }

  private int topPattern() throws ParseException {
    Token start = grammar.tokens.peek();
    int flags = pattern();
    if ((flags & REST) != 0) throw TokenStream.error(start, "Rest pattern can only be used inside array and dictionary patterns");
    return flags;
  }

  /** Parses one pattern: CATCH_ALL for {@code _} and a bare {@code var name}, BINDS when it binds a variable, REST for {@code ..}. */
  private int pattern() throws ParseException {
    TokenStream tokens = grammar.tokens;
    if (tokens.accept("var")) {
      tokens.name();
      return CATCH_ALL | BINDS;
    }
    if (tokens.accept("..")) return REST;
    if (tokens.at("[")) return arrayPattern();
    if (tokens.at("{")) return dictionaryPattern();
    return valuePattern() ? CATCH_ALL : 0;
  }

  private boolean valuePattern() throws ParseException {
    boolean wildcard = TokenStream.is(grammar.tokens.peek(), "_");
    grammar.expressions.expression();
    return wildcard;
  }

  /** Returns BINDS when an element binds a variable; {@code ..} must be the last element. */
  private int arrayPattern() throws ParseException {
    TokenStream tokens = grammar.tokens;
    tokens.next();
    tokens.openBracket();
    int binds = 0;
    boolean rest = false;
    while (!tokens.at("]")) {
      if (rest) throw TokenStream.error(tokens.peek(), "The \"..\" pattern must be the last element in the pattern array");
      int flags = pattern();
      rest = (flags & REST) != 0;
      binds |= flags & BINDS;
      if (!tokens.accept(",")) break;
    }
    tokens.close("]");
    return binds;
  }

  private int dictionaryPattern() throws ParseException {
    TokenStream tokens = grammar.tokens;
    tokens.next();
    tokens.openBracket();
    int binds = 0;
    boolean rest = false;
    while (!tokens.at("}")) {
      if (rest) throw TokenStream.error(tokens.peek(), "The \"..\" pattern must be the last element in the pattern dictionary");
      rest = tokens.accept("..");
      if (!rest) binds |= keyPattern();
      if (!tokens.accept(",")) break;
    }
    tokens.close("}");
    return binds;
  }

  private int keyPattern() throws ParseException {
    grammar.expressions.expression();
    if (!grammar.tokens.accept(":")) return 0;
    Token value = grammar.tokens.peek();
    int flags = pattern();
    if ((flags & REST) != 0) throw TokenStream.error(value, "The \"..\" pattern cannot be used as a value");
    return flags & BINDS;
  }
}
