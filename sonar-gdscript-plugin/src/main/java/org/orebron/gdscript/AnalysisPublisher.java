package org.orebron.gdscript;

import org.orebron.gdscript.metrics.SizeMetrics;
import org.sonar.api.batch.fs.InputFile;
import org.sonar.api.batch.sensor.SensorContext;
import org.sonar.api.batch.sensor.error.NewAnalysisError;
import org.sonar.api.measures.CoreMetrics;
import org.sonar.api.measures.FileLinesContext;
import org.sonar.api.measures.FileLinesContextFactory;
import org.sonar.api.measures.Metric;

/** Saves one file's staged results. Sonar cannot roll back saved results, so a started file is always finished. */
final class AnalysisPublisher {
  private final SensorContext context;
  private final FileLinesContextFactory fileLinesContextFactory;

  AnalysisPublisher(SensorContext context, FileLinesContextFactory fileLinesContextFactory) {
    this.context = context;
    this.fileLinesContextFactory = fileLinesContextFactory;
  }

  void publish(FileAnalysis analysis) {
    InputFile file = analysis.file();
    save(file, GDScriptMetrics.INCOMPLETE_FILES, analysis.complete() ? 0 : 1);
    if (analysis.complete()) publishSize(file, analysis.size());
    else publishFailure(file, analysis.failure());
    analysis.violations().forEach(violation -> publishIssue(file, violation));
  }

  private void publishSize(InputFile file, SizeMetrics size) {
    save(file, CoreMetrics.NCLOC, size.ncloc());
    save(file, CoreMetrics.COMMENT_LINES, size.commentLineCount());
    FileLinesContext lines = fileLinesContextFactory.createFor(file);
    size.codeLines().forEach(line -> lines.setIntValue(CoreMetrics.NCLOC_DATA_KEY, line, 1));
    lines.save();
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

  private void publishIssue(InputFile file, LineLengthCheck.Violation violation) {
    var issue = context.newIssue().forRule(LineLengthCheck.RULE);
    issue.at(issue.newLocation().on(file).at(file.selectLine(violation.line())).message(violation.message())).save();
  }

  private void save(InputFile file, Metric<Integer> metric, int value) {
    context.<Integer>newMeasure().on(file).forMetric(metric).withValue(value).save();
  }
}
