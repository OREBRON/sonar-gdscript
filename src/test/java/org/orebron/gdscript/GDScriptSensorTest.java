package org.orebron.gdscript;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sonar.api.batch.fs.InputFile;
import org.sonar.api.batch.fs.internal.TestInputFileBuilder;
import org.sonar.api.batch.rule.internal.ActiveRulesBuilder;
import org.sonar.api.batch.sensor.internal.SensorContextTester;
import org.sonar.api.rule.RuleKey;
import static org.junit.jupiter.api.Assertions.*;

class GDScriptSensorTest {
  @TempDir Path directory;

  private SensorContextTester context(String content, String language, InputFile.Type type, boolean active) {
    var context = SensorContextTester.create(directory);
    var file = TestInputFileBuilder.create("project", "sample.gd").setLanguage(language)
        .setType(type).setCharset(StandardCharsets.UTF_8).setContents(content).build();
    context.fileSystem().add(file);
    var rules = new ActiveRulesBuilder();
    if (active) rules.addRule(new org.sonar.api.batch.rule.internal.NewActiveRule.Builder()
        .setRuleKey(RuleKey.of("gdscript", "line-length")).setLanguage("gdscript").setSeverity("MINOR").build());
    context.setActiveRules(rules.build());
    return context;
  }

  @Test void acceptsBoundaryEmptyAndUnicodeLines() {
    for (String text : new String[]{"", "x".repeat(120), "😀".repeat(120), "\r\n\n"}) {
      var context = context(text, "gdscript", InputFile.Type.MAIN, true);
      new GDScriptSensor().execute(context);
      assertTrue(context.allIssues().isEmpty());
    }
  }

  @Test void respectsConfiguredThresholdAndCrLf() {
    var context = context("12345\r\n123456\r\n", "gdscript", InputFile.Type.MAIN, true);
    context.settings().setProperty("sonar.gdscript.maxLineLength", "5");
    new GDScriptSensor().execute(context);
    assertEquals(1, context.allIssues().size());
    assertEquals(2, context.allIssues().iterator().next().primaryLocation().textRange().start().line());
  }

  @Test void skipsInactiveRulesOtherLanguagesAndTestFiles() {
    var inactive = context("x".repeat(121), "gdscript", InputFile.Type.MAIN, false);
    var other = context("x".repeat(121), "other", InputFile.Type.MAIN, true);
    var test = context("x".repeat(121), "gdscript", InputFile.Type.TEST, true);
    for (var context : java.util.List.of(inactive, other, test)) {
      new GDScriptSensor().execute(context);
      assertTrue(context.allIssues().isEmpty());
    }
  }

  @Test void rejectsInvalidConfiguration() {
    for (String value : new String[]{"0", "-1", "abc", "2147483648"}) {
      var context = context("short", "gdscript", InputFile.Type.MAIN, true);
      context.settings().setProperty("sonar.gdscript.maxLineLength", value);
      assertThrows(IllegalArgumentException.class, () -> new GDScriptSensor().execute(context));
    }
  }

  @Test void reportsActualOffendingLine() throws java.io.IOException {
    String content;
    try (var fixture = getClass().getResourceAsStream("/fixtures/long-line.gd")) {
      assertNotNull(fixture);
      content = new String(fixture.readAllBytes(), StandardCharsets.UTF_8);
    }
    var context = context(content, "gdscript", InputFile.Type.MAIN, true);
    new GDScriptSensor().execute(context);
    var issue = context.allIssues().iterator().next();
    assertEquals(1, context.allIssues().size());
    assertEquals(RuleKey.of("gdscript", "line-length"), issue.ruleKey());
    assertEquals(2, issue.primaryLocation().textRange().start().line());
    assertEquals(2, issue.primaryLocation().textRange().end().line());
    assertEquals("Shorten this line from " + content.lines().toList().get(1).length()
        + " to at most 120 characters.", issue.primaryLocation().message());
    assertTrue(context.measures("project:sample.gd").isEmpty());
  }
}
