package org.orebron.gdscript.parser;

import org.orebron.gdscript.lexer.Keywords;
import org.orebron.gdscript.lexer.Token;
import org.orebron.gdscript.lexer.TokenKind;

/** Parameter lists, return types and type annotations, shared by functions, lambdas, signals and accessors. */
final class Signatures {
  private final Grammar grammar;

  Signatures(Grammar grammar) { this.grammar = grammar; }

  void parameterList() throws ParseException {
    TokenStream tokens = grammar.tokens;
    tokens.expect("(");
    tokens.openBracket();
    while (!tokens.at(")")) {
      parameter();
      if (!tokens.accept(",")) break;
    }
    tokens.close(")");
  }

  void returnType() throws ParseException {
    if (grammar.tokens.accept("->")) type();
  }

  /** A type such as {@code int}, {@code Node.Inner} or {@code Dictionary[String, int]}; {@code void} is allowed. */
  void type() throws ParseException {
    TokenStream tokens = grammar.tokens;
    Token token = tokens.peek();
    if (token.kind() != TokenKind.IDENTIFIER || (Keywords.isReserved(token.text()) && !token.text().equals("void"))) {
      throw TokenStream.error(token, "Expected a type");
    }
    tokens.next();
    while (tokens.accept(".")) tokens.name();
    if (tokens.at("[")) typeArguments();
  }

  private void typeArguments() throws ParseException {
    TokenStream tokens = grammar.tokens;
    tokens.next();
    tokens.openBracket();
    type();
    if (tokens.accept(",")) type();
    tokens.close("]");
  }

  /** {@code name}, {@code name: Type}, {@code name := default}, {@code name: Type = default} or variadic {@code ...name}. */
  private void parameter() throws ParseException {
    TokenStream tokens = grammar.tokens;
    tokens.accept("...");
    tokens.name();
    if (tokens.accept(":=")) {
      grammar.expressions.expression();
      return;
    }
    if (tokens.accept(":")) type();
    if (tokens.accept("=")) grammar.expressions.expression();
  }
}
