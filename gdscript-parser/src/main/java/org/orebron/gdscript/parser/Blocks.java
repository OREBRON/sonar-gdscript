package org.orebron.gdscript.parser;

import org.orebron.gdscript.lexer.Token;
import org.orebron.gdscript.lexer.TokenKind;

/** Indentation-based bodies, shared by class members, statements, match branches and accessors. */
final class Blocks {
  /** Parses one item at the given indentation: a member, a statement, a match branch or an accessor list. */
  @FunctionalInterface
  interface Item {
    void parse(int indent) throws ParseException;
  }

  private final Grammar grammar;

  Blocks(Grammar grammar) { this.grammar = grammar; }

  /** Items at one indentation. Stops at a dedenting NEWLINE, which is left for the enclosing block. */
  void items(int indent, Item item) throws ParseException {
    TokenStream tokens = grammar.tokens;
    while (true) {
      suite(indent, item);
      Token end = tokens.raw();
      if (end.kind() == TokenKind.EOF || (end.kind() == TokenKind.NEWLINE && end.nextIndent() < indent)) return;
      if (end.kind() != TokenKind.NEWLINE) throw TokenStream.error(end, "Expected end of line");
      if (end.nextIndent() > indent) throw TokenStream.error(tokens.rawAhead(1), "Unexpected indentation");
      tokens.skipRawNewline();
      if (tokens.raw().kind() == TokenKind.EOF) return;
    }
  }

  /** Items on one line, separated by ';'. */
  void suite(int indent, Item item) throws ParseException {
    do item.parse(indent); while (grammar.tokens.accept(";") && !grammar.tokens.atEndOfLine());
  }

  /** The body after ':': the rest of the line, or an indented block below it. */
  void body(int parentIndent, Item item) throws ParseException {
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
