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

  @Test void namingConventionFollowsTheStyleGuide() throws Exception {
    String source = """
        class_name my_class
        signal HealthChanged
        const MAX_SPEED = 1
        const Bullet = preload("res://bullet.gd")
        const lower = 2
        enum State {IDLE, Running = 2}
        var _private_value
        var camelCase
        func _on_Button_pressed(otherValue, _unused):
        \tfor Index in 3:
        \t\tpass
        class inner_class:
        \tpass
        """;
    var violations = new NamingConventionCheck().check(parse(source), 0);
    assertEquals(List.of(1, 2, 5, 6, 8, 9, 9, 10, 12), lines(violations));
    assertEquals("Rename this class \"my_class\" to match the PascalCase naming convention.", violations.get(0).message());
    assertEquals("Rename this enum member \"Running\" to match the CONSTANT_CASE naming convention.", violations.get(3).message());
  }

  @Test void emptyBlockNeedsAComment() throws Exception {
    String source = """
        func stub():
        \tpass
        func explained():
        \tpass # overridden by subclasses
        func documented():
        \t# Nothing to clean up.
        \tpass
        func f(x):
        \tif x:
        \t\tpass
        \telse:
        \t\tprint(x)
        \tmatch x:
        \t\t1:
        \t\t\tpass
        \tvar cb = func(): pass
        """;
    var violations = new EmptyBlockCheck().check(parse(source), 0);
    assertEquals(List.of(1, 9), lines(violations));
    assertTrue(violations.get(0).message().startsWith("Add a nested comment"));
    assertEquals("Either remove or fill this block of code.", violations.get(1).message());
  }

  @Test void commentedCodeIsCodeThatParses() throws Exception {
    String source = """
        # var speed = 10
        # print(speed)
        # return early when the player is dead
        # iOS-Specific (OpenIAP)
        # if is_ready:
        # Note: x = 1 is the default.
        ## Example: foo(1)
        var a = 1 # b = 2
        #\tpass
        """;
    var violations = new CommentedCodeCheck().check(parse(source), 0);
    assertEquals(List.of(1, 5, 9), lines(violations));
    assertEquals("Remove this commented out code.", violations.get(0).message());
  }

  @Test void duplicateStringCountsRegularAndRawStrings() throws Exception {
    String source = "var a = {\"speed\": 1}\nvar b = a[\"speed\"] + a[r\"speed\"]\nvar c = [&\"named\", &\"named\", &\"named\", \"abc\", \"abc\", \"abc\"]\n";
    var violations = new DuplicateStringCheck().check(parse(source), 3);
    assertEquals(List.of(1), lines(violations));
    assertEquals(List.of(9), violations.stream().map(Violation::column).toList());
    assertEquals("Define a constant instead of duplicating this literal \"speed\" 3 times.", violations.get(0).message());
    assertTrue(new DuplicateStringCheck().check(parse(source), 4).isEmpty());
  }

  @Test void collapsibleIfNeedsTwoLoneIfs() throws Exception {
    String source = """
        func f(a, b):
        \tif a:
        \t\tif b:
        \t\t\tpass
        \tif a:
        \t\tif b:
        \t\t\tpass
        \t\telse:
        \t\t\tpass
        \tif a:
        \t\tif b:
        \t\t\tpass
        \telse:
        \t\tpass
        \tif a:
        \t\tprint(a)
        \t\tif b:
        \t\t\tpass
        """;
    assertEquals(List.of(3), lines(new CollapsibleIfCheck().check(parse(source), 0)));
  }

  @Test void tooManyReturnsLeavesLambdasOut() throws Exception {
    String source = "func f(x):\n\tif x:\n\t\treturn 1\n\tvar g = func(): return 2\n\tif not x:\n\t\treturn 3\n\treturn 4\n";
    var violations = new TooManyReturnsCheck().check(parse(source), 2);
    assertEquals(List.of(1), lines(violations));
    assertEquals("This function has 3 returns, which is more than the 2 allowed.", violations.get(0).message());
    assertTrue(new TooManyReturnsCheck().check(parse(source), 3).isEmpty());
    assertTrue(!new TooManyReturnsCheck().activeByDefault());
  }

  @Test void booleanLiteralComparisonSkipsGetters() throws Exception {
    String source = "var a = done == true\nvar b = false != done\nvar c = d.get(\"k\", false) == true\nvar e = is_ready() == false\n";
    assertEquals(List.of(1, 2, 4), lines(new BooleanLiteralCheck().check(parse(source), 0)));
  }

  @Test void identicalBranchesComparesMultiLineBodies() throws Exception {
    String source = """
        func f(x):
        \tif x == 1:
        \t\tprint(x)
        \t\treturn
        \telif x == 2:
        \t\tprint(x)
        \t\treturn
        \telse:
        \t\tprint(x)
        \tmatch x:
        \t\t1:
        \t\t\tprint(x)
        \t\t\treturn
        \t\t2:
        \t\t\tprint(x)
        \t\t\treturn
        \t\t3:
        \t\t\tpass
        \t\t4:
        \t\t\tpass
        """;
    var violations = new IdenticalBranchesCheck().check(parse(source), 0);
    assertEquals(List.of(5, 14), lines(violations));
    assertEquals("This branch's code block is the same as the block for the branch on line 2.", violations.get(0).message());
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
