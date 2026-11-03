package org.orebron.gdscript.checks;

import java.util.List;
import java.util.Optional;
import org.orebron.gdscript.parser.SyntaxNode;

/** Functions and accessors with more lines of code than the limit; comment and blank lines do not count. */
public final class FunctionLengthCheck implements Check {
  @Override public String key() { return "function-length"; }
  @Override public String name() { return "Functions should not have too many lines of code"; }
  @Override public String severity() { return "MAJOR"; }
  @Override public List<String> tags() { return List.of("brain-overload"); }
  @Override public Optional<Limit> limit() { return Optional.of(new Limit("max", 75, "Maximum lines of code in a function")); }

  @Override public String htmlDescription() {
    return "<p>A function should not have more lines of code than the limit (default 75), counted from its "
        + "<code>func</code> line to its last statement. Comment and blank lines do not count.</p>"
        + "<p>Long functions do several things at once; split them into smaller ones.</p>";
  }

  @Override public List<Violation> check(SourceFile file, int maximum) {
    return Functions.withBodies(file)
        .filter(function -> codeLines(file, function) > maximum)
        .map(function -> Violation.at(this, function.token(), "This function has " + codeLines(file, function)
            + " lines of code, which is greater than the " + maximum + " authorized. Split it into smaller functions."))
        .toList();
  }

  private static int codeLines(SourceFile file, SyntaxNode function) {
    return file.size().codeLines().subSet(function.token().line(), function.last().endLine() + 1).size();
  }
}
