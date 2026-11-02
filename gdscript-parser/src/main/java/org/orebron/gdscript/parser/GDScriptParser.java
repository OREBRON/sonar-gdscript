package org.orebron.gdscript.parser;

import java.util.List;
import java.util.function.BooleanSupplier;
import org.orebron.gdscript.lexer.Token;
import org.orebron.gdscript.lexer.TokenKind;

/** Parses lexer tokens into a SyntaxNode tree; succeeds only if every token is consumed (ADR 0002). */
public final class GDScriptParser {
  private GDScriptParser() {}

  public static SyntaxNode parse(List<Token> tokens) throws ParseException { return parse(tokens, () -> false); }

  /** {@code cancelled} is polled every 10,000 consumed tokens; a true answer aborts with a CancellationException. */
  public static SyntaxNode parse(List<Token> tokens, BooleanSupplier cancelled) throws ParseException {
    var grammar = new Grammar(new TokenStream(tokens, cancelled));
    grammar.tree.open(SyntaxKind.SCRIPT, grammar.tokens.raw());
    if (grammar.tokens.raw().kind() != TokenKind.EOF) script(grammar);
    return grammar.tree.close();
  }

  private static void script(Grammar grammar) throws ParseException {
    TokenStream tokens = grammar.tokens;
    if (tokens.raw().lineIndent() != 0) throw TokenStream.error(tokens.raw(), "Unexpected indentation");
    grammar.blocks.items(0, grammar.declarations::member);
    if (tokens.raw().kind() != TokenKind.EOF) throw TokenStream.error(tokens.raw(), "Unexpected token");
  }
}
