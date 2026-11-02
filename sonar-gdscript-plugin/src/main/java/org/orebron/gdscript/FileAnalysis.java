package org.orebron.gdscript;

import java.io.IOException;
import java.util.List;
import java.util.OptionalInt;
import java.util.function.BooleanSupplier;
import org.orebron.gdscript.lexer.GDScriptLexer;
import org.orebron.gdscript.lexer.LexException;
import org.orebron.gdscript.metrics.SizeMetrics;
import org.sonar.api.batch.fs.InputFile;

/** Everything one file contributes, computed before anything is published; {@code size} is null when incomplete. */
record FileAnalysis(InputFile file, SizeMetrics size, Failure failure, List<LineLengthCheck.Violation> violations) {
  /** Why a file could not be analyzed completely; line 0 means there is no location. */
  record Failure(String message, int line, int column) {}

  boolean complete() { return failure == null; }

  /** Throws CancellationException when {@code cancelled} turns true during lexing. */
  static FileAnalysis of(InputFile file, OptionalInt maximumLineLength, BooleanSupplier cancelled) {
    String content;
    try {
      content = file.contents();
    } catch (IOException e) {
      return new FileAnalysis(file, null, new Failure("Cannot read the file: " + e.getMessage(), 0, 0), List.of());
    }
    var violations = maximumLineLength.isPresent()
        ? LineLengthCheck.violations(content, maximumLineLength.getAsInt()) : List.<LineLengthCheck.Violation>of();
    try {
      return new FileAnalysis(file, SizeMetrics.of(new GDScriptLexer(content, cancelled).tokenize()), null, violations);
    } catch (LexException e) {
      return new FileAnalysis(file, null, new Failure(e.getMessage(), e.line(), e.column()), violations);
    }
  }
}
