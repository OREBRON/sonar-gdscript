package org.orebron.gdscript.checks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.orebron.gdscript.lexer.GDScriptLexer;
import org.orebron.gdscript.lexer.SourceException;
import org.orebron.gdscript.lexer.Token;
import org.orebron.gdscript.metrics.SizeMetrics;
import org.orebron.gdscript.parser.GDScriptParser;
import org.orebron.gdscript.parser.SyntaxNode;

/**
 * The fixture project in its/rules, which is also scanned on a server: every rule has a file named after it, and
 * its "# Noncompliant" comments list exactly the lines the rule reports at its default limit.
 */
class RuleFixturesTest {
  private static final Path FIXTURES = Path.of("..", "its", "rules");
  /** {@code # Noncompliant}, {@code # Noncompliant@+1} for another line, {@code # Noncompliant 2} for two issues. */
  private static final Pattern MARKER = Pattern.compile("#\\s*Noncompliant(?:@([+-]\\d+))?(?:\\s+(\\d+)\\b)?");

  static Stream<Arguments> checks() { return Checks.ALL.stream().map(check -> Arguments.of(check.key(), check)); }

  @ParameterizedTest(name = "{0}")
  @MethodSource("checks")
  void fixtureListsWhatTheRuleReports(String key, Check check) throws Exception {
    Path fixture = FIXTURES.resolve(key + ".gd");
    assertTrue(Files.exists(fixture), "Every rule needs a fixture: " + fixture);
    String source = Files.readString(fixture);
    List<Integer> actual = analyze(source).stream().filter(violation -> violation.rule().equals(check.ruleKey()))
        .map(Violation::line).sorted().toList();
    assertEquals(expectedLines(source), actual, key);
  }

  @Test void everyNosonarLineHasAFindingForTheScannerToDrop() throws Exception {
    String source = Files.readString(FIXTURES.resolve("nosonar.gd"));
    var reported = new HashSet<Integer>();
    analyze(source).forEach(violation -> reported.add(violation.line()));
    String[] lines = source.split("\n", -1);
    for (int i = 0; i < lines.length; i++) {
      if (lines[i].contains("# NOSONAR")) assertTrue(reported.contains(i + 1), "nosonar.gd:" + (i + 1) + " has no finding");
    }
  }

  @Test void everyFixtureBelongsToARule() throws Exception {
    var keys = new HashSet<String>();
    Checks.ALL.forEach(check -> keys.add(check.key() + ".gd"));
    keys.add("nosonar.gd");
    try (Stream<Path> files = Files.list(FIXTURES)) {
      files.filter(path -> path.toString().endsWith(".gd"))
          .forEach(path -> assertTrue(keys.contains(path.getFileName().toString()), "No rule for " + path));
    }
  }

  static List<Integer> expectedLines(String source) {
    var expected = new ArrayList<Integer>();
    String[] lines = source.split("\n", -1);
    for (int i = 0; i < lines.length; i++) {
      var matcher = MARKER.matcher(lines[i]);
      if (!matcher.find()) continue;
      int line = i + 1 + (matcher.group(1) == null ? 0 : Integer.parseInt(matcher.group(1)));
      int count = matcher.group(2) == null ? 1 : Integer.parseInt(matcher.group(2));
      for (int n = 0; n < count; n++) expected.add(line);
    }
    return expected.stream().sorted().toList();
  }

  /**
   * Every rule at its default limit, with the precedence a real analysis applies. As in the sensor, a file that fails
   * to tokenize or parse is checked with what could be read, and parsing-failure reports the failure.
   */
  private static List<Violation> analyze(String source) {
    List<Token> tokens = List.of();
    SyntaxNode tree = null;
    SourceException failure = null;
    try {
      tokens = GDScriptLexer.lex(source);
      tree = GDScriptParser.parse(tokens);
    } catch (SourceException e) {
      failure = e;
    }
    var file = new SourceFile(source, tokens, tokens.isEmpty() ? null : SizeMetrics.of(tokens), tree);
    var violations = new ArrayList<Violation>();
    for (Check check : Checks.ALL) {
      int limit = check.limit().map(Limit::defaultValue).orElse(check instanceof LineLengthCheck ? LineLengthCheck.DEFAULT_MAXIMUM : 0);
      violations.addAll(check.check(file, limit));
      if (failure != null && check instanceof ParsingFailureCheck parsingFailure) violations.add(parsingFailure.violation(failure.getMessage(), failure.line()));
    }
    return Checks.withPrecedence(violations);
  }
}
