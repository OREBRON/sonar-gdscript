package org.orebron.gdscript.checks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.orebron.gdscript.lexer.GDScriptLexer;
import org.orebron.gdscript.metrics.SizeMetrics;
import org.orebron.gdscript.parser.GDScriptParser;

/** The rules that follow Godot's GDScript warnings; Godot's own warning tests were the reference for each case. */
class GodotWarningChecksTest {
  private static final String UNUSED = """
      signal used_by_name
      signal used_directly
      signal unused
      var _private_used
      var _private_unused
      var public_unused
      func f(used, unused_parameter, _ignored):
      \tvar used_local = used
      \tvar unused_local = 1
      \tvar _ignored_local = 2
      \tconst UNUSED_CONSTANT = 3
      \tvar cb = func(lambda_unused): return used_local
      \tmatch used:
      \t\t[var bound]:
      \t\t\tpass
      \t\t{"key": var read}:
      \t\t\tprint(read)
      \temit_signal("used_by_name")
      \tused_directly.emit()
      \tprint(self._private_used, cb, {used_local = 1})
      \t@warning_ignore("unused_variable")
      \tvar silenced = 4
      var setter_only:
      \tset(value):
      \t\tpass
      """;

  @Test void unusedNamesFollowGodot() throws Exception {
    var file = parse(UNUSED);
    assertEquals(List.of(9, 14), lines(new UnusedSymbolChecks.UnusedVariableCheck().check(file, 0)));
    assertEquals(List.of(11), lines(new UnusedSymbolChecks.UnusedLocalConstantCheck().check(file, 0)));
    var parameters = new UnusedSymbolChecks.UnusedParameterCheck().check(file, 0);
    assertEquals(List.of(7, 12), lines(parameters));
    assertEquals("Remove the unused parameter \"lambda_unused\" of \"<anonymous lambda>()\", or rename it \"_lambda_unused\".",
        parameters.get(1).message());
    assertEquals(List.of(5), lines(new UnusedSymbolChecks.UnusedPrivateClassVariableCheck().check(file, 0)));
    assertEquals(List.of(3), lines(new UnusedSymbolChecks.UnusedSignalCheck().check(file, 0)));
  }

  @Test void shadowingComparesWithTheSameClassOnly() throws Exception {
    String source = """
        var health = 1
        func heal():
        \tpass
        func f(health):
        \tvar heal = 2
        \tfor value in health:
        \t\tprint(value, heal)
        class Inner:
        \tfunc g():
        \t\tvar health = 3
        \t\tprint(health)
        """;
    var violations = new ShadowedVariableCheck().check(parse(source), 0);
    assertEquals(List.of(4, 5), lines(violations));
    assertEquals("The local variable \"heal\" shadows the function declared at line 2; rename it.", violations.get(1).message());
  }

  @Test void unreachableCodeFollowsReturnsOnEveryPath() throws Exception {
    String source = """
        func f(x):
        \tif x:
        \t\treturn 1
        \t\tprint("after return")
        \tif x:
        \t\treturn 2
        \telif not x:
        \t\treturn 3
        \tprint("reachable: no else")
        \tmatch x:
        \t\t1:
        \t\t\treturn 4
        \t\t_:
        \t\t\treturn 5
        \tprint("unreachable")
        \tprint("reported once")
        var p:
        \tget:
        \t\treturn 1
        \t\tprint("accessors are left out")
        """;
    assertEquals(List.of(4, 15), lines(new UnreachableCodeCheck().check(parse(source), 0)));
  }

  /** Every enclosing block asked again whether a nested block returns, and rescanned it: seconds at 250 levels. */
  @Test void deeplyNestedBlocksAreCheckedInNearLinearTime() throws Exception {
    int depth = 250; // the parser stops at 256 levels of nesting
    var source = new StringBuilder("func f(x):\n");
    for (int i = 1; i <= depth; i++) {
      String indent = "\t".repeat(i);
      source.append((indent + "print(x)\n").repeat(20)).append(indent).append("if x:\n");
    }
    source.append("\t".repeat(depth + 1)).append("return 1\n");
    for (int i = depth; i >= 1; i--) {
      String indent = "\t".repeat(i);
      source.append(indent).append("else:\n").append(indent).append("\treturn 0\n").append(indent).append("x + 1\n");
    }
    assertChecksTakeUnderASecond(parse(source.toString()), depth, depth);
  }

  /** Each statement looked through all of its block's children: seconds for 30,000 ifs in one function. */
  @Test void manySiblingBlocksAreCheckedInLinearTime() throws Exception {
    int n = 30_000;
    var source = new StringBuilder("func f(x):\n");
    for (int i = 0; i < n; i++) source.append("\tif x:\n\t\tx + 1\n");
    source.append("\treturn 0\n\tx + 1\n");
    assertChecksTakeUnderASecond(parse(source.toString()), 1, n + 1);
  }

