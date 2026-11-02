package org.orebron.gdscript;

import java.io.IOException;
import java.util.List;
import java.util.OptionalInt;
import java.util.function.BooleanSupplier;
import org.orebron.gdscript.lexer.GDScriptLexer;
import org.orebron.gdscript.lexer.LexException;
import org.orebron.gdscript.lexer.SourceException;
import org.orebron.gdscript.lexer.Token;
import org.orebron.gdscript.metrics.SizeMetrics;
import org.orebron.gdscript.metrics.StructureMetrics;
import org.orebron.gdscript.parser.GDScriptParser;
import org.orebron.gdscript.parser.ParseException;
import org.sonar.api.batch.fs.InputFile;

/**
 * Everything one file contributes, computed before anything is published. The outcome follows the metrics plan:
 * ANALYZED (no failure), LEXICAL_ONLY (parser failure: size but no structure) or FAILED (neither).
 */
record FileAnalysis(InputFile file, SizeMetrics size, StructureMetrics structure, Failure failure,
    List<LineLengthCheck.Violation> violations) {
  /** Why a file could not be analyzed completely; line 0 means there is no location. */
  record Failure(String message, int line, int column) {
    static Failure of(SourceException e) { return new Failure(e.getMessage(), e.line(), e.column()); }
  }

  boolean complete() { return failure == null; }

  /** Throws CancellationException when {@code cancelled} turns true during lexing or parsing. */
  static FileAnalysis of(InputFile file, OptionalInt maximumLineLength, BooleanSupplier cancelled) {
    String content;
    try {
      content = file.contents();
    } catch (IOException e) {
      return new FileAnalysis(file, null, null, new Failure("Cannot read the file: " + e.getMessage(), 0, 0), List.of());
    }
    var violations = maximumLineLength.isPresent()
        ? LineLengthCheck.violations(content, maximumLineLength.getAsInt()) : List.<LineLengthCheck.Violation>of();
    try {
      return analyzeTokens(file, new GDScriptLexer(content, cancelled).tokenize(), violations, cancelled);
    } catch (LexException e) {
      return new FileAnalysis(file, null, null, Failure.of(e), violations);
    }
  }

  private static FileAnalysis analyzeTokens(InputFile file, List<Token> tokens, List<LineLengthCheck.Violation> violations,
      BooleanSupplier cancelled) {
    var size = SizeMetrics.of(tokens);
    try {
      return new FileAnalysis(file, size, StructureMetrics.of(GDScriptParser.parse(tokens, cancelled)), null, violations);
    } catch (ParseException e) {
      return new FileAnalysis(file, size, null, Failure.of(e), violations);
    }
  }
}
