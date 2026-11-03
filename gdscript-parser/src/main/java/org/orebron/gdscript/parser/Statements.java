package org.orebron.gdscript.parser;

import java.util.Set;
import org.orebron.gdscript.lexer.Token;
import org.orebron.gdscript.lexer.TokenKind;

/** Statements inside function, accessor, lambda and match-branch bodies. */
final class Statements {
  private static final Set<String> ASSIGNMENTS = Set.of("=", "+=", "-=", "*=", "/=", "%=", "**=", "<<=", ">>=", "&=", "|=", "^=");
  private static final Set<String> SIMPLE_STATEMENT_ENDS = Set.of(";", ")", ",", "]", "}");
  private static final Set<String> CONSTRUCTORS = Set.of("_init", "_static_init");
  private final Grammar grammar;

  Statements(Grammar grammar) { this.grammar = grammar; }

  void statement(int indent) throws ParseException {
    if (grammar.declarations.annotationsEndLine()) return;
    TokenStream tokens = grammar.tokens;
    switch (Declarations.keyword(tokens.peek())) {
      case "pass", "breakpoint" -> tokens.next();
      case "break", "continue" -> loopJump();
      case "assert" -> {
        tokens.next();
        grammar.expressions.arguments();
      }
      case "return" -> returnStatement();
      case "var" -> grammar.declarations.variable(indent, false);
      case "const" -> grammar.declarations.constant();
      case "if" -> conditional(indent);
      case "while" -> loop(SyntaxKind.WHILE, indent);
      case "for" -> loop(SyntaxKind.FOR, indent);
      case "match" -> grammar.matches.match(indent);
      default -> expressionStatement();
    }
  }

  private void loopJump() throws ParseException {
    Token jump = grammar.tokens.next();
    if (!grammar.inLoop()) throw TokenStream.error(jump, "Cannot use \"" + jump.text() + "\" outside of a loop");
  }

  private void returnStatement() throws ParseException {
    Token token = grammar.tokens.next();
    if (endsSimpleStatement()) return;
    if (CONSTRUCTORS.contains(grammar.function())) throw TokenStream.error(token, "Constructor cannot return a value");
    grammar.expressions.expression();
  }

  private boolean endsSimpleStatement() {
    Token token = grammar.tokens.peek();
    return token.kind() == TokenKind.NEWLINE || token.kind() == TokenKind.EOF
        || (token.kind() == TokenKind.OPERATOR && SIMPLE_STATEMENT_ENDS.contains(token.text()));
  }

  private void expressionStatement() throws ParseException {
    Token start = grammar.tokens.peek();
    if (TokenStream.is(start, "func")) throw TokenStream.error(start, "Standalone lambdas cannot be accessed");
    grammar.expressions.expression();
    Token token = grammar.tokens.peek();
    if (token.kind() == TokenKind.OPERATOR && ASSIGNMENTS.contains(token.text())) {
      grammar.tokens.next();
      grammar.expressions.expression();
    }
  }

  private void loop(SyntaxKind kind, int indent) throws ParseException {
    TokenStream tokens = grammar.tokens;
    grammar.tree.open(kind, tokens.next());
    if (kind == SyntaxKind.FOR) loopVariable();
    grammar.expressions.expression();
    tokens.expect(":");
    grammar.loopBody(indent, this::statement);
    grammar.tree.close();
  }

  private void loopVariable() throws ParseException {
    TokenStream tokens = grammar.tokens;
    tokens.name();
    if (tokens.accept(":")) grammar.signatures.type();
    tokens.expect("in");
  }

  private void conditional(int indent) throws ParseException {
    branch(SyntaxKind.IF, indent);
    while (nextLineStartsWith("elif", indent)) branch(SyntaxKind.ELIF, indent);
    if (nextLineStartsWith("else", indent)) {
      grammar.tree.open(SyntaxKind.ELSE, grammar.tokens.next());
      grammar.tokens.expect(":");
      grammar.blocks.body(indent, this::statement);
      grammar.tree.close();
    }
  }

  private void branch(SyntaxKind kind, int indent) throws ParseException {
    grammar.tree.open(kind, grammar.tokens.next());
    grammar.expressions.expression();
    grammar.tokens.expect(":");
    grammar.blocks.body(indent, this::statement);
    grammar.tree.close();
  }

  /** Whether the next line has this indentation and starts with the keyword; consumes the line break if so. */
  private boolean nextLineStartsWith(String keyword, int indent) {
    TokenStream tokens = grammar.tokens;
    Token end = tokens.raw();
    boolean continues = end.kind() == TokenKind.NEWLINE && end.nextIndent() == indent && TokenStream.is(tokens.rawAhead(1), keyword);
    if (continues) tokens.skipRawNewline();
    return continues;
  }
}
