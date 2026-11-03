package org.orebron.gdscript.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CancellationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.orebron.gdscript.lexer.GDScriptLexer;
import org.orebron.gdscript.lexer.LexException;

class GDScriptParserTest {
  /** The syntax corpus comes from the gdscript-lexer test-jar; every valid file is accepted by Godot 4.7.2. */
  @ParameterizedTest
  @CsvSource({
      "valid/crlf.gd, 0, 1, 0, 0",
      "valid/declarations.gd, 3, 10, 2, 0",
      "valid/expressions.gd, 0, 5, 0, 6",
      "valid/extras.gd, 0, 1, 0, 0",
      "valid/lambda_closers.gd, 0, 1, 0, 4",
      "valid/layout.gd, 0, 2, 0, 0",
      "valid/literals.gd, 0, 1, 0, 0",
      "valid/statements.gd, 0, 5, 0, 0",
      "engine-rejected/nested_typed_collection.gd, 0, 0, 0, 0"})
  void parsesTheCorpusCompletely(String file, int classes, int functions, int accessors, int lambdas) throws Exception {
    SyntaxNode script = parse(corpus(file));
    assertEquals(List.of(1, classes, functions, accessors, lambdas), List.of(count(script, SyntaxKind.SCRIPT),
        count(script, SyntaxKind.CLASS), count(script, SyntaxKind.FUNCTION), count(script, SyntaxKind.ACCESSOR),
        count(script, SyntaxKind.LAMBDA)));
  }

  @ParameterizedTest
  @CsvSource({"invalid/unclosed_bracket.gd, Expected ']', 3, 0", "invalid/unexpected_indent.gd, Unexpected indentation, 2, 1"})
  void rejectsIncompleteSyntaxWithLocation(String file, String message, int line, int column) throws IOException {
    String source = corpus(file);
    var error = assertThrows(ParseException.class, () -> parse(source));
    assertTrue(error.getMessage().startsWith(message), error.getMessage());
    assertEquals(List.of(line, column), List.of(error.line(), error.column()));
  }

  @Test void rejectsKeywordsWhereNamesAreExpected() {
    assertThrows(ParseException.class, () -> parse("var if = 1\n"));
    assertThrows(ParseException.class, () -> parse("func f():\n\treturn 1 +\n"));
  }

  @Test void rejectsClosersOutsideBrackets() {
    for (String source : new String[]{"func f():\n\tpass)\n", "func f():\n\tpass\n\t)\n", "var a = 1,\n"}) {
      var error = assertThrows(ParseException.class, () -> parse(source));
      assertTrue(error.getMessage().matches("(Expected end of line|Unexpected operator).*"), error.getMessage());
    }
  }

  @Test void limitsNestingDepth() throws Exception {
    parse("var x = " + "(".repeat(255) + "1" + ")".repeat(255) + "\n");
    var error = assertThrows(ParseException.class, () -> parse("var x = " + "(".repeat(256) + "1" + ")".repeat(256) + "\n"));
    assertTrue(error.getMessage().startsWith("Nesting deeper than 256"), error.getMessage());
  }

  @Test void classifiesMatchBranches() throws Exception {
    SyntaxNode script = parse("""
        func f(x):
        \tmatch x:
        \t\t1, 2:
        \t\t\tpass
        \t\t[var a, ..]:
        \t\t\tpass
        \t\tvar o when o > 0:
        \t\t\tpass
        \t\t3, _:
        \t\t\tpass
        \t\tvar rest:
        \t\t\tpass
        """);
    assertEquals(3, count(script, SyntaxKind.MATCH_BRANCH));
    assertEquals(2, count(script, SyntaxKind.CATCH_ALL_BRANCH));
  }

  @Test void marksConditionalExpressionsAndLogicalOperators() throws Exception {
    SyntaxNode script = parse("var v = a if c and d or not e else b\nvar w = !x && y\n");
    assertEquals(1, count(script, SyntaxKind.CONDITIONAL_EXPRESSION));
    assertEquals(3, count(script, SyntaxKind.LOGICAL_OPERATOR));
  }

  @Test void nestsBodiesInsideTheirOwners() throws Exception {
    SyntaxNode function = parse("func f():\n\tif a:\n\t\tvar g = func(): return 1\n\telif b:\n\t\tpass\n").children().get(0);
    assertEquals(SyntaxKind.FUNCTION, function.kind());
    assertEquals("f", function.token().text());
    assertEquals(List.of(SyntaxKind.IF, SyntaxKind.ELIF), function.children().stream().map(SyntaxNode::kind).toList());
    assertEquals(SyntaxKind.LAMBDA, function.children().get(0).children().get(0).kind());
  }

  @Test void acceptsAnEmptyScript() throws Exception {
    SyntaxNode script = parse("");
    assertEquals(SyntaxKind.SCRIPT, script.kind());
    assertTrue(script.children().isEmpty());
  }

  @Test void stopsWhenCancelled() throws LexException {
    var tokens = GDScriptLexer.lex("var a\n".repeat(5_000));
    assertThrows(CancellationException.class, () -> GDScriptParser.parse(tokens, () -> true));
  }

  private static SyntaxNode parse(String source) throws LexException, ParseException {
    return GDScriptParser.parse(GDScriptLexer.lex(source));
  }

  private static int count(SyntaxNode root, SyntaxKind kind) {
    return (int) root.descendantsAndSelf().filter(node -> node.kind() == kind).count();
  }

  private static String corpus(String file) throws IOException {
    try (var stream = GDScriptParserTest.class.getResourceAsStream("/fixtures/syntax/" + file)) {
      return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
    }
  }
}
