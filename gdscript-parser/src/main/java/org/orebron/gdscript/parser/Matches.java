package org.orebron.gdscript.parser;

import org.orebron.gdscript.lexer.Token;

/** The match statement: branches with pattern lists, optional {@code when} guards and bodies. */
final class Matches {
  private final Grammar grammar;

  Matches(Grammar grammar) { this.grammar = grammar; }

  void match(int indent) throws ParseException {
    grammar.tokens.expect("match");
    grammar.expressions.expression();
    grammar.tokens.expect(":");
    grammar.blocks.indented(indent, this::branch);
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

  /** Whether any pattern in the list matches everything. */
  private boolean patternList() throws ParseException {
    boolean catchAll = pattern();
    while (grammar.tokens.accept(",")) catchAll |= pattern();
    return catchAll;
  }

  /** Parses one pattern; returns true for {@code _} and a bare {@code var name}, which match everything. */
  private boolean pattern() throws ParseException {
    TokenStream tokens = grammar.tokens;
    if (tokens.accept("var")) {
      tokens.name();
      return true;
    }
    if (tokens.accept("..")) return false;
    if (tokens.at("[")) arrayPattern();
    else if (tokens.at("{")) dictionaryPattern();
    else return valuePattern();
    return false;
  }

  private boolean valuePattern() throws ParseException {
    boolean wildcard = TokenStream.is(grammar.tokens.peek(), "_");
    grammar.expressions.expression();
    return wildcard;
  }

  private void arrayPattern() throws ParseException {
    TokenStream tokens = grammar.tokens;
    tokens.next();
    tokens.openBracket();
    while (!tokens.at("]")) {
      pattern();
      if (!tokens.accept(",")) break;
    }
    tokens.close("]");
  }

  private void dictionaryPattern() throws ParseException {
    TokenStream tokens = grammar.tokens;
    tokens.next();
    tokens.openBracket();
    while (!tokens.at("}")) {
      if (!tokens.accept("..")) keyPattern();
      if (!tokens.accept(",")) break;
    }
    tokens.close("}");
  }

  private void keyPattern() throws ParseException {
    grammar.expressions.expression();
    if (grammar.tokens.accept(":")) pattern();
  }
}
