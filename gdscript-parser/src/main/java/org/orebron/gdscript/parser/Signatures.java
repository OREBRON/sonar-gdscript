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

  /** Parses {@code (parameters)} of a function, accessor or lambda and returns the parameter names. */
  List<Token> parameterList() throws ParseException { return parameterList(false); }

  /** Signal parameters have neither defaults nor a rest parameter. */
  void signalParameterList() throws ParseException { parameterList(true); }

  /** Godot's order rules: optional parameters come last, and a rest parameter {@code ...name} ends the list. */
  private List<Token> parameterList(boolean signal) throws ParseException {
    TokenStream tokens = grammar.tokens;
    tokens.expect("(");
    tokens.openBracket();
    var names = new ArrayList<Token>();
    boolean optional = false;
    boolean rest = false;
    while (!tokens.at(")")) {
      if (rest) throw TokenStream.error(tokens.peek(), "Cannot have parameters after the rest parameter");
      Token dots = tokens.peek();
      rest = tokens.accept("...");
      if (rest && signal) throw TokenStream.error(dots, "Signals cannot have a rest parameter");
      Token name = tokens.name();
      boolean hasDefault = typeAndDefault();
      if (hasDefault && signal) throw TokenStream.error(name, "Signal parameters cannot have a default value");
      if (hasDefault && rest) throw TokenStream.error(name, "The rest parameter cannot have a default value");
      if (!hasDefault && !rest && optional) throw TokenStream.error(name, "Cannot have mandatory parameters after optional parameters");
      optional |= hasDefault;
      names.add(name);
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
    grammar.enter(tokens.next());
    tokens.openBracket();
    type();
    if (tokens.accept(",")) type();
    tokens.close("]");
    grammar.leave();
  }

  /** What follows a parameter name: {@code : Type}, {@code := default} or {@code : Type = default}; true with a default. */
  private boolean typeAndDefault() throws ParseException {
    TokenStream tokens = grammar.tokens;
    if (tokens.accept(":=")) {
      grammar.expressions.expression();
      return true;
    }
    if (tokens.accept(":")) typeAfterColon();
    if (!tokens.accept("=")) return false;
    grammar.expressions.expression();
    return true;
  }
}
