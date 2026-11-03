package org.orebron.gdscript.checks;

import java.util.List;
import java.util.Optional;
import org.orebron.gdscript.metrics.CyclomaticComplexity;

/** Functions and accessors whose cyclomatic complexity exceeds the limit. */
public final class FunctionComplexityCheck implements Check {
  @Override public String key() { return "function-complexity"; }
  @Override public String name() { return "Functions should not be too complex"; }
  @Override public String severity() { return "MAJOR"; }
  @Override public List<String> tags() { return List.of("brain-overload"); }
  @Override public Optional<Limit> limit() { return Optional.of(new Limit("max", 10, "Maximum cyclomatic complexity of a function")); }

  @Override public String htmlDescription() {
    return "<p>A function's cyclomatic complexity should not exceed the limit (default 10). It is 1 plus one for each "
        + "<code>if</code>, <code>elif</code>, <code>for</code>, <code>while</code>, conditional expression, "
        + "<code>and</code>/<code>or</code>, and <code>match</code> branch other than an unguarded catch-all. "
        + "Lambdas are counted on their own and do not add to the function that contains them.</p>"
        + "<p>Split complex functions into smaller ones that are easier to test and change.</p>";
  }

  @Override public List<Violation> check(SourceFile file, int maximum) {
    return Functions.withBodies(file)
        .filter(function -> CyclomaticComplexity.ofBody(function) > maximum)
        .map(function -> Violation.at(this, function.token(), "Reduce the cyclomatic complexity of \"" + function.token().text()
            + "\" from " + CyclomaticComplexity.ofBody(function) + " to at most " + maximum + "."))
        .toList();
  }
}
