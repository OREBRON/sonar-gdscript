package org.orebron.gdscript.checks;

import java.util.List;
import java.util.Optional;
import org.orebron.gdscript.parser.SyntaxKind;
import org.orebron.gdscript.parser.SyntaxNode;

/** Functions with more parameters than the limit; a variadic {@code ...args} counts as one. */
public final class ParameterCountCheck implements Check {
  @Override public String key() { return "too-many-parameters"; }
  @Override public String name() { return "Functions should not have too many parameters"; }
  @Override public String severity() { return "MAJOR"; }
  @Override public List<String> tags() { return List.of("brain-overload"); }
  @Override public Optional<Limit> limit() { return Optional.of(new Limit("max", 7, "Maximum number of parameters of a function")); }

  @Override public String htmlDescription() {
    return "<p>A function should not have more parameters than the limit (default 7); a variadic "
        + "<code>...args</code> counts as one.</p><p>Long parameter lists are hard to read and to call correctly; "
        + "group related values into a class, a resource or a dictionary.</p>";
  }

  @Override public List<Violation> check(SourceFile file, int maximum) {
    return Functions.withBodies(file)
        .filter(function -> parameters(function) > maximum)
        .map(function -> Violation.at(this, function.token(), "This function has " + parameters(function)
            + " parameters, which is greater than the " + maximum + " authorized."))
        .toList();
  }

  private static long parameters(SyntaxNode function) {
    return function.children().stream().filter(child -> child.kind() == SyntaxKind.PARAMETER).count();
  }
}
