package org.orebron.gdscript.checks;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.orebron.gdscript.lexer.Token;
import org.orebron.gdscript.lexer.TokenKind;
import org.orebron.gdscript.parser.SyntaxKind;
import org.orebron.gdscript.parser.SyntaxNode;

/**
 * Where each token sits in the tree: the innermost block around it, and that block's function and class. Blocks are
 * the nodes that open a scope in GDScript: the script, classes, functions, accessors, lambdas and statement bodies.
 */
final class Scopes {
  static final Set<SyntaxKind> BLOCKS = EnumSet.of(SyntaxKind.SCRIPT, SyntaxKind.CLASS, SyntaxKind.FUNCTION,
      SyntaxKind.ACCESSOR, SyntaxKind.LAMBDA, SyntaxKind.IF, SyntaxKind.ELIF, SyntaxKind.ELSE, SyntaxKind.FOR,
      SyntaxKind.WHILE, SyntaxKind.MATCH_BRANCH, SyntaxKind.CATCH_ALL_BRANCH);
  static final Set<SyntaxKind> FUNCTIONS = EnumSet.of(SyntaxKind.FUNCTION, SyntaxKind.ACCESSOR, SyntaxKind.LAMBDA);
  static final Set<SyntaxKind> CLASSES = EnumSet.of(SyntaxKind.SCRIPT, SyntaxKind.CLASS);

  final List<Token> tokens;
  final SyntaxNode root;
  private final SyntaxNode[] innermost;
  private final Map<SyntaxNode, SyntaxNode> parents = new IdentityHashMap<>();

  /** Needs a parsed file. */
  Scopes(SourceFile file) {
    tokens = file.tokens();
    root = file.tree();
    innermost = new SyntaxNode[tokens.size()];
    Arrays.fill(innermost, root);
    visit(root);
  }

  private void visit(SyntaxNode node) {
    for (SyntaxNode child : node.children()) {
      parents.put(child, node);
      if (BLOCKS.contains(child.kind())) {
        int end = TokenRanges.indexOf(tokens, child.last());
        for (int i = TokenRanges.indexOf(tokens, child.token()); i <= end; i++) innermost[i] = child;
      }
      visit(child);
    }
  }

  int indexOf(Token token) { return TokenRanges.indexOf(tokens, token); }

  /** The innermost block that contains the token. */
  SyntaxNode blockAt(int index) { return innermost[index]; }

  SyntaxNode parent(SyntaxNode node) { return parents.get(node); }

  /** The function, accessor or lambda the node is in, itself included; null at class level. */
  SyntaxNode functionOf(SyntaxNode node) {
    for (SyntaxNode at = node; at != null; at = parents.get(at)) {
      if (FUNCTIONS.contains(at.kind())) return at;
      if (CLASSES.contains(at.kind())) return null;
    }
    return null;
  }

  /** The script or inner class the node is in, itself included. */
  SyntaxNode classOf(SyntaxNode node) {
    for (SyntaxNode at = node; at != null; at = parents.get(at)) {
      if (CLASSES.contains(at.kind())) return at;
    }
    return root;
  }

  boolean contains(SyntaxNode node, Token token) {
    if (node == root) return true;
    return node.token().offset() <= token.offset() && token.offset() <= node.last().offset();
  }

  /** The index after the end of the statement or header that starts at {@code index}: a line break or ';' outside brackets. */
  int statementEnd(int index) {
    int depth = 0;
    for (int i = index; i < tokens.size(); i++) {
      Token token = tokens.get(i);
      if (token.kind() == TokenKind.EOF || (depth == 0 && (token.kind() == TokenKind.NEWLINE || TokenRanges.is(token, ";")))) return i;
      if (token.kind() != TokenKind.OPERATOR) continue;
      switch (token.text()) {
        case "(", "[", "{" -> depth++;
        case ")", "]", "}" -> depth = Math.max(0, depth - 1);
        default -> { }
      }
    }
    return tokens.size();
  }

  /** The previous or next significant token, or null. */
  Token significantBefore(int index) {
    for (int i = index - 1; i >= 0; i--) if (TokenRanges.isSignificant(tokens.get(i))) return tokens.get(i);
    return null;
  }

  Token significantAfter(int index) {
    for (int i = index + 1; i < tokens.size(); i++) if (TokenRanges.isSignificant(tokens.get(i))) return tokens.get(i);
    return null;
  }

  /**
   * The indexes of the statements that start directly in a block's body: lines at the body's indentation outside
   * brackets, and statements after ';'. Annotations are skipped, and elif and else continue the if before them.
   */
  List<Integer> statements(SyntaxNode block) {
    var starts = new ArrayList<Integer>();
    List<Token> body = TokenRanges.significant(TokenRanges.body(tokens, block));
    if (body.isEmpty()) return starts;
    int first = indexOf(body.get(0));
    int end = indexOf(block.last());
    boolean inline = !startsLine(first);
    int indent = tokens.get(first).lineIndent();
    int depth = 0;
    int semicolonLine = -1;
    for (int i = first; i <= end; i++) {
      Token token = tokens.get(i);
      if (!TokenRanges.isSignificant(token)) continue;
      if (TokenRanges.is(token, ";")) {
        if (depth == 0) semicolonLine = token.line();
        continue;
      }
      // A ';' separates statements on its own line only; a trailing one ends the line's statement.
      boolean newLine = !inline && startsLine(i) && !continued(i) && token.lineIndent() == indent;
      boolean start = depth == 0 && (token.line() == semicolonLine || i == first || newLine);
      semicolonLine = -1;
      if (start && token.kind() == TokenKind.ANNOTATION) {
        i = skipAnnotations(i, end);
        if (i <= end && tokens.get(i).line() == token.line()) starts.add(i);
        else i--;
        continue;
      }
      if (start && !TokenRanges.is(token, "elif") && !TokenRanges.is(token, "else")) starts.add(i);
      if (token.kind() != TokenKind.OPERATOR) continue;
      switch (token.text()) {
        case "(", "[", "{" -> depth++;
        case ")", "]", "}" -> depth = Math.max(0, depth - 1);
        default -> { }
      }
    }
    return starts;
  }

  /** The index of the first significant token after the annotations and their arguments at {@code index}. */
  private int skipAnnotations(int index, int end) {
    int i = index;
    while (i <= end && (!TokenRanges.isSignificant(tokens.get(i)) || tokens.get(i).kind() == TokenKind.ANNOTATION)) {
      if (tokens.get(i).kind() == TokenKind.ANNOTATION && i + 1 <= end && TokenRanges.is(tokens.get(i + 1), "(")) {
        int depth = 0;
        for (i = i + 1; i <= end; i++) {
          if (TokenRanges.is(tokens.get(i), "(")) depth++;
          else if (TokenRanges.is(tokens.get(i), ")") && --depth == 0) break;
        }
      }
      i++;
    }
    return i;
  }

  /** Whether a '\\' continuation joins the token's line to the previous one; such lines keep the first line's indent. */
  private boolean continued(int index) {
    for (int i = index - 1; i >= 0 && !TokenRanges.isSignificant(tokens.get(i)); i--) {
      if (tokens.get(i).kind() == TokenKind.CONTINUATION) return true;
    }
    return false;
  }

  /** Whether the token is the first significant one on its line. */
  boolean startsLine(int index) {
    Token before = significantBefore(index);
    return before == null || before.endLine() < tokens.get(index).line();
  }
}