  /** Each input took almost 4 s before the fix and 0.2 s cold after; the bound only has to separate the two. */
  private static void assertChecksTakeUnderASecond(SourceFile file, int unreachable, int standalone) {
    int[] issues = assertTimeoutPreemptively(Duration.ofSeconds(1), () -> new int[] {
        new UnreachableCodeCheck().check(file, 0).size(), new StandaloneExpressionCheck().check(file, 0).size()});
    assertEquals(unreachable, issues[0]);
    assertEquals(standalone, issues[1]);
  }

  @Test void unreachablePatternFollowsACatchAll() throws Exception {
    String source = "func f(x):\n\tmatch x:\n\t\tvar y when y > 0:\n\t\t\tpass\n\t\t_:\n\t\t\tpass\n\t\t1:\n\t\t\tpass\n\t\t2:\n\t\t\tpass\n";
    assertEquals(List.of(7, 9), lines(new UnreachablePatternCheck().check(parse(source), 0)));
  }

  @Test void standaloneExpressionsHaveNoEffect() throws Exception {
    String source = """
        func f(a, b):
        \ta + b
        \ta == b
        \ta.b
        \tprint(a)
        \t((print(b)))
        \ta.b = 1
        \tawait a.done
        \t\"\"\"A string comment.\"\"\"
        \tmatch a:
        \t\t1:
        \t\t\tpass
        \ta if b else 1
        \t@warning_ignore("standalone_expression")
        \t-a
        \tvar cb = func(): print(a)
        \ta(); b
        """;
    var violations = new StandaloneExpressionCheck().check(parse(source), 0);
    assertEquals(List.of(2, 3, 4, 13, 17), lines(violations));
    assertTrue(violations.get(3).message().startsWith("Remove this conditional expression"));
  }

  @Test void emptyFileHasNoCode() throws Exception {
    assertEquals(List.of(1), lines(new EmptyFileCheck().check(parse("# Only a comment.\n\n"), 0)));
    assertTrue(new EmptyFileCheck().check(parse("pass\n"), 0).isEmpty());
  }

  @Test void onreadyRulesReadTheAnnotations() throws Exception {
    String source = """
        extends Node
        var a = $A
        var b := %B as Node2D
        var c = get_node("C")
        var d = self.get_node("D")
        var e = $E.position
        @onready var f = $F
        static var g = $G
        @export
        @onready var h = 1
        """;
    var file = parse(source);
    assertEquals(List.of(2, 3, 4, 5), lines(new OnreadyChecks.GetNodeWithoutOnreadyCheck().check(file, 0)));
    assertEquals("Add \"@onready\": the default value uses \"%\", which finds no node before \"_ready()\".",
        new OnreadyChecks.GetNodeWithoutOnreadyCheck().check(file, 0).get(1).message());
    assertEquals(List.of(10), lines(new OnreadyChecks.OnreadyWithExportCheck().check(file, 0)));
  }

  @Test void warningIgnoreCoversTheHeaderAndRegions() throws Exception {
    String source = """
        @warning_ignore("unused_parameter")
        func f(a,
        \t\tb):
        \tvar x = 1
        @warning_ignore_start("unused_variable")
        func g():
        \tvar y = 1
        @warning_ignore_restore("unused_variable")
        func h():
        \tvar z = 1
        """;
    var file = parse(source);
    assertTrue(new UnusedSymbolChecks.UnusedParameterCheck().check(file, 0).isEmpty());
    assertEquals(List.of(4, 10), lines(new UnusedSymbolChecks.UnusedVariableCheck().check(file, 0)));
  }

  @Test void warningIgnoreGrowsWithTheAnnotationNotNamesTimesLines() throws Exception {
    int count = 3000;
    var names = IntStream.range(0, count).mapToObj(n -> "\"n" + n + "\"").toList();
    String source = "@warning_ignore(" + String.join(", ", names) + ")\n" + "# c\n".repeat(count) + "var x = 1\n";
    var ignores = WarningIgnores.of(GDScriptLexer.lex(source));
    assertTrue(ignores.ignores("n0", 1) && ignores.ignores("n" + (count - 1), count + 2));
    assertFalse(ignores.ignores("n0", count + 3));
    assertEquals(count, ignores.rangeCount());
  }

  @Test void warningIgnoreJoinsOverlappingRanges() throws Exception {
    String source = """
        @warning_ignore("a")
        func f(x,
        \t\ty):
        \tpass
        @warning_ignore_start("a")
        @warning_ignore("a")
        var z = 1
        @warning_ignore_restore("a")
        var w = 1
        @warning_ignore("a")
        var v = 1
        var u = 1
        """;
    var ignores = WarningIgnores.of(GDScriptLexer.lex(source));
    assertEquals(List.of(1, 2, 3, 5, 6, 7, 8, 10, 11), IntStream.rangeClosed(1, 12).filter(line -> ignores.ignores("a", line)).boxed().toList());
    assertEquals(3, ignores.rangeCount());
  }

  private static SourceFile parse(String source) throws Exception {
    var tokens = GDScriptLexer.lex(source);
    return new SourceFile(source, tokens, SizeMetrics.of(tokens), GDScriptParser.parse(tokens));
  }

  private static List<Integer> lines(List<Violation> violations) { return violations.stream().map(Violation::line).toList(); }
}
