package org.orebron.gdscript.parser;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;
import org.orebron.gdscript.lexer.Keywords;
import org.orebron.gdscript.lexer.Token;
import org.orebron.gdscript.lexer.TokenKind;

/** Significant tokens with lookahead. Inside brackets newlines are skipped unless a block body re-enables them. */
final class TokenStream {
  private static final int CANCEL_CHECK_INTERVAL = 10_000;
  private final List<Token> tokens;
  private final BooleanSupplier cancelled;
  private final Deque<Boolean> skipNewlines = new ArrayDeque<>();
  private int position;
  private int consumed;
  private int openBrackets;

  TokenStream(List<Token> all, BooleanSupplier cancelled) {
    this.tokens = all.stream().filter(t -> t.kind() != TokenKind.COMMENT && t.kind() != TokenKind.CONTINUATION).toList();
    this.cancelled = cancelled;
    skipNewlines.push(false);
  }

  Token raw() { return tokens.get(position); }
  Token rawAhead(int count) { return tokens.get(Math.min(position + count, tokens.size() - 1)); }

  Token peek() {
    if (skipNewlines.peek()) while (raw().kind() == TokenKind.NEWLINE) position++;
    return raw();
  }

  Token next() {
    Token token = peek();
    if (token.kind() != TokenKind.EOF) advance();
    return token;
  }

  /** Consumes the raw NEWLINE at the cursor. */
  void skipRawNewline() { advance(); }

  boolean at(String text) { return is(peek(), text); }
  boolean atKind(TokenKind kind) { return peek().kind() == kind; }

  boolean accept(String text) {
    if (!at(text)) return false;
    advance();
    return true;
  }

  Token expect(String text) throws ParseException {
    if (!at(text)) throw error(peek(), "Expected '" + text + "'");
    return next();
  }

  Token name() throws ParseException {
    Token token = peek();
    if (token.kind() != TokenKind.IDENTIFIER || Keywords.isReserved(token.text())) throw error(token, "Expected an identifier");
    return next();
  }

  boolean atEndOfLine() { return raw().kind() == TokenKind.NEWLINE || raw().kind() == TokenKind.EOF; }

  /** Call after consuming an opening bracket; {@link #close} consumes the closer and restores the previous mode. */
  void openBracket() {
    skipNewlines.push(true);
    openBrackets++;
  }

  void close(String closer) throws ParseException {
    expect(closer);
    skipNewlines.pop();
    openBrackets--;
  }

  /** True inside any open bracket, including the body of a multi-line lambda written in one. */
  boolean insideBrackets() { return openBrackets > 0; }

  void respectNewlines() { skipNewlines.push(false); }
  void restoreNewlines() { skipNewlines.pop(); }

  static boolean is(Token token, String text) {
    return (token.kind() == TokenKind.OPERATOR || token.kind() == TokenKind.IDENTIFIER) && token.text().equals(text);
  }

  static ParseException error(Token token, String message) {
    String near = token.kind() == TokenKind.EOF ? " at end of file" : " near '" + token.text().strip() + "'";
    return new ParseException(message + near, token.line(), token.column());
  }

  private void advance() {
    position++;
    if (++consumed % CANCEL_CHECK_INTERVAL == 0 && cancelled.getAsBoolean()) throw new CancellationException("GDScript parsing was cancelled");
  }
}
