package org.orebron.gdscript.checks;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.orebron.gdscript.lexer.Token;
import org.orebron.gdscript.lexer.TokenKind;
import org.orebron.gdscript.parser.SyntaxKind;
import org.orebron.gdscript.parser.SyntaxNode;

/**
 * Functions and if, elif, else, for and while blocks whose body is only {@code pass}, with no comment saying why.
 * Match branches, lambdas and accessors are left out: there {@code pass} is the usual way to ignore a case.
 */
public final class EmptyBlockCheck implements Check {
  private static final Set<SyntaxKind> BLOCKS = EnumSet.of(SyntaxKind.FUNCTION, SyntaxKind.IF, SyntaxKind.ELIF,
      SyntaxKind.ELSE, SyntaxKind.FOR, SyntaxKind.WHILE);

  @Override public String key() { return "empty-block"; }
  @Override public String name() { return "Functions and blocks should not be empty"; }
  @Override public String severity() { return "MAJOR"; }
  @Override public List<String> tags() { return List.of("suspicious"); }

  @Override public String htmlDescription() {
    return "<p>A function or an <code>if</code>, <code>elif</code>, <code>else</code>, <code>for</code> or "
        + "<code>while</code> block that contains only <code>pass</code> is either unfinished or unnecessary. "
        + "Complete it, remove it, or add a comment inside it that explains why it is empty.</p>";
  }

  @Override public List<Violation> check(SourceFile file, int unused) {
    if (file.tree() == null) return List.of();
    return file.tree().descendantsAndSelf().filter(node -> BLOCKS.contains(node.kind()))
        .filter(node -> isEmpty(file.tokens(), node))
        .map(node -> Violation.at(this, node.token(), node.kind() == SyntaxKind.FUNCTION
            ? "Add a nested comment explaining why this function is empty, or complete the implementation."
            : "Either remove or fill this block of code."))
        .toList();
  }

  /** Only {@code pass}, and no comment in the body or after the {@code pass} on its line. */
  private static boolean isEmpty(List<Token> tokens, SyntaxNode node) {
    List<Token> body = TokenRanges.body(tokens, node);
    List<Token> statements = TokenRanges.significant(body);
    if (statements.size() != 1 || !TokenRanges.is(statements.get(0), "pass")) return false;
    int after = TokenRanges.indexOf(tokens, node.last()) + 1;
    boolean commentAfter = after < tokens.size() && tokens.get(after).kind() == TokenKind.COMMENT;
    return !commentAfter && body.stream().noneMatch(token -> token.kind() == TokenKind.COMMENT);
  }
}
