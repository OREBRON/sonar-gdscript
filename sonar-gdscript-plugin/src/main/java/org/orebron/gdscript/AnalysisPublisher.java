package org.orebron.gdscript;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.orebron.gdscript.checks.Violation;
import org.orebron.gdscript.lexer.Keywords;
import org.orebron.gdscript.lexer.Token;
import org.orebron.gdscript.lexer.TokenKind;
import org.orebron.gdscript.metrics.SizeMetrics;
import org.orebron.gdscript.metrics.StructureMetrics;
import org.sonar.api.batch.fs.InputFile;
import org.sonar.api.batch.sensor.SensorContext;
import org.sonar.api.batch.sensor.cpd.NewCpdTokens;
import org.sonar.api.batch.sensor.error.NewAnalysisError;
import org.sonar.api.batch.sensor.highlighting.NewHighlighting;
import org.sonar.api.batch.sensor.highlighting.TypeOfText;
import org.sonar.api.issue.NoSonarFilter;
import org.sonar.api.measures.CoreMetrics;
import org.sonar.api.measures.FileLinesContext;
import org.sonar.api.measures.FileLinesContextFactory;
import org.sonar.api.measures.Metric;

/** Saves one file's staged results. Sonar cannot roll back saved results, so a started file is always finished. */
final class AnalysisPublisher {
  private final SensorContext context;
  private final FileLinesContextFactory fileLinesContextFactory;
  private final NoSonarFilter noSonarFilter;

  AnalysisPublisher(SensorContext context, FileLinesContextFactory fileLinesContextFactory, NoSonarFilter noSonarFilter) {
    this.context = context;
    this.fileLinesContextFactory = fileLinesContextFactory;
    this.noSonarFilter = noSonarFilter;
  }

  void publish(FileAnalysis analysis) {
    InputFile file = analysis.file();
    save(file, GDScriptMetrics.INCOMPLETE_FILES, analysis.complete() ? 0 : 1);
    if (analysis.size() != null) publishSize(file, analysis.size());
    if (analysis.structure() != null) publishStructure(file, analysis.structure());
    if (!analysis.complete()) publishFailure(file, analysis.failure());
    if (!analysis.tokens().isEmpty()) publishTokens(file, analysis.tokens());
    analysis.violations().forEach(violation -> publishIssue(file, violation));
  }

  private void publishStructure(InputFile file, StructureMetrics structure) {
    save(file, CoreMetrics.CLASSES, structure.classes());
    save(file, CoreMetrics.FUNCTIONS, structure.functions());
    save(file, CoreMetrics.COMPLEXITY, structure.complexity());
  }

  private void publishSize(InputFile file, SizeMetrics size) {
    save(file, CoreMetrics.NCLOC, size.ncloc());
    save(file, CoreMetrics.COMMENT_LINES, size.commentLineCount());
    FileLinesContext lines = fileLinesContextFactory.createFor(file);
    size.codeLines().forEach(line -> lines.setIntValue(CoreMetrics.NCLOC_DATA_KEY, line, 1));
    lines.save();
  }

  /** NOSONAR lines are registered before any issue is saved, since the scanner filters issues as they are saved. */
  private void publishTokens(InputFile file, List<Token> tokens) {
    noSonarFilter.noSonarInFile(file, noSonarLines(tokens));
    NewHighlighting highlighting = context.newHighlighting().onFile(file);
    NewCpdTokens cpd = context.newCpdTokens().onFile(file);
    for (Token token : tokens) {
      TypeOfText type = highlighting(token);
      if (type != null) highlighting.highlight(token.line(), token.column(), token.endLine(), token.endColumn(), type);
      String image = cpdImage(token);
      if (image != null) cpd.addToken(token.line(), token.column(), token.endLine(), token.endColumn(), image);
    }
    highlighting.save();
    cpd.save();
  }

  /** Lines with a comment containing NOSONAR; the scanner drops the issues of every rule there. */
  private static Set<Integer> noSonarLines(List<Token> tokens) {
    return tokens.stream().filter(token -> token.kind() == TokenKind.COMMENT && token.text().contains("NOSONAR"))
        .map(Token::line).collect(Collectors.toSet());
  }

  private static TypeOfText highlighting(Token token) {
    return switch (token.kind()) {
      case COMMENT -> token.text().startsWith("##") ? TypeOfText.STRUCTURED_COMMENT : TypeOfText.COMMENT;
      case STRING, NODE_PATH -> TypeOfText.STRING;
      case NUMBER -> TypeOfText.CONSTANT;
      case ANNOTATION -> TypeOfText.ANNOTATION;
      case IDENTIFIER -> Keywords.isReserved(token.text()) ? TypeOfText.KEYWORD : null;
      default -> null;
    };
  }

  /** Literals are normalized within their kind (metrics plan, "Duplication"); trivia is not a CPD token. */
  private static String cpdImage(Token token) {
    return switch (token.kind()) {
      case COMMENT, CONTINUATION, NEWLINE, EOF -> null;
      case NUMBER -> "0";
      case STRING -> switch (token.text().charAt(0)) {
        case '&' -> "&\"\"";
        case '^' -> "^\"\"";
        default -> "\"\"";
      };
      default -> token.text();
    };
  }

  private void publishFailure(InputFile file, FileAnalysis.Failure failure) {
    NewAnalysisError error = context.newAnalysisError().onFile(file).message(failure.message());
    if (failure.line() > 0) locate(error, file, failure);
    error.save();
  }

  private static void locate(NewAnalysisError error, InputFile file, FileAnalysis.Failure failure) {
    try {
      error.at(file.newPointer(failure.line(), failure.column()));
    } catch (IllegalArgumentException e) {
      // The position is outside the indexed text: the error is still saved, without a location.
    }
  }

  private void publishIssue(InputFile file, Violation violation) {
    var issue = context.newIssue().forRule(violation.rule());
    var range = violation.column() < 0 ? file.selectLine(violation.line())
        : file.newRange(violation.line(), violation.column(), violation.line(), violation.endColumn());
    issue.at(issue.newLocation().on(file).at(range).message(violation.message())).save();
  }

  private void save(InputFile file, Metric<Integer> metric, int value) {
    context.<Integer>newMeasure().on(file).forMetric(metric).withValue(value).save();
  }
}
