package org.orebron.gdscript;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.orebron.gdscript.metrics.SizeFixture;
import org.sonar.api.batch.fs.InputFile;
import org.sonar.api.batch.sensor.highlighting.TypeOfText;
import org.sonar.api.issue.NoSonarFilter;
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
  private final Map<String, Set<Integer>> noSonarLines = new HashMap<>();

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

  private void execute(SensorContextTester context) {
    var noSonar = new NoSonarFilter() {
      @Override public NoSonarFilter noSonarInFile(InputFile file, Set<Integer> lines) {
        assertTrue(context.allIssues().stream().noneMatch(issue -> issue.primaryLocation().inputComponent().equals(file)),
            "NOSONAR lines must be known before the file's issues are saved");
        noSonarLines.put(file.key(), lines);
        return this;
      }
    };
    new GDScriptSensor(fileLines, noSonar).execute(context);
  }

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

  private static final String SHELL_CALL = "func f(c):\n\tOS.execute(\"sh\", [\"-c\", c])\n";
  private static final String PARSING_FAILURE = "GDScript analysis is incomplete, so rules that need the syntax tree, including the "
      + "security rules, did not run on this file: ";

  private SensorContextTester activeRules(String... keys) {
    var context = SensorContextTester.create(directory);
    var rules = new ActiveRulesBuilder();
    for (String key : keys) rules.addRule(new org.sonar.scanner.plugin.api.impl.rule.NewActiveRule.Builder()
        .setRuleKey(RuleKey.of("gdscript", key)).setLanguage("gdscript").build());
    context.setActiveRules(rules.build());
    return context;
  }

  /** Issue #14: a nesting-depth parse failure silences the security rules, so a parsing-failure issue reaches new-code gates. */
  @Test void reportsAParsingFailureWhereTheSecurityRulesAreSilent() {
    var parsed = activeRules("parsing-failure", "process-execution");
    add(parsed, "sample.gd", SHELL_CALL, "gdscript", InputFile.Type.MAIN);
    execute(parsed);
    assertEquals(List.of(RuleKey.of("gdscript", "process-execution")), parsed.allIssues().stream().map(issue -> issue.ruleKey()).toList());

    var context = activeRules("parsing-failure", "process-execution");
    add(context, "sample.gd", SHELL_CALL + "var x = " + "(".repeat(256) + "1" + ")".repeat(256) + "\n", "gdscript", InputFile.Type.MAIN);
    execute(context);
    assertEquals(1, measure(context, "sample.gd", INCOMPLETE));
    assertEquals(1, context.allIssues().size());
    var issue = context.allIssues().iterator().next();
    assertEquals(RuleKey.of("gdscript", "parsing-failure"), issue.ruleKey());
    var range = issue.primaryLocation().textRange();
    assertEquals(List.of(3, 3), List.of(range.start().line(), range.end().line()));
    assertEquals(PARSING_FAILURE + "Nesting deeper than 256 near '1' at line 3, column 265", issue.primaryLocation().message());
  }

  @Test void reportsLexAndReadFailuresAndNotCleanFiles() {
    var context = activeRules("parsing-failure");
    add(context, "lex.gd", "var s = \"abc\n", "gdscript", InputFile.Type.MAIN);
    // A literal is named by its kind only: its text may be a secret.
    add(context, "literal.gd", "var a = 1 \"s3cret\"\n", "gdscript", InputFile.Type.MAIN);
    add(context, "clean.gd", "var a = 1\n", "gdscript", InputFile.Type.MAIN);
    context.fileSystem().add(TestInputFileBuilder.create("project", "missing.gd").setLanguage("gdscript")
        .setType(InputFile.Type.MAIN).setCharset(StandardCharsets.UTF_8).build());
    execute(context);
    var issues = new HashMap<String, org.sonar.api.batch.sensor.issue.Issue>();
    context.allIssues().forEach(issue -> issues.put(issue.primaryLocation().inputComponent().key(), issue));
    assertEquals(Set.of("project:lex.gd", "project:literal.gd", "project:missing.gd"), issues.keySet());
    var lex = issues.get("project:lex.gd").primaryLocation();
    assertEquals(PARSING_FAILURE + "Unterminated string at line 1, column 9", lex.message());
    assertEquals(1, lex.textRange().start().line());
    String literal = issues.get("project:literal.gd").primaryLocation().message();
    assertTrue(literal.startsWith(PARSING_FAILURE) && literal.contains("near a string literal") && !literal.contains("s3cret"), literal);
    var missing = issues.get("project:missing.gd").primaryLocation();
    assertTrue(missing.message().startsWith(PARSING_FAILURE + "Cannot read the file"), missing.message());
    assertNull(missing.textRange());
  }

  /** A failure at the end of the file is on its empty last line; a line the scanner rejects puts the issue on the file. */
  @Test void reportsAFailureAtTheEndOfTheFileAndPastIt() {
    var context = activeRules("parsing-failure");
    add(context, "sample.gd", "var a = [1, 2\n", "gdscript", InputFile.Type.MAIN);
    execute(context);
    var location = context.allIssues().iterator().next().primaryLocation();
    assertTrue(location.message().startsWith(PARSING_FAILURE + "Expected ']' at end of file"), location.message());
    assertEquals(2, location.textRange().start().line());

    var past = context(false);
    add(past, "sample.gd", "var a = 1\n", "gdscript", InputFile.Type.MAIN);
    var file = past.fileSystem().inputFile(past.fileSystem().predicates().all());
    var violation = new org.orebron.gdscript.checks.ParsingFailureCheck().violation("m", 99);
    new AnalysisPublisher(past, fileLines, null)
        .publish(new FileAnalysis(file, List.of(), null, null, new FileAnalysis.Failure("m", 99, 0), List.of(violation)));
    assertNull(past.allIssues().iterator().next().primaryLocation().textRange());
  }

  @Test void reportsNoParsingFailureWhenTheRuleIsInactive() {
    var context = context("var a = [1, 2\n", "gdscript", InputFile.Type.MAIN, true);
    execute(context);
    assertEquals(1, measure(context, "sample.gd", INCOMPLETE));
    assertTrue(context.allIssues().isEmpty());
  }

  /** NOSONAR on the line where the analysis stopped must not drop the parsing-failure issue; other lines keep it. */
  @Test void nosonarCannotHideTheFailureLine() {
    var context = activeRules("parsing-failure");
    add(context, "sample.gd", "# NOSONAR\nvar a = )  # NOSONAR\n", "gdscript", InputFile.Type.MAIN);
    execute(context);
    assertEquals(Set.of(1), noSonarLines.get("project:sample.gd"));
    assertEquals(2, context.allIssues().iterator().next().primaryLocation().textRange().start().line());
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

  /** A hostile file name must not forge scanner log lines (CWE-117), neither in the warning nor in the strict-mode failure. */
  @Test void escapesControlCharactersInReportedFileNames() {
    var file = TestInputFileBuilder.create("project", "bad\n\u001B[31mFORGED.gd").setLanguage("gdscript").setType(InputFile.Type.MAIN)
        .setCharset(StandardCharsets.UTF_8).setContents("var s = \"abc\n").build();
    var context = context(false);
    context.fileSystem().add(file);
    context.settings().setProperty(GDScriptSensor.FAIL_ON_INCOMPLETE, "true");
    var failure = assertThrows(IllegalStateException.class, () -> execute(context));
    // slf4j-nop discards logs, so a proxy Logger records the formatted warnings.
    var warnings = new ArrayList<String>();
    var log = (org.slf4j.Logger) java.lang.reflect.Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{org.slf4j.Logger.class},
        (proxy, method, args) -> {
          if (method.getName().equals("warn")) {
            warnings.add(org.slf4j.helpers.MessageFormatter.arrayFormat((String) args[0], java.util.Arrays.copyOfRange(args, 1, args.length)).getMessage());
          }
          return null;
        });
    GDScriptSensor.report(List.of(FileAnalysis.of(file, List.of(), () -> false)), false, log);
    assertEquals(1, warnings.size());
    for (String text : List.of(warnings.get(0), failure.getMessage())) {
      assertTrue(text.contains("bad\\nU+001B[31mFORGED.gd"), text);
      assertTrue(text.chars().noneMatch(c -> c < 0x20 || c == 0x7F), text);
    }
  }

  /** commented-code parses comment text, so a deeply nested comment must not stop the analysis of other files. */
  @Test void deeplyNestedCommentedCodeDoesNotStopTheAnalysis() {
    var context = activeRule("commented-code", "0");
    add(context, "nested.gd", "# var x: " + "A[".repeat(50_000) + "int" + "]".repeat(50_000) + "\nvar a = 1\n", "gdscript", InputFile.Type.MAIN);
    add(context, "next.gd", "var b = 1\n", "gdscript", InputFile.Type.MAIN);
    execute(context);
    assertEquals(1, measure(context, "nested.gd", CoreMetrics.NCLOC_KEY));
    assertEquals(1, measure(context, "next.gd", CoreMetrics.NCLOC_KEY));
  }

  /** An unexpected failure ends that file as FAILED, naming only the exception type; the analysis goes on. */
  @Test void containsUnexpectedFailuresPerFile() {
    var file = TestInputFileBuilder.create("project", "crash.gd").setLanguage("gdscript").setType(InputFile.Type.MAIN)
        .setCharset(StandardCharsets.UTF_8).setContents("var a = 1\n").build();
    var crashing = new org.orebron.gdscript.checks.Check() {
      @Override public String key() { return "crash"; }
      @Override public String name() { return "crash"; }
      @Override public String htmlDescription() { return ""; }
      @Override public String severity() { return "MINOR"; }
      @Override public List<org.orebron.gdscript.checks.Violation> check(org.orebron.gdscript.checks.SourceFile source, int limit) {
        throw new StackOverflowError("scanned text");
      }
    };
    var analysis = FileAnalysis.of(file, List.of(new org.orebron.gdscript.checks.ActiveCheck(crashing, 0)), () -> false);
    assertFalse(analysis.complete());
    assertEquals("Analysis failed: StackOverflowError", analysis.failure().message());
  }

  /** A check that cancels the scan stops the file before the next check; no FileAnalysis exists to publish. */
  @Test void stopsBetweenChecksWhenCancelled() {
    // Parsed, unparsable and untokenizable files reach the checks through different paths.
    for (String content : new String[]{"var a = 1\n", "var a = [1, 2\n", "var s = \"abc\n"}) {
      var file = TestInputFileBuilder.create("project", "sample.gd").setLanguage("gdscript").setType(InputFile.Type.MAIN)
          .setCharset(StandardCharsets.UTF_8).setContents(content).build();
      var cancelled = new AtomicBoolean();
      var ran = new ArrayList<String>();
      // An explicit list fixes the order: the cancelling check runs first.
      var checks = List.of(recording("cancelling", ran, () -> cancelled.set(true)), recording("next", ran, () -> {}));
      assertThrows(CancellationException.class, () -> FileAnalysis.of(file, checks, cancelled::get), content);
      assertEquals(List.of("cancelling"), ran, content);
    }
  }

  private static org.orebron.gdscript.checks.ActiveCheck recording(String key, List<String> ran, Runnable action) {
    var check = new org.orebron.gdscript.checks.Check() {
      @Override public String key() { return key; }
      @Override public String name() { return key; }
      @Override public String htmlDescription() { return ""; }
      @Override public String severity() { return "MINOR"; }
      @Override public List<org.orebron.gdscript.checks.Violation> check(org.orebron.gdscript.checks.SourceFile source, int limit) {
        ran.add(key);
        action.run();
        return List.of();
      }
    };
    return new org.orebron.gdscript.checks.ActiveCheck(check, 0);
  }

  @Test void reportsCodeSmellsOnTheFunctionNameWithTheRuleParameter() {
    var context = activeRule("function-complexity", "1");
    add(context, "sample.gd", "func busy(x):\n\tif x:\n\t\tpass\n", "gdscript", InputFile.Type.MAIN);
    execute(context);
    var issue = context.allIssues().iterator().next();
    assertEquals(RuleKey.of("gdscript", "function-complexity"), issue.ruleKey());
    var range = issue.primaryLocation().textRange();
    assertEquals(List.of(1, 5, 1, 9), List.of(range.start().line(), range.start().lineOffset(), range.end().line(), range.end().lineOffset()));
    assertEquals("Reduce the cyclomatic complexity of \"busy\" from 2 to at most 1.", issue.primaryLocation().message());
  }

  @Test void rejectsAnInvalidRuleParameter() {
    var context = activeRule("nesting-depth", "0");
    add(context, "sample.gd", "var a = 1\n", "gdscript", InputFile.Type.MAIN);
    var error = assertThrows(IllegalArgumentException.class, () -> execute(context));
    assertEquals("nesting-depth.max must be a positive integer", error.getMessage());
  }

  private SensorContextTester activeRule(String key, String max) {
    var context = SensorContextTester.create(directory);
    var rules = new ActiveRulesBuilder();
    rules.addRule(new org.sonar.scanner.plugin.api.impl.rule.NewActiveRule.Builder().setRuleKey(RuleKey.of("gdscript", key))
        .setLanguage("gdscript").setParam("max", max).build());
    context.setActiveRules(rules.build());
    return context;
  }

  @Test void publishesHighlightingDuplicationTokensAndNoSonarLines() {
    var context = context("## Doc\n@export var speed := 1.5 # NOSONAR\nvar s = &\"a\" + ^\"a\" + \"a\" + $Node\nvar t = \"\"\"x\ny\"\"\"\n",
        "gdscript", InputFile.Type.MAIN, true);
    execute(context);
    String key = "project:sample.gd";
    assertEquals(Set.of(2), noSonarLines.get(key));
    assertEquals(List.of(TypeOfText.STRUCTURED_COMMENT), context.highlightingTypeAt(key, 1, 0));
    assertEquals(List.of(List.of(TypeOfText.ANNOTATION), List.of(TypeOfText.KEYWORD), List.of(), List.of(TypeOfText.CONSTANT),
        List.of(TypeOfText.COMMENT)), List.of(0, 8, 12, 21, 25).stream().map(column -> context.highlightingTypeAt(key, 2, column)).toList());
    assertEquals(List.of(TypeOfText.STRING), context.highlightingTypeAt(key, 3, 28));
    assertEquals(List.of(TypeOfText.STRING), context.highlightingTypeAt(key, 5, 0));
    // Literals with the same text keep distinct images per kind; comments are not tokens, so line 1 has none.
    assertEquals(List.of("2:@exportvarspeed:=0", "3:vars=&\"\"+^\"\"+\"\"+$Node", "4:vart=\"\""),
        context.cpdTokens(key).stream().map(line -> line.getStartLine() + ":" + line.getValue()).toList());
  }

  @Test void publishesNothingWhenCancelled() {
    var context = goodAndBadFiles();
    context.setCancelled(true);
    execute(context);
    assertTrue(context.measures("project:good.gd").isEmpty());
    assertTrue(context.allAnalysisErrors().isEmpty());
  }

  /** Issue #12: an issue on a token spanning lines must not build an invalid range and abort the whole scan. */
  @Test void reportsIssuesOnMultiLineTokensOnTheirFirstLine() {
    String[][] cases = {
        // An empty range: the segment ends at the column where the node path starts.
        {"standalone-expression", "func f():\n\t\t$\"a\nb\"\n", "2"},
        // The end column is past the end of the start line.
        {"standalone-expression", "func f():\n\t$\"a\nbcdefgh\"\n", "2"},
        // A string pattern after a catch-all branch.
        {"unreachable-pattern", "func f(x):\n\tmatch x:\n\t\t_:\n\t\t\tpass\n\t\t\"a\nb\":\n\t\t\tpass\n", "5"},
    };
    for (String[] c : cases) {
      var context = SensorContextTester.create(directory);
      context.setActiveRules(new ActiveRulesBuilder().addRule(new org.sonar.scanner.plugin.api.impl.rule.NewActiveRule.Builder()
          .setRuleKey(RuleKey.of("gdscript", c[0])).setLanguage("gdscript").build()).build());
      add(context, "bad.gd", c[1], "gdscript", InputFile.Type.MAIN);
      add(context, "next.gd", "var b = 1\n", "gdscript", InputFile.Type.MAIN);
      try { execute(context); } catch (RuntimeException e) { System.out.println("CASE " + c[0] + " " + e); continue; }
      assertEquals(1, context.allIssues().size(), c[1]);
      var range = context.allIssues().iterator().next().primaryLocation().textRange();
      int line = Integer.parseInt(c[2]);
      assertEquals(List.of(line, line), List.of(range.start().line(), range.end().line()), c[1]);
      assertEquals(1, measure(context, "next.gd", CoreMetrics.NCLOC_KEY), c[1]);
    }
  }

  /** A violation whose range the scanner rejects is still saved, on its whole line; the file is finished. */
  @Test void publishesAnInvalidRangeOnTheWholeLine() {
    var context = context(false);
    add(context, "sample.gd", "var a = 1\n", "gdscript", InputFile.Type.MAIN);
    var file = context.fileSystem().inputFile(context.fileSystem().predicates().all());
    var violation = new org.orebron.gdscript.checks.Violation(RuleKey.of("gdscript", "line-length"), 1, 5, 2, "m");
    var noSonar = new NoSonarFilter() {
      @Override public NoSonarFilter noSonarInFile(InputFile f, Set<Integer> lines) { return this; }
    };
    new AnalysisPublisher(context, fileLines, noSonar)
        .publish(new FileAnalysis(file, List.of(), null, null, null, List.of(violation)));
    var range = context.allIssues().iterator().next().primaryLocation().textRange();
    assertEquals(List.of(1, 0, 1, 9), List.of(range.start().line(), range.start().lineOffset(), range.end().line(), range.end().lineOffset()));
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
