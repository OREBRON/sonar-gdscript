package org.orebron.gdscript.parser;

import java.util.List;
import org.orebron.gdscript.lexer.Token;
import org.orebron.gdscript.lexer.TokenKind;

/** Class-level members of the script and of inner classes; variables and constants are shared with statements. */
final class Declarations {
  private final Grammar grammar;

  Declarations(Grammar grammar) { this.grammar = grammar; }

  void member(int indent) throws ParseException {
    if (annotationsEndLine()) return;
    TokenStream tokens = grammar.tokens;
    if (tokens.atKind(TokenKind.STRING)) {
      tokens.next(); // Godot allows strings as comments in class bodies
      return;
    }
    switch (keyword(tokens.peek())) {
      case "class_name" -> className();
      case "extends" -> {
        tokens.next();
        extendsTarget();
      }
      case "signal" -> signal();
      case "enum" -> enumeration();
      case "const" -> constant();
      case "static" -> staticMember(indent);
      case "var" -> variable(indent, true);
      case "func" -> function(indent, false);
      case "class" -> innerClass(indent);
      case "pass" -> tokens.next();
      default -> throw TokenStream.error(tokens.peek(), "Unexpected token in class body");
    }
  }

  static String keyword(Token token) { return token.kind() == TokenKind.IDENTIFIER ? token.text() : ""; }

  /** Parses leading annotations; returns true when they stand alone on their line. */
  boolean annotationsEndLine() throws ParseException {
    TokenStream tokens = grammar.tokens;
    while (tokens.atKind(TokenKind.ANNOTATION)) {
      tokens.next();
      if (TokenStream.is(tokens.raw(), "(")) grammar.expressions.arguments();
      if (tokens.atEndOfLine()) return true;
    }
    return false;
  }

  void variable(int indent, boolean member) throws ParseException {
    TokenStream tokens = grammar.tokens;
    tokens.expect("var");
    tokens.name();
    if (member && accessorsFollowColon()) {
      tokens.next();
      accessors(indent);
      return;
    }
    typeAndValue();
    if (member && tokens.accept(":")) accessors(indent);
  }

  void constant() throws ParseException {
    TokenStream tokens = grammar.tokens;
    tokens.expect("const");
    tokens.name();
    if (tokens.accept(":=")) {
      grammar.expressions.expression();
      return;
    }
    if (tokens.accept(":")) grammar.signatures.typeAfterColon();
    tokens.expect("=");
    grammar.expressions.expression();
  }

  private void className() throws ParseException {
    grammar.tokens.next();
    grammar.tokens.name();
    if (grammar.tokens.accept("extends")) extendsTarget();
  }

  private void extendsTarget() throws ParseException {
    TokenStream tokens = grammar.tokens;
    if (tokens.atKind(TokenKind.STRING)) tokens.next(); else tokens.name();
    while (tokens.accept(".")) tokens.name();
  }

  private void signal() throws ParseException {
    grammar.tokens.next();
    grammar.tokens.name();
    if (grammar.tokens.at("(")) grammar.signatures.signalParameterList();
  }

  private void enumeration() throws ParseException {
    TokenStream tokens = grammar.tokens;
    tokens.next();
    if (!tokens.at("{")) tokens.name();
    tokens.expect("{");
    tokens.openBracket();
    while (!tokens.at("}")) {
      tokens.name();
      if (tokens.accept("=")) grammar.expressions.expression();
      if (!tokens.accept(",")) break;
    }
    tokens.close("}");
  }

  private void staticMember(int indent) throws ParseException {
    TokenStream tokens = grammar.tokens;
    tokens.next();
    if (tokens.at("func")) function(indent, true);
    else if (tokens.at("var")) variable(indent, true);
    else throw TokenStream.error(tokens.peek(), "Expected func or var after static");
  }

  private void function(int indent, boolean isStatic) throws ParseException {
    TokenStream tokens = grammar.tokens;
    tokens.expect("func");
    Token name = tokens.name();
    List<Token> parameters = grammar.signatures.parameterList();
    if (name.text().equals("_static_init")) {
      if (!isStatic) throw TokenStream.error(name, "Static constructor must be declared static");
      if (!parameters.isEmpty()) throw TokenStream.error(name, "Static constructor cannot have parameters");
    }
    grammar.signatures.returnType();
    if (!tokens.accept(":")) return; // a body-less @abstract declaration
    body(SyntaxKind.FUNCTION, name, parameters, indent, grammar.statements::statement);
  }

  private void innerClass(int indent) throws ParseException {
    TokenStream tokens = grammar.tokens;
    tokens.expect("class");
    Token name = tokens.name();
    if (tokens.accept("extends")) extendsTarget();
    tokens.expect(":");
    body(SyntaxKind.CLASS, name, List.of(), indent, this::member);
  }

  private void body(SyntaxKind kind, Token token, List<Token> parameters, int indent, Blocks.Item item) throws ParseException {
    grammar.tree.open(kind, token);
    grammar.signatures.keep(parameters);
    grammar.functionBody(kind == SyntaxKind.FUNCTION ? token.text() : "", indent, item);
    grammar.tree.close();
  }

  private boolean accessorsFollowColon() {
    Token after = grammar.tokens.rawAhead(1);
    return grammar.tokens.at(":")
        && (after.kind() == TokenKind.NEWLINE || TokenStream.is(after, "get") || TokenStream.is(after, "set"));
  }

  private void typeAndValue() throws ParseException {
    TokenStream tokens = grammar.tokens;
    if (tokens.accept(":=")) {
      grammar.expressions.expression();
      return;
    }
    if (tokens.accept(":")) grammar.signatures.typeAfterColon();
    if (tokens.accept("=")) grammar.expressions.expression();
  }

  private void accessors(int indent) throws ParseException {
    if (grammar.tokens.atEndOfLine()) grammar.blocks.indented(indent, blockIndent -> accessorList(blockIndent, true));
    else accessorList(indent, false);
  }

  /** In an indented block, as in Godot, a line may end after ',' when the next accessor starts a line at the same indent. */
  private void accessorList(int indent, boolean indented) throws ParseException {
    TokenStream tokens = grammar.tokens;
    accessor(indent);
    while (tokens.accept(",")) {
      if (indented && tokens.raw().kind() == TokenKind.NEWLINE && tokens.raw().nextIndent() == indent) tokens.skipRawNewline();
      accessor(indent);
    }
  }

  private void accessor(int indent) throws ParseException {
    TokenStream tokens = grammar.tokens;
    Token name = tokens.name();
    if (!name.text().equals("get") && !name.text().equals("set")) throw TokenStream.error(name, "Expected get or set");
    if (tokens.accept("=")) {
      tokens.name(); // delegated to a named function: no body
      return;
    }
    List<Token> parameters = tokens.at("(") ? grammar.signatures.parameterList() : List.of();
    tokens.expect(":");
    body(SyntaxKind.ACCESSOR, name, parameters, indent, grammar.statements::statement);
  }
}
