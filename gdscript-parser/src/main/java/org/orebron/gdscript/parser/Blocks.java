package org.orebron.gdscript.parser;

import java.util.Set;
import org.orebron.gdscript.lexer.Token;
import org.orebron.gdscript.lexer.TokenKind;

/** Indentation-based bodies, shared by class members, statements, match branches and accessors. */
final class Blocks {
  /** Parses one item at the given indentation: a member, a statement, a match branch or an accessor list. */
  @FunctionalInterface
  interface Item {
    void parse(int indent) throws ParseException;
  }

  /** Tokens that cannot start a statement but can end an expression around a multi-line lambda. */
  private static final Set<String> EXPRESSION_CLOSERS = Set.of(")", "]", "}", ",");
  private final Grammar grammar;

  Blocks(Grammar grammar) { this.grammar = grammar; }

  /** Items at one indentation. Stops at a dedenting NEWLINE, which is left for the enclosing block. */
  void items(int indent, Item item) throws ParseException {
    do suite(indent, item); while (continuesAt(indent, grammar.tokens.raw()));
  }

  /**
   * Consumes the line break when the next line continues this block. Inside brackets a lambda body, and every
   * block in it, also ends before ')', ']', '}' or ',' on the same or the next line, as Godot allows.
   */
  private boolean continuesAt(int indent, Token end) throws ParseException {
    TokenStream tokens = grammar.tokens;
    if (end.kind() == TokenKind.EOF || closesEnclosingExpression(end)) return false;
    if (end.kind() != TokenKind.NEWLINE) throw TokenStream.error(end, "Expected end of line");
    Token next = tokens.rawAhead(1);
    if (end.nextIndent() < indent || closesEnclosingExpression(next)) return false;
    if (end.nextIndent() > indent) throw TokenStream.error(next, "Unexpected indentation");
    tokens.skipRawNewline();
    return next.kind() != TokenKind.EOF;
  }

  private boolean closesEnclosingExpression(Token token) {
    return grammar.tokens.insideBrackets() && token.kind() == TokenKind.OPERATOR && EXPRESSION_CLOSERS.contains(token.text());
  }

  /** Items on one line, separated by ';'. */
  void suite(int indent, Item item) throws ParseException {
    do item.parse(indent); while (semicolons() && !grammar.tokens.atEndOfLine());
  }

  /** Consumes one or more ';', as in {@code a();;; b()}; returns whether there was any. */
  private boolean semicolons() {
    boolean any = false;
    while (grammar.tokens.accept(";")) any = true;
    return any;
  }

  /** The body after ':': the rest of the line, or an indented block below it; empty before a closer, as in {@code (func():)}. */
  void body(int parentIndent, Item item) throws ParseException {
    if (closesEnclosingExpression(grammar.tokens.raw())) return;
    if (grammar.tokens.atEndOfLine()) indented(parentIndent, item);
    else suite(parentIndent, item);
  }

  /** A block that must start on the next line, indented deeper than its parent line. */
  void indented(int parentIndent, Item item) throws ParseException {
    TokenStream tokens = grammar.tokens;
    Token start = tokens.raw();
    if (start.kind() != TokenKind.NEWLINE || start.nextIndent() <= parentIndent) throw TokenStream.error(start, "Expected an indented block");
    grammar.enter(start);
    tokens.skipRawNewline();
    tokens.respectNewlines();
    items(start.nextIndent(), item);
    tokens.restoreNewlines();
    grammar.leave();
  }
}
