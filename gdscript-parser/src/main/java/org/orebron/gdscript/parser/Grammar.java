package org.orebron.gdscript.parser;

import org.orebron.gdscript.lexer.Token;

/** Shared parser state and the mutually recursive grammar parts; parts read other parts only when they run. */
final class Grammar {
  static final int MAX_DEPTH = 256;
  final TokenStream tokens;
  final TreeBuilder tree;
  final Blocks blocks = new Blocks(this);
  final Declarations declarations = new Declarations(this);
  final Statements statements = new Statements(this);
  final Matches matches = new Matches(this);
  final Expressions expressions = new Expressions(this);
  final Signatures signatures = new Signatures(this);
  private int depth;

  Grammar(TokenStream tokens) {
    this.tokens = tokens;
    this.tree = new TreeBuilder(tokens::lastConsumed);
  }

  /** One counter covers blocks and expressions (ADR 0002). */
  void enter(Token at) throws ParseException {
    if (++depth > MAX_DEPTH) throw TokenStream.error(at, "Nesting deeper than " + MAX_DEPTH);
  }

  void leave() { depth--; }
}
