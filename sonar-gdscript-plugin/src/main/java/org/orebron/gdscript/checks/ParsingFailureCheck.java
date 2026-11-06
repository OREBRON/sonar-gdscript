package org.orebron.gdscript.checks;

import java.util.List;

/**
 * A file the analyzer could not read, tokenize or parse. The rules that need a syntax tree, the security rules among
 * them, are silent on such a file; this issue makes that visible to new-code quality gates and pull-request decoration,
 * which the {@code gdscript_incomplete_files} measure does not reach.
 */
public final class ParsingFailureCheck implements Check {
  @Override public String key() { return "parsing-failure"; }
  @Override public String name() { return "GDScript parser failure"; }
  @Override public String severity() { return "MAJOR"; }

  @Override public String htmlDescription() {
    return "<p>The analyzer could not read, tokenize or parse this file, for example because of a syntax error, syntax "
        + "newer than the supported Godot version, or an expression nested too deeply. Rules that need a syntax tree, "
        + "including every security rule, did not run on it, so the file may hide issues that are not reported.</p>"
        + "<p>Fix the reported problem so that the whole file is analyzed. The issue is on the line where the analysis "
        + "stopped, or on the file when there is no such line; <code>NOSONAR</code> does not drop it.</p>";
  }

  /** The failure is not in the source: the sensor reports it through {@link #violation} when the rule is active. */
  @Override public List<Violation> check(SourceFile file, int unused) { return List.of(); }

  /** On {@code line}, or on the whole file when it is 0. {@code reason} must already be sanitized: it is published as is. */
  public Violation violation(String reason, int line) {
    return new Violation(ruleKey(), line, -1, -1,
        "GDScript analysis is incomplete, so rules that need the syntax tree, including the security rules, did not run on this file: " + reason);
  }
}
