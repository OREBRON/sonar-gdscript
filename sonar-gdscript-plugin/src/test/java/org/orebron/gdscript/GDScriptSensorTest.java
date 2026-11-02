package org.orebron.gdscript;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.orebron.gdscript.metrics.SizeFixture;
import org.sonar.api.batch.fs.InputFile;
import com.sonarsource.scanner.engine.sensor.test.fixtures.TestInputFileBuilder;
import org.sonar.api.measures.CoreMetrics;
import org.sonar.api.measures.FileLinesContext;
import org.sonar.api.measures.FileLinesContextFactory;
import org.sonar.scanner.plugin.api.impl.rule.ActiveRulesBuilder;
import com.sonarsource.scanner.engine.sensor.test.fixtures.SensorContextTester;
import org.sonar.api.rule.RuleKey;
import static org.junit.jupiter.api.Assertions.*;

class GDScriptSensorTest {
  private static final String INCOMPLETE = "gdscript_incomplete_files";
  @TempDir Path directory;
  private final Map<String, Map<Integer, Integer>> nclocData = new HashMap<>();
  private final FileLinesContextFactory fileLines = file -> new RecordingLines(file.key());

  private SensorContextTester context(boolean active) {
    var context = SensorContextTester.create(directory);
    var rules = new ActiveRulesBuilder();
    if (active) rules.addRule(new org.sonar.scanner.plugin.api.impl.rule.NewActiveRule.Builder()
        .setRuleKey(RuleKey.of("gdscript", "line-length")).setLanguage("gdscript").setSeverity("MINOR").build());
    context.setActiveRules(rules.build());
    return context;
  }

  private SensorContextTester context(String content, String language, InputFile.Type type, boolean active) {
    var context = context(active);
    add(context, "sample.gd", content, language, type);
    return context;
  }

  private static void add(SensorContextTester context, String name, String content, String language, InputFile.Type type) {
    context.fileSystem().add(TestInputFileBuilder.create("project", name).setLanguage(language)
        .setType(type).setCharset(StandardCharsets.UTF_8).setContents(content).build());
  }

  private void execute(SensorContextTester context) { new GDScriptSensor(fileLines).execute(context); }

  private static Integer measure(SensorContextTester context, String file, String metric) {
    var measure = context.<Integer>measure("project:" + file, metric);
    return measure == null ? null : measure.value();
  }

  @Test void acceptsBoundaryEmptyAndUnicodeLines() {
    for (String text : new String[]{"", "x".repeat(120), "😀".repeat(120), "\r\n\n"}) {
      var context = context(text, "gdscript", InputFile.Type.MAIN, true);
      execute(context);
      assertTrue(context.allIssues().isEmpty());
    }
  }

  @Test void respectsConfiguredThresholdAndCrLf() {
    var context = context("12345\r\n123456\r\n", "gdscript", InputFile.Type.MAIN, true);
    context.settings().setProperty("sonar.gdscript.maxLineLength", "5");
    execute(context);
    assertEquals(1, context.allIssues().size());
    assertEquals(2, context.allIssues().iterator().next().primaryLocation().textRange().start().line());
  }

  @Test void skipsInactiveRulesOtherLanguagesAndTestFiles() {
    var inactive = context("x".repeat(121), "gdscript", InputFile.Type.MAIN, false);
    var other = context("x".repeat(121), "other", InputFile.Type.MAIN, true);
    var test = context("x".repeat(121), "gdscript", InputFile.Type.TEST, true);
    for (var context : List.of(inactive, other, test)) {
      execute(context);
      assertTrue(context.allIssues().isEmpty());
    }
    assertTrue(other.measures("project:sample.gd").isEmpty());
    assertTrue(test.measures("project:sample.gd").isEmpty());
  }

  @Test void rejectsInvalidConfiguration() {
    for (String value : new String[]{"0", "-1", "abc", "2147483648"}) {
      var context = context("short", "gdscript", InputFile.Type.MAIN, true);
      context.settings().setProperty("sonar.gdscript.maxLineLength", value);
      assertThrows(IllegalArgumentException.class, () -> execute(context));
    }
  }

  @Test void reportsActualOffendingLine() throws IOException {
    String content;
    try (var fixture = getClass().getResourceAsStream("/fixtures/long-line.gd")) {
      assertNotNull(fixture);
      content = new String(fixture.readAllBytes(), StandardCharsets.UTF_8);
    }
    var context = context(content, "gdscript", InputFile.Type.MAIN, true);
    execute(context);
    var issue = context.allIssues().iterator().next();
    assertEquals(1, context.allIssues().size());
    assertEquals(RuleKey.of("gdscript", "line-length"), issue.ruleKey());
    assertEquals(2, issue.primaryLocation().textRange().start().line());
    assertEquals(2, issue.primaryLocation().textRange().end().line());
    assertEquals("Shorten this line from " + content.lines().toList().get(1).length()
        + " to at most 120 characters.", issue.primaryLocation().message());
    assertEquals(0, measure(context, "sample.gd", INCOMPLETE));
  }

  @Test void publishesMetricsWhetherOrNotTheRuleIsActive() {
    var context = context(SizeFixture.content(), "gdscript", InputFile.Type.MAIN, false);
    execute(context);
    assertEquals(SizeFixture.CODE_LINES.size(), measure(context, "sample.gd", CoreMetrics.NCLOC_KEY));
    assertEquals(SizeFixture.COMMENT_LINES.size(), measure(context, "sample.gd", CoreMetrics.COMMENT_LINES_KEY));
    assertEquals(List.of(1, 1, 1), List.of(measure(context, "sample.gd", CoreMetrics.CLASSES_KEY),
        measure(context, "sample.gd", CoreMetrics.FUNCTIONS_KEY), measure(context, "sample.gd", CoreMetrics.COMPLEXITY_KEY)));
    assertEquals(0, measure(context, "sample.gd", INCOMPLETE));
    assertEquals(SizeFixture.CODE_LINES, nclocData.get("project:sample.gd").keySet());
    assertEquals(Set.of(1), Set.copyOf(nclocData.get("project:sample.gd").values()));
    assertTrue(context.allIssues().isEmpty());
  }

