package org.orebron.gdscript.checks;

import java.util.Comparator;
import java.util.List;

/**
 * A rule that reports what one of Godot's GDScript warnings reports, without the engine's type information. Like
 * Godot, it stays silent on lines that {@code @warning_ignore} covers for its warning, so code that already silences
 * the editor warning stays clean here as well.
 */
abstract class GodotWarningCheck implements Check {
  /** The warning's name as {@code @warning_ignore} spells it, such as {@code unused_variable}. */
  abstract String warning();

  abstract List<Violation> find(SourceFile file);

  /** The warning a violation stands for, when a rule reports more than one. */
  String warningOf(Violation violation) { return warning(); }

  @Override public final List<Violation> check(SourceFile file, int unused) {
    if (file.tokens().isEmpty()) return List.of();
    var ignores = WarningIgnores.of(file.tokens());
    return find(file).stream().filter(violation -> !ignores.ignores(warningOf(violation), violation.line()))
        .sorted(Comparator.comparingInt(Violation::line).thenComparingInt(Violation::column)).toList();
  }

  @Override public List<String> tags() { return List.of("godot-warning"); }

  /** Where Godot documents the warning. */
  static String godotNote(String warning) {
    return "<p>This is Godot's <code>" + warning.toUpperCase() + "</code> warning; <code>@warning_ignore(\"" + warning
        + "\")</code> silences it here as in the editor.</p>";
  }
}
