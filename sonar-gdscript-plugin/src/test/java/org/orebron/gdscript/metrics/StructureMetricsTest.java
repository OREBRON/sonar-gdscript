package org.orebron.gdscript.metrics;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.orebron.gdscript.lexer.GDScriptLexer;
import org.orebron.gdscript.parser.GDScriptParser;

/** The worked complexity examples of docs/metrics-plan.md, each as a whole script. */
class StructureMetricsTest {
  @ParameterizedTest(name = "{0}")
  @CsvSource(delimiter = '|', textBlock = """
      one function                     | func f():\\n\\tpass\\n                                                                 | 1
      if, and, elif, else              | func f(x):\\n\\tif x and y:\\n\\t\\tpass\\n\\telif z:\\n\\t\\tpass\\n\\telse:\\n\\t\\tpass\\n | 4
      member conditional expression    | var v = a if c else b\\n                                                             | 1
      match without unguarded catch-all | func f(x):\\n\\tmatch x:\\n\\t\\t1, 2:\\n\\t\\t\\tpass\\n\\t\\t[var a, ..]:\\n\\t\\t\\tpass\\n\\t\\tvar o when o > 0:\\n\\t\\t\\tpass\\n\\t\\t_:\\n\\t\\t\\tpass\\n | 4
      lambda is its own body           | func f():\\n\\tvar cb = func(): return a or b\\n                                     | 3
      loops                            | func f():\\n\\tfor i in range(3):\\n\\t\\twhile i:\\n\\t\\t\\tbreak\\n                | 3
      """)
  void countsCyclomaticComplexity(String example, String script, int complexity) throws Exception {
    assertEquals(complexity, of(unescape(script)).complexity());
  }

  @Test void countsClassesAndFunctionsWithBodies() throws Exception {
    var metrics = of("""
        @abstract class Shape:
        \t@abstract func area() -> float
        \tfunc name() -> String:
        \t\treturn "shape"
        var size: int:
        \tget:
        \t\treturn 1
        var other: int: get = name
        """);
    assertEquals(new StructureMetrics(2, 2, 2), metrics);
  }

  private static StructureMetrics of(String script) throws Exception {
    return StructureMetrics.of(GDScriptParser.parse(GDScriptLexer.lex(script)));
  }

  private static String unescape(String script) { return script.replace("\\n", "\n").replace("\\t", "\t"); }
}
