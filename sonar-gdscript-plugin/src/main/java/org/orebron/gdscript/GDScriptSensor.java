package org.orebron.gdscript;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.stream.Collectors;
import org.orebron.gdscript.checks.ActiveCheck;
import org.orebron.gdscript.checks.Check;
import org.orebron.gdscript.checks.Checks;
import org.sonar.api.batch.fs.InputFile;
import org.sonar.api.batch.rule.ActiveRule;
import org.sonar.api.batch.sensor.Sensor;
import org.sonar.api.batch.sensor.SensorContext;
import org.sonar.api.batch.sensor.SensorDescriptor;
import org.sonar.api.measures.FileLinesContextFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Tokenizes and parses main GDScript files, publishes metrics and runs the active checks; nothing is executed. */
public final class GDScriptSensor implements Sensor {
  public static final String FAIL_ON_INCOMPLETE = "sonar.gdscript.failOnIncompleteAnalysis";
  private static final Logger LOG = LoggerFactory.getLogger(GDScriptSensor.class);
  private static final int REPORTED_FILES = 20;
  private final FileLinesContextFactory fileLinesContextFactory;

  public GDScriptSensor(FileLinesContextFactory fileLinesContextFactory) {
    this.fileLinesContextFactory = fileLinesContextFactory;
  }

  @Override public void describe(SensorDescriptor descriptor) {
    descriptor.name("GDScript sensor").onlyOnLanguage(GDScriptLanguage.KEY).onlyOnFileType(InputFile.Type.MAIN);
  }

  @Override public void execute(SensorContext context) {
    List<ActiveCheck> checks = activeChecks(context);
    var publisher = new AnalysisPublisher(context, fileLinesContextFactory);
    var incomplete = new ArrayList<FileAnalysis>();
    for (InputFile file : mainFiles(context)) {
      Optional<FileAnalysis> analysis = analyze(context, file, checks);
      if (analysis.isEmpty()) return;
      publisher.publish(analysis.get());
      if (!analysis.get().complete()) incomplete.add(analysis.get());
    }
    report(incomplete, context.config().getBoolean(FAIL_ON_INCOMPLETE).orElse(false));
  }

  /** Checks whose rule is active, with their limits; settings are validated only for active rules. */
  private static List<ActiveCheck> activeChecks(SensorContext context) {
    var checks = new ArrayList<ActiveCheck>();
    for (Check check : Checks.ALL) {
      ActiveRule rule = context.activeRules().find(check.ruleKey());
      if (rule != null) checks.add(new ActiveCheck(check, check.configuredLimit(rule, context.config())));
    }
    return checks;
  }

  /** Empty when the analysis is cancelled; nothing of the current file is published then. */
  private static Optional<FileAnalysis> analyze(SensorContext context, InputFile file, List<ActiveCheck> checks) {
    if (context.isCancelled()) return Optional.empty();
    try {
      var analysis = FileAnalysis.of(file, checks, context::isCancelled);
      return context.isCancelled() ? Optional.empty() : Optional.of(analysis);
    } catch (CancellationException e) {
      return Optional.empty();
    }
  }

  private static Iterable<InputFile> mainFiles(SensorContext context) {
    var predicates = context.fileSystem().predicates();
    return context.fileSystem().inputFiles(
        predicates.and(predicates.hasLanguage(GDScriptLanguage.KEY), predicates.hasType(InputFile.Type.MAIN)));
  }

  private static void report(List<FileAnalysis> incomplete, boolean failOnIncomplete) {
    incomplete.stream().limit(REPORTED_FILES)
        .forEach(analysis -> LOG.warn("Incomplete GDScript analysis of {}: {}", analysis.file(), analysis.failure().message()));
    if (incomplete.size() > REPORTED_FILES) LOG.warn("{} more GDScript files were analyzed incompletely", incomplete.size() - REPORTED_FILES);
    if (failOnIncomplete && !incomplete.isEmpty()) {
      throw new IllegalStateException(incomplete.size() + " GDScript files were analyzed incompletely and " + FAIL_ON_INCOMPLETE
          + " is true: " + incomplete.stream().limit(REPORTED_FILES).map(analysis -> analysis.file().toString()).collect(Collectors.joining(", ")));
    }
  }
}
