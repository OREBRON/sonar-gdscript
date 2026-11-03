package org.orebron.gdscript.checks;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.orebron.gdscript.lexer.Token;
import org.orebron.gdscript.parser.SyntaxKind;
import org.orebron.gdscript.parser.SyntaxNode;

/** Functions and accessors with more return statements than the limit; returns inside lambdas belong to the lambda. */
public final class TooManyReturnsCheck implements Check {
  @Override public String key() { return "too-many-returns"; }
  @Override public String name() { return "Functions should not contain too many return statements"; }
  @Override public String severity() { return "MAJOR"; }
  @Override public List<String> tags() { return List.of("brain-overload"); }
  /** Early returns are idiomatic in GDScript, so the rule is opt-in, as SonarSource ships it for other languages. */
  @Override public boolean activeByDefault() { return false; }
  @Override public Optional<Limit> limit() { return Optional.of(new Limit("max", 3, "Maximum number of return statements")); }

  @Override public String htmlDescription() {
    return "<p>Many exit points make a function hard to follow: each one is a path to keep in mind when reading or "
        + "changing it. Keep the number of <code>return</code> statements under the limit (default 3), for example "
        + "by extracting the checks into smaller functions.</p>";
  }

  @Override public List<Violation> check(SourceFile file, int maximum) {
    return Functions.withBodies(file).flatMap(function -> {
      long returns = returns(file.tokens(), function);
      if (returns <= maximum) return Stream.empty();
      return Stream.of(Violation.at(this, function.token(), "This function has " + returns + " returns, which is more than the "
          + maximum + " allowed."));
    }).toList();
  }

  private static long returns(List<Token> tokens, SyntaxNode function) {
    List<SyntaxNode> lambdas = function.descendantsAndSelf().filter(node -> node.kind() == SyntaxKind.LAMBDA).toList();
    return TokenRanges.of(tokens, function).stream().filter(token -> TokenRanges.is(token, "return"))
        .filter(token -> lambdas.stream().noneMatch(lambda -> lambda.token().offset() <= token.offset() && token.offset() <= lambda.last().offset()))
        .count();
  }
}
