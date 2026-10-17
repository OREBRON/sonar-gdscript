package org.orebron.gdscript;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import org.sonar.api.batch.fs.InputFile;
import org.sonar.api.batch.sensor.Sensor;
import org.sonar.api.batch.sensor.SensorContext;
import org.sonar.api.batch.sensor.SensorDescriptor;
import org.sonar.api.rule.RuleKey;

/** Text-only analysis; no syntax, security, coverage or metrics are inferred. */
public final class GDScriptSensor implements Sensor {
  public static final String MAX_LINE_LENGTH = "sonar.gdscript.maxLineLength";
  public static final RuleKey RULE = RuleKey.of("gdscript", "line-length");

  @Override public void describe(SensorDescriptor descriptor) {
    descriptor.name("GDScript line-length sensor").onlyOnLanguage("gdscript").onlyOnFileType(InputFile.Type.MAIN);
  }

  @Override public void execute(SensorContext context) {
    if (context.activeRules().find(RULE) == null) return;
    int maximum;
    try {
      maximum = Integer.parseInt(context.config().get(MAX_LINE_LENGTH).orElse("120"));
    } catch (NumberFormatException e) {
      throw new IllegalArgumentException(MAX_LINE_LENGTH + " must be a positive integer", e);
    }
    if (maximum <= 0) throw new IllegalArgumentException(MAX_LINE_LENGTH + " must be a positive integer");
    var fs = context.fileSystem();
    var predicate = fs.predicates().and(fs.predicates().hasLanguage("gdscript"), fs.predicates().hasType(InputFile.Type.MAIN));
    for (InputFile file : fs.inputFiles(predicate)) {
      if (context.isCancelled()) return;
      try (var reader = new BufferedReader(new InputStreamReader(file.inputStream(), file.charset()))) {
        String line;
        int number = 0;
        while ((line = reader.readLine()) != null) {
          number++;
          int length = line.codePointCount(0, line.length());
          if (length > maximum) {
            var issue = context.newIssue().forRule(RULE);
            issue.at(issue.newLocation().on(file).at(file.selectLine(number))
                .message("Shorten this line from " + length + " to at most " + maximum + " characters.")).save();
          }
        }
      } catch (IOException e) {
        throw new IllegalStateException("Cannot read GDScript file " + file.uri(), e);
      }
    }
  }
}
