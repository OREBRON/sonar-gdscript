package org.orebron.gdscript.lexer;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;

/**
 * Lossless GDScript tokenizer (ADR 0002): tokens are ordered, never overlap and cover every non-whitespace
 * character. It reads text only; it never loads or executes project code.
 */
public final class GDScriptLexer {
  private static final int CANCEL_CHECK_INTERVAL = 10_000;
  private final Cursor cursor;
  private final BooleanSupplier cancelled;
  private final TokenScanner scanner;
  private final Indentation indentation = new Indentation();
  private final List<Token> tokens = new ArrayList<>();
  private int depth;
  private int lineIndent;
  private int pendingNewline = -1;
  private int scanned;
  private boolean lineHasCode;
  /** A '\' continuation waits for its next code token; comment-only lines in between do not end the line. */
  private boolean continuing;

  /** {@code cancelled} is polled every 10,000 tokens; a true answer aborts with a CancellationException. */
  public GDScriptLexer(String source, BooleanSupplier cancelled) {
    this.cursor = new Cursor(source);
    this.cancelled = cancelled;
    this.scanner = new TokenScanner(cursor, this::previousIsOperand);
  }

  public static List<Token> lex(String source) throws LexException { return new GDScriptLexer(source, () -> false).tokenize(); }

  public List<Token> tokenize() throws LexException {
    startLine();
    while (!cursor.atEnd()) {
      if (step() && !continuing) startLine();
    }
    if (lineHasCode) tokens.add(here(TokenKind.NEWLINE, 0));
    tokens.add(here(TokenKind.EOF, 0));
    return List.copyOf(tokens);
  }

  /** Consumes one element; returns true when it ended a physical line. */
  private boolean step() throws LexException {
    char c = cursor.peek();
    if (cursor.isLineBreak(0)) {
      endLine();
      return true;
    }
    if (c == ' ' || c == '\t') cursor.advance(1);
    else if (c == '#') trivia(TokenKind.COMMENT, cursor.restOfLineLength());
    else if (c == '\\' && cursor.isLineBreak(1)) continuation();
    else token();
    return false;
  }

  // Inside brackets only multi-line lambda bodies use indentation, so mixing is checked at depth 0 only.
  private void startLine() throws LexException {
    int width = indentation.read(cursor, depth == 0);
    if (width == Indentation.NOT_A_CODE_LINE) return;
    lineIndent = width;
    if (pendingNewline >= 0) tokens.set(pendingNewline, tokens.get(pendingNewline).withNextIndent(width));
    pendingNewline = -1;
  }

  private void endLine() {
    if (lineHasCode && !continuing) {
      pendingNewline = tokens.size();
      tokens.add(here(TokenKind.NEWLINE, cursor.lineBreakLength()));
    }
    cursor.newline();
    if (!continuing) lineHasCode = false;
  }

  private void continuation() {
    trivia(TokenKind.CONTINUATION, 1);
    cursor.newline();
    continuing = true;
  }

  private void trivia(TokenKind kind, int length) {
    tokens.add(here(kind, length));
    cursor.advance(length);
  }

  private void token() throws LexException {
    int start = cursor.position();
    int line = cursor.line();
    int column = cursor.column();
    TokenKind kind = scanner.scan();
    String text = cursor.text(start, cursor.position());
    tokens.add(new Token(kind, text, start, line, column, cursor.line(), cursor.column(), lineIndent, 0));
    if (kind == TokenKind.OPERATOR) trackDepth(text);
    lineHasCode = true;
    continuing = false;
    checkCancelled();
  }

  /** A single-line token of the given length starting at the cursor, which is not advanced. */
  private Token here(TokenKind kind, int length) {
    int start = cursor.position();
    int column = cursor.column();
    return new Token(kind, cursor.text(start, start + length), start, cursor.line(), column, cursor.line(), column + length, lineIndent, 0);
  }

  private void trackDepth(String operator) {
    if (Syntax.opensBracket(operator)) depth++;
    else if (Syntax.closesBracket(operator) && depth > 0) depth--;
  }

  private boolean previousIsOperand() {
    int previous = previousCode(tokens.size());
    if (previous < 0) return false;
    Token token = tokens.get(previous);
    if (token.kind() == TokenKind.IDENTIFIER && isContextualKeyword(token.text(), previous)) return false;
    return Syntax.endsOperand(token);
  }

  /**
   * Like Godot's parser, {@code match} starting a statement opens a match and {@code when} after a pattern opens a
   * guard; elsewhere both are names, so {@code x = match % 2} stays a modulo.
   */
  private boolean isContextualKeyword(String word, int index) {
    if (word.equals("match")) {
      int before = index - 1;
      while (before >= 0 && (tokens.get(before).kind() == TokenKind.COMMENT || tokens.get(before).kind() == TokenKind.CONTINUATION)) before--;
      return before < 0 || tokens.get(before).kind() == TokenKind.NEWLINE;
    }
    if (word.equals("when")) {
      int before = previousCode(index);
      return before >= 0 && Syntax.endsOperand(tokens.get(before));
    }
    return false;
  }

  /** The index of the last non-trivia token before {@code end}, or -1. */
  private int previousCode(int end) {
    for (int i = end - 1; i >= 0; i--) {
      if (!isTrivia(tokens.get(i))) return i;
    }
    return -1;
  }

  private boolean isTrivia(Token token) {
    return token.kind() == TokenKind.COMMENT || token.kind() == TokenKind.CONTINUATION
        || (token.kind() == TokenKind.NEWLINE && depth > 0);
  }

  private void checkCancelled() {
    if (++scanned % CANCEL_CHECK_INTERVAL == 0 && cancelled.getAsBoolean()) throw new CancellationException("GDScript lexing was cancelled");
  }
}
