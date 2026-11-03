package org.orebron.gdscript.checks;

import java.util.List;

/** A script with no code at all: only comments, blank lines or nothing. */
public final class EmptyFileCheck extends GodotWarningCheck {
  @Override public String key() { return "empty-file"; }
  @Override public String name() { return "Script files should not be empty"; }
  @Override public String severity() { return "MINOR"; }
  @Override String warning() { return "empty_file"; }

  @Override public String htmlDescription() {
    return "<p>A <code>.gd</code> file without code adds nothing to the project. Remove it, or add the code it was "
        + "meant to hold.</p>" + godotNote(warning());
  }

  @Override List<Violation> find(SourceFile file) {
    if (file.tokens().stream().anyMatch(TokenRanges::isSignificant)) return List.of();
    return List.of(Violation.onLine(this, 1, "Remove this empty script file, or add code to it."));
  }
}
