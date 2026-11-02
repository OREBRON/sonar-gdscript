package org.orebron.gdscript.metrics;

import java.util.Collections;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;
import org.orebron.gdscript.lexer.Token;
import org.orebron.gdscript.lexer.TokenKind;

/** Code and comment lines as defined in docs/metrics-plan.md, computed from tokens only. */
public record SizeMetrics(SortedSet<Integer> codeLines, SortedSet<Integer> commentLines) {
  public static SizeMetrics of(List<Token> tokens) {
    var code = new TreeSet<Integer>();
    var comments = new TreeSet<Integer>();
    for (Token token : tokens) {
      if (token.kind() == TokenKind.COMMENT) addIfSignificant(comments, token);
      else if (isCode(token)) addLines(code, token);
    }
    return new SizeMetrics(Collections.unmodifiableSortedSet(code), Collections.unmodifiableSortedSet(comments));
  }

  public int ncloc() { return codeLines.size(); }
  public int commentLineCount() { return commentLines.size(); }

  /** "#" and "# ----" are not comment lines; a comment needs a letter or digit. */
  private static void addIfSignificant(SortedSet<Integer> lines, Token comment) {
    if (comment.text().codePoints().anyMatch(Character::isLetterOrDigit)) lines.add(comment.line());
  }

  private static boolean isCode(Token token) {
    return token.kind() != TokenKind.NEWLINE && token.kind() != TokenKind.CONTINUATION && token.kind() != TokenKind.EOF;
  }

  /** Every line a multi-line string spans is code, blank ones included. */
  private static void addLines(SortedSet<Integer> lines, Token token) {
    for (int line = token.line(); line <= token.endLine(); line++) lines.add(line);
  }
}
