package org.orebron.gdscript;

import java.io.IOException;
import java.util.List;
import java.util.function.BooleanSupplier;
import org.orebron.gdscript.checks.ActiveCheck;
import org.orebron.gdscript.checks.SourceFile;
import org.orebron.gdscript.checks.Violation;
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
 * ANALYZED (no failure), LEXICAL_ONLY (parser failure: size but no structure) or FAILED (neither). Checks still
 * run on whatever could be read, so a text rule such as line-length reports on a file that fails to tokenize.
 * Tokens are empty when the file could not be read or tokenized.
 */
record FileAnalysis(InputFile file, List<Token> tokens, SizeMetrics size, StructureMetrics structure, Failure failure, List<Violation> violations) {
  /** Why a file could not be analyzed completely; line 0 means there is no location. */
  record Failure(String message, int line, int column) {
    static Failure of(SourceException e) { return new Failure(e.getMessage(), e.line(), e.column()); }
  }

  boolean complete() { return failure == null; }

  /** Throws CancellationException when {@code cancelled} turns true during lexing or parsing. */
  static FileAnalysis of(InputFile file, List<ActiveCheck> checks, BooleanSupplier cancelled) {
    String content;
    try {
      content = file.contents();
    } catch (IOException e) {
      return new FileAnalysis(file, List.of(), null, null, new Failure("Cannot read the file: " + e.getMessage(), 0, 0), List.of());
    }
    try {
      return analyzeTokens(file, content, new GDScriptLexer(content, cancelled).tokenize(), checks, cancelled);
    } catch (LexException e) {
      return new FileAnalysis(file, List.of(), null, null, Failure.of(e), run(checks, new SourceFile(content, List.of(), null, null)));
    }
  }

  private static FileAnalysis analyzeTokens(InputFile file, String content, List<Token> tokens, List<ActiveCheck> checks,
      BooleanSupplier cancelled) {
    var size = SizeMetrics.of(tokens);
    try {
      var tree = GDScriptParser.parse(tokens, cancelled);
      return new FileAnalysis(file, tokens, size, StructureMetrics.of(tree), null, run(checks, new SourceFile(content, tokens, size, tree)));
    } catch (ParseException e) {
      return new FileAnalysis(file, tokens, size, null, Failure.of(e), run(checks, new SourceFile(content, tokens, size, null)));
    }
  }

  private static List<Violation> run(List<ActiveCheck> checks, SourceFile source) {
    return checks.stream().flatMap(check -> check.run(source).stream()).toList();
  }
}
