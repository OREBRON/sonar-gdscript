package org.orebron.gdscript.checks;

import java.util.List;
import org.orebron.gdscript.lexer.Token;
import org.orebron.gdscript.metrics.SizeMetrics;
import org.orebron.gdscript.parser.SyntaxNode;

/**
 * What checks may read. Tokens are empty and size is null when lexing failed; the tree is null when parsing failed.
 * The scopes, symbols and calls are built once, on first use, and shared by all checks of the file.
 */
public final class SourceFile {
  private final String content;
  private final List<Token> tokens;
  private final SizeMetrics size;
  private final SyntaxNode tree;
  private Scopes scopes;
  private Symbols symbols;
  private Calls calls;

  public SourceFile(String content, List<Token> tokens, SizeMetrics size, SyntaxNode tree) {
    this.content = content;
    this.tokens = tokens;
    this.size = size;
    this.tree = tree;
  }

  public String content() { return content; }
  public List<Token> tokens() { return tokens; }
  public SizeMetrics size() { return size; }
  public SyntaxNode tree() { return tree; }

  /** Null when the file did not parse. */
  Scopes scopes() {
    if (scopes == null && tree != null) scopes = new Scopes(this);
    return scopes;
  }

  /** Null when the file did not parse. */
  Symbols symbols() {
    if (symbols == null && tree != null) symbols = new Symbols(scopes());
    return symbols;
  }

  /** Null when the file did not parse. */
  Calls calls() {
    if (calls == null && tree != null) calls = new Calls(symbols());
    return calls;
  }
}
