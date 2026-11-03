package org.orebron.gdscript.parser;

import static java.util.Map.entry;

import java.util.Map;
import org.orebron.gdscript.lexer.Keywords;
import org.orebron.gdscript.lexer.Token;
import org.orebron.gdscript.lexer.TokenKind;

/** Expressions by precedence climbing; every token of an expression is consumed, none is skipped. */
final class Expressions {
  private static final int CAST = 1;
  private static final int TERNARY = 2;
  private static final int OR = 3;
  private static final int AND = 4;
  private static final int NOT = 5;
  private static final int IN = 6;
  private static final int SIGN = 14;
  private static final int AWAIT = 18;
  private static final Map<String, Integer> INFIX = Map.ofEntries(
      entry("as", CAST), entry("if", TERNARY), entry("or", OR), entry("||", OR), entry("and", AND), entry("&&", AND),
      entry("in", IN), entry("<", 7), entry(">", 7), entry("<=", 7), entry(">=", 7), entry("==", 7), entry("!=", 7),
      entry("|", 8), entry("^", 9), entry("&", 10), entry("<<", 11), entry(">>", 11), entry("+", 12), entry("-", 12),
      entry("*", 13), entry("/", 13), entry("%", 13), entry("**", 16), entry("is", 17));
  private final Grammar grammar;

  Expressions(Grammar grammar) { this.grammar = grammar; }

  void expression() throws ParseException { binary(0); }

  /** {@code (arguments)} of a call or an annotation. */
  void arguments() throws ParseException {
    grammar.tokens.expect("(");
    grammar.tokens.openBracket();
    list(")");
  }

  private void binary(int minimum) throws ParseException {
    TokenStream tokens = grammar.tokens;
    grammar.enter(tokens.peek());
    unary();
    for (int precedence = infixPrecedence(); precedence >= minimum; precedence = infixPrecedence()) {
      infix(tokens.next(), precedence);
    }
    grammar.leave();
  }

  private int infixPrecedence() {
    Token token = grammar.tokens.peek();
    if (TokenStream.is(token, "not") && TokenStream.is(grammar.tokens.rawAhead(1), "in")) return IN;
    boolean operator = token.kind() == TokenKind.OPERATOR || token.kind() == TokenKind.IDENTIFIER;
    return operator ? INFIX.getOrDefault(token.text(), -1) : -1;
  }

  private void infix(Token operator, int precedence) throws ParseException {
    switch (operator.text()) {
      case "not" -> {
        grammar.tokens.expect("in");
        binary(IN + 1);
      }
      case "if" -> conditional(operator);
      case "is" -> {
        grammar.tokens.accept("not");
        grammar.signatures.type();
      }
      case "as" -> grammar.signatures.type();
      default -> {
        if (precedence == OR || precedence == AND) grammar.tree.leaf(SyntaxKind.LOGICAL_OPERATOR, operator);
        binary(precedence + 1);
      }
    }
  }

  /** {@code value if condition else other}; the value before {@code if} is already parsed. */
  private void conditional(Token ifToken) throws ParseException {
    grammar.tree.leaf(SyntaxKind.CONDITIONAL_EXPRESSION, ifToken);
    binary(OR);
    grammar.tokens.expect("else");
    binary(TERNARY);
  }

  private void unary() throws ParseException {
    TokenStream tokens = grammar.tokens;
    Token token = tokens.peek();
    switch (token.kind() == TokenKind.OPERATOR || token.kind() == TokenKind.IDENTIFIER ? token.text() : "") {
      case "not", "!" -> {
        tokens.next();
        binary(NOT + 1);
      }
      case "-", "+", "~" -> {
        tokens.next();
        binary(SIGN);
      }
      case "await" -> {
        tokens.next();
        binary(AWAIT);
      }
      default -> postfix();
    }
  }

  private void postfix() throws ParseException {
    TokenStream tokens = grammar.tokens;
    primary();
    while (true) {
      if (tokens.at("(")) arguments();
      else if (tokens.accept(".")) memberName();
      else if (tokens.at("[")) subscript();
      else return;
    }
  }

  private void memberName() throws ParseException {
    Token token = grammar.tokens.peek();
    if (token.kind() != TokenKind.IDENTIFIER) throw TokenStream.error(token, "Expected a member name");
    grammar.tokens.next();
  }

  private void subscript() throws ParseException {
    grammar.tokens.next();
    grammar.tokens.openBracket();
    expression();
    grammar.tokens.close("]");
  }

  private void primary() throws ParseException {
    Token token = grammar.tokens.next();
    switch (token.kind()) {
      case NUMBER, STRING, NODE_PATH -> { }
      case IDENTIFIER -> identifier(token);
      case OPERATOR -> bracketed(token);
      default -> throw TokenStream.error(token, "Expected an expression");
    }
  }

  private void identifier(Token token) throws ParseException {
    if (token.text().equals("func")) lambda(token);
    else if (token.text().equals("preload")) {
      if (!grammar.tokens.at("(")) throw TokenStream.error(token, "Expected \"(\" after \"preload\"");
    }
    else if (Keywords.isReserved(token.text()) && !Keywords.isOperand(token.text())) throw TokenStream.error(token, "Unexpected keyword");
  }

  private void bracketed(Token open) throws ParseException {
    TokenStream tokens = grammar.tokens;
    switch (open.text()) {
      case "(" -> {
        tokens.openBracket();
        expression();
        tokens.close(")");
      }
      case "[" -> {
        tokens.openBracket();
        list("]");
      }
      case "{" -> {
        tokens.openBracket();
        dictionary();
      }
      default -> throw TokenStream.error(open, "Unexpected operator");
    }
  }

  /** Comma-separated expressions up to the closer, which is consumed; a trailing comma is allowed. */
  private void list(String closer) throws ParseException {
    TokenStream tokens = grammar.tokens;
    while (!tokens.at(closer)) {
      expression();
      if (!tokens.accept(",")) break;
    }
    tokens.close(closer);
  }

  private void dictionary() throws ParseException {
    TokenStream tokens = grammar.tokens;
    while (!tokens.at("}")) {
      dictionaryEntry();
      if (!tokens.accept(",")) break;
    }
    tokens.close("}");
  }

  /** {@code key: value}, or Lua style {@code name = value} and {@code "name" = value}. */
  private void dictionaryEntry() throws ParseException {
    TokenStream tokens = grammar.tokens;
    boolean luaKey = tokens.atKind(TokenKind.IDENTIFIER) || tokens.atKind(TokenKind.STRING);
    if (luaKey && TokenStream.is(tokens.rawAhead(1), "=")) {
      tokens.next();
      tokens.next();
      expression();
      return;
    }
    expression();
    tokens.expect(":");
    expression();
  }

  /** A lambda body's indentation is compared with the line that holds {@code func} (ADR 0002). */
  private void lambda(Token func) throws ParseException {
    TokenStream tokens = grammar.tokens;
    if (tokens.atKind(TokenKind.IDENTIFIER)) tokens.name();
    var parameters = grammar.signatures.parameterList();
    grammar.signatures.returnType();
    tokens.expect(":");
    grammar.tree.open(SyntaxKind.LAMBDA, func);
    grammar.signatures.keep(parameters);
    grammar.functionBody("", func.lineIndent(), grammar.statements::statement);
    grammar.tree.close();
  }
}
