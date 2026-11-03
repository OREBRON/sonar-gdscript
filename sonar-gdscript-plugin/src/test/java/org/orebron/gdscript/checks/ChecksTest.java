package org.orebron.gdscript.checks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.orebron.gdscript.lexer.GDScriptLexer;
import org.orebron.gdscript.metrics.SizeMetrics;
import org.orebron.gdscript.parser.GDScriptParser;

/** Each check with compliant and noncompliant code, at and just above its limit. */
class ChecksTest {
  @Test void functionComplexityCountsOwnDecisionsOnly() throws Exception {
    String source = """
        func busy(a, b):
        \tif a and b:
        \t\tpass
        \telif a or b:
        \t\tpass
        \tvar cb = func(): return a if b else 1
        func calm():
        \tpass
        """;
    var violations = new FunctionComplexityCheck().check(parse(source), 4);
    assertEquals(List.of(1), lines(violations));
    assertEquals("Reduce the cyclomatic complexity of \"busy\" from 5 to at most 4.", violations.get(0).message());
    assertTrue(new FunctionComplexityCheck().check(parse(source), 5).isEmpty());
  }

  @Test void functionLengthCountsCodeLinesOnly() throws Exception {
    String source = "func f():\n\t# comment\n\n\tvar a = 1\n\treturn a\nfunc g():\n\tpass\n";
    var violations = new FunctionLengthCheck().check(parse(source), 2);
    assertEquals(List.of(1), lines(violations));
    assertEquals("This function has 3 lines of code, which is greater than the 2 authorized. Split it into smaller functions.",
        violations.get(0).message());
    assertTrue(new FunctionLengthCheck().check(parse(source), 3).isEmpty());
  }

  @Test void parameterCountIncludesVariadicAndAccessors() throws Exception {
    String source = "func f(a, b := 1, ...rest):\n\tpass\nvar x: int:\n\tset(value):\n\t\tpass\n";
    assertEquals(List.of(1), lines(new ParameterCountCheck().check(parse(source), 2)));
    assertTrue(new ParameterCountCheck().check(parse(source), 3).isEmpty());
  }

  @Test void nestingDepthReportsTheOutermostStatementBeyondTheLimit() throws Exception {
    String source = """
        func f(x):
        \tif x:
        \t\tfor i in x:
        \t\t\tif i:
        \t\t\t\twhile i:
        \t\t\t\t\tpass
        \telse:
        \t\tmatch x:
        \t\t\t1:
        \t\t\t\tif x:
        \t\t\t\t\tpass
        \t\tvar cb = func():
        \t\t\tif x:
        \t\t\t\tpass
        """;
    var violations = new NestingDepthCheck().check(parse(source), 2);
    assertEquals(List.of(4, 10), lines(violations));
    assertEquals("Refactor this code to not nest more than 2 if/for/while/match statements.", violations.get(0).message());
    assertTrue(new NestingDepthCheck().check(parse(source), 4).isEmpty());
  }

  @Test void taskCommentFindsUppercaseTagsInCommentsOnly() throws Exception {
    String source = "# TODO: add sound\nvar a = \"TODO in a string\" # FIXME later\n# todo lowercase\n# TODOS plural\n";
    var violations = new TaskCommentCheck().check(parse(source), 0);
    assertEquals(List.of(1, 2), lines(violations));
    assertEquals(List.of(0, 27), violations.stream().map(Violation::column).toList());
    assertTrue(violations.get(1).message().contains("FIXME"));
  }

  @Test void lineLengthCountsCodePoints() {
    var file = new SourceFile("abc\n😀😀😀😀\n", List.of(), null, null);
    assertEquals(List.of(2), lines(new LineLengthCheck().check(file, 3)));
  }

  @Test void treeChecksSkipFilesThatDidNotParse() {
    var file = new SourceFile("func f(a, b, c):\n", List.of(), null, null);
    for (Check check : Checks.ALL) {
      if (!(check instanceof LineLengthCheck)) assertTrue(check.check(file, 1).isEmpty(), check.key());
    }
  }

  @Test void rejectsInvalidLimits() {
    for (String value : new String[]{"0", "-1", "abc"}) {
      assertThrows(IllegalArgumentException.class, () -> Limit.parse("nesting-depth.max", value, 3));
    }
    assertEquals(3, Limit.parse("nesting-depth.max", null, 3));
    assertEquals(5, Limit.parse("nesting-depth.max", " 5 ", 3));
  }

  private static SourceFile parse(String source) throws Exception {
    var tokens = GDScriptLexer.lex(source);
    return new SourceFile(source, tokens, SizeMetrics.of(tokens), GDScriptParser.parse(tokens));
  }

  private static List<Integer> lines(List<Violation> violations) { return violations.stream().map(Violation::line).toList(); }
}