  @Test void publishesExplicitZerosForAnEmptyFile() {
    var context = context("", "gdscript", InputFile.Type.MAIN, false);
    execute(context);
    assertEquals(0, measure(context, "sample.gd", CoreMetrics.NCLOC_KEY));
    assertEquals(0, measure(context, "sample.gd", CoreMetrics.COMMENT_LINES_KEY));
    assertEquals(List.of(1, 0, 0), List.of(measure(context, "sample.gd", CoreMetrics.CLASSES_KEY),
        measure(context, "sample.gd", CoreMetrics.FUNCTIONS_KEY), measure(context, "sample.gd", CoreMetrics.COMPLEXITY_KEY)));
    assertEquals(0, measure(context, "sample.gd", INCOMPLETE));
    assertTrue(nclocData.get("project:sample.gd").isEmpty());
  }

  @Test void keepsLexicalMetricsWhenParsingFails() {
    var context = context("# list\nvar a = [1, 2\n", "gdscript", InputFile.Type.MAIN, false);
    execute(context);
    assertEquals(1, measure(context, "sample.gd", INCOMPLETE));
    assertEquals(1, measure(context, "sample.gd", CoreMetrics.NCLOC_KEY));
    assertEquals(1, measure(context, "sample.gd", CoreMetrics.COMMENT_LINES_KEY));
    assertEquals(Set.of(2), nclocData.get("project:sample.gd").keySet());
    assertNull(measure(context, "sample.gd", CoreMetrics.CLASSES_KEY));
    assertNull(measure(context, "sample.gd", CoreMetrics.COMPLEXITY_KEY));
    assertTrue(context.allAnalysisErrors().iterator().next().message().startsWith("Expected ']'"));
  }

  @Test void marksUntokenizableFileIncompleteAndStillChecksLineLength() {
    var context = context("var s = \"abc\n" + "x".repeat(121) + "\n", "gdscript", InputFile.Type.MAIN, true);
    execute(context);
    assertEquals(1, measure(context, "sample.gd", INCOMPLETE));
    assertNull(measure(context, "sample.gd", CoreMetrics.NCLOC_KEY));
    assertNull(measure(context, "sample.gd", CoreMetrics.COMMENT_LINES_KEY));
    assertNull(measure(context, "sample.gd", CoreMetrics.CLASSES_KEY));
    var error = context.allAnalysisErrors().iterator().next();
    assertTrue(error.message().startsWith("Unterminated string"), error.message());
    assertEquals(List.of(1, 8), List.of(error.location().line(), error.location().lineOffset()));
    assertEquals(2, context.allIssues().iterator().next().primaryLocation().textRange().start().line());
  }

  @Test void marksUnreadableFileIncomplete() {
    var context = context(false);
    context.fileSystem().add(TestInputFileBuilder.create("project", "missing.gd").setLanguage("gdscript")
        .setType(InputFile.Type.MAIN).setCharset(StandardCharsets.UTF_8).build());
    execute(context);
    assertEquals(1, measure(context, "missing.gd", INCOMPLETE));
    assertNull(measure(context, "missing.gd", CoreMetrics.NCLOC_KEY));
    assertTrue(context.allAnalysisErrors().iterator().next().message().startsWith("Cannot read the file"));
  }

  @Test void reportsIncompleteFilesWithoutFailingByDefault() {
    var context = goodAndBadFiles();
    execute(context);
    assertEquals(0, measure(context, "good.gd", INCOMPLETE));
    assertEquals(1, measure(context, "bad.gd", INCOMPLETE));
  }

  @Test void strictModeFailsAfterPublishingEveryFile() {
    var context = goodAndBadFiles();
    context.settings().setProperty(GDScriptSensor.FAIL_ON_INCOMPLETE, "true");
    var failure = assertThrows(IllegalStateException.class, () -> execute(context));
    assertTrue(failure.getMessage().contains("bad.gd"), failure.getMessage());
    assertEquals(1, measure(context, "good.gd", CoreMetrics.NCLOC_KEY));
    assertEquals(1, measure(context, "bad.gd", INCOMPLETE));
  }

  @Test void publishesNothingWhenCancelled() {
    var context = goodAndBadFiles();
    context.setCancelled(true);
    execute(context);
    assertTrue(context.measures("project:good.gd").isEmpty());
    assertTrue(context.allAnalysisErrors().isEmpty());
  }

  private SensorContextTester goodAndBadFiles() {
    var context = context(false);
    add(context, "good.gd", "var a = 1\n", "gdscript", InputFile.Type.MAIN);
    add(context, "bad.gd", "var s = \"abc\n", "gdscript", InputFile.Type.MAIN);
    return context;
  }

  /** Records ncloc_data per file, as the scanner's FileLinesContext would. */
  private final class RecordingLines implements FileLinesContext {
    private final String fileKey;
    private final Map<Integer, Integer> values = new TreeMap<>();

    RecordingLines(String fileKey) { this.fileKey = fileKey; }

    @Override public void setIntValue(String metricKey, int line, int value) {
      assertEquals(CoreMetrics.NCLOC_DATA_KEY, metricKey);
      values.put(line, value);
    }

    @Override public void setStringValue(String metricKey, int line, String value) { fail("unexpected string line data"); }
    @Override public void save() { nclocData.put(fileKey, values); }
  }
}
