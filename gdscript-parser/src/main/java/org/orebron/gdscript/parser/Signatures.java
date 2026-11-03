package org.orebron.gdscript.parser;

import java.util.ArrayList;
import java.util.List;
import org.orebron.gdscript.lexer.Keywords;
import org.orebron.gdscript.lexer.Token;
import org.orebron.gdscript.lexer.TokenKind;

/** Parameter lists, return types and type annotations, shared by functions, lambdas, signals and accessors. */
final class Signatures {
  private final Grammar grammar;

  Signatures(Grammar grammar) { this.grammar = grammar; }

  /** Parses {@code (parameters)} and returns the parameter names. */
  List<Token> parameterList() throws ParseException {
    TokenStream tokens = grammar.tokens;
    tokens.expect("(");
    tokens.openBracket();
    var names = new ArrayList<Token>();
    while (!tokens.at(")")) {
      names.add(parameter());
      if (!tokens.accept(",")) break;
    }
    tokens.close(")");
    return names;
  }

  /** Adds a PARAMETER leaf for each name to the node being built. */
  void keep(List<Token> parameters) {
    parameters.forEach(name -> grammar.tree.leaf(SyntaxKind.PARAMETER, name));
  }

  void returnType() throws ParseException {
    if (grammar.tokens.accept("->")) type();
  }

  /** The type after a consumed ':'; none in {@code name : = value}, which infers it like {@code :=}. */
  void typeAfterColon() throws ParseException {
    if (!grammar.tokens.at("=")) type();
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
  private Token parameter() throws ParseException {
    TokenStream tokens = grammar.tokens;
    tokens.accept("...");
    Token name = tokens.name();
    if (tokens.accept(":=")) {
      grammar.expressions.expression();
      return name;
    }
    if (tokens.accept(":")) typeAfterColon();
    if (tokens.accept("=")) grammar.expressions.expression();
    return name;
  }
}
