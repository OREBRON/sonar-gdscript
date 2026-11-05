package org.orebron.gdscript.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
      "valid/godot_extras.gd, 0, 3, 0, 0",
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

  /** Accepted by Godot's own parser tests (modules/gdscript/tests/scripts) but not covered by godot_extras.gd. */
  @Test void acceptsGodotParserFeatures() throws Exception {
    parse("func f():\n\tvar g = (func():)\n\tg.call(func():, 1)\n");
    parse("func f(a : = 1, b: int = 2) -> void:\n\tpass\n");
    parse("var d = {\"a b\" = 1, c = 2}\nvar e = Vector2(1., .5)\nvar f = 123.e4\n");
    parse("func f(a, b = 1, ...rest):\n\tassert(a, \"x\")\n\tvar r = preload(\"res://r.gd\")\n\tfor i in a:\n\t\tmatch i:\n\t\t\t1:\n\t\t\t\tbreak\n");
    parse("func _init():\n\tvar g = func(): return 1\n\treturn\nstatic func _static_init():\n\tpass\n");
    parse("func f(x):\n\tmatch x:\n\t\t[var a, ..]:\n\t\t\tpass\n\t\t{1: var b, ..}:\n\t\t\tpass\n\t\t[1, ..], 2:\n\t\t\tpass\n");
  }

  /** Errors Godot's parser reports without a semantic model; each case was checked with Godot 4.7.2 {@code --check-only}. */
  @ParameterizedTest
  @CsvSource(delimiter = '|', value = {
      "func f(...a, b):\\n\\tpass| Cannot have parameters after the rest parameter",
      "func f(...a = []):\\n\\tpass| The rest parameter cannot have a default value",
      "func f(a = 1, b):\\n\\tpass| Cannot have mandatory parameters after optional parameters",
      "signal s(a = 1)| Signal parameters cannot have a default value",
      "signal s(...a)| Signals cannot have a rest parameter",
      "func f():\\n\\tbreak| Cannot use \"break\" outside of a loop",
      "func f():\\n\\tfor i in 2:\\n\\t\\tvar g = func():\\n\\t\\t\\tcontinue| Cannot use \"continue\" outside of a loop",
      "func _init():\\n\\treturn 1| Constructor cannot return a value",
      "func _static_init():\\n\\tpass| Static constructor must be declared static",
      "static func _static_init(a):\\n\\tpass| Static constructor cannot have parameters",
      "func f():\\n\\tfunc g():\\n\\t\\tpass| Standalone lambdas cannot be accessed",
      "func f(x):\\n\\tmatch x:\\n\\t\\t[[var a]], 2:\\n\\t\\t\\tpass| Cannot use a variable bind with multiple patterns",
      "func f(x):\\n\\tmatch x:\\n\\t\\t..:\\n\\t\\t\\tpass| Rest pattern can only be used inside array and dictionary patterns",
      "func f(x):\\n\\tmatch x:\\n\\t\\t[.., 1]:\\n\\t\\t\\tpass| The \"..\" pattern must be the last element in the pattern array",
      "func f(x):\\n\\tmatch x:\\n\\t\\t{.., 1}:\\n\\t\\t\\tpass| The \"..\" pattern must be the last element in the pattern dictionary",
      "func f(x):\\n\\tmatch x:\\n\\t\\t{1: ..}:\\n\\t\\t\\tpass| The \"..\" pattern cannot be used as a value",
      "var yield = 1| Expected an identifier",
      "var a = preload| Expected \"(\" after \"preload\""})
  void rejectsWhatGodotsParserRejects(String source, String message) {
    var error = assertThrows(ParseException.class, () -> parse(source.replace("\\n", "\n").replace("\\t", "\t") + "\n"));
    assertTrue(error.getMessage().startsWith(message.strip()), error.getMessage());
  }

  @Test void rejectsClosersOutsideBrackets() {
    for (String source : new String[]{"func f():\n\tpass)\n", "func f():\n\tpass\n\t)\n", "var a = 1,\n"}) {
      var error = assertThrows(ParseException.class, () -> parse(source));
      assertTrue(error.getMessage().matches("(Expected end of line|Unexpected operator).*"), error.getMessage());
    }
  }

  /** Parse errors reach scanner logs: literal content stays out, other token text is escaped and capped. */
  @Test void keepsErrorMessagesPrintableAndWithoutLiterals() {
    for (String literal : new String[]{"\"\"\"top\u001bsecret\r\nforged\"\"\"", "$\"top\u001bsecret\nforged\""}) {
      var error = assertThrows(ParseException.class, () -> parse("var a = 1 " + literal + "\n"));
      assertTrue(error.getMessage().matches(".* near a (string literal|node path) at line 1, column 11"), error.getMessage());
      assertTrue(error.getMessage().chars().noneMatch(c -> c < 0x20 || c == 0x7F), error.getMessage());
      assertFalse(error.getMessage().contains("secret"), error.getMessage());
    }
    String message = assertThrows(ParseException.class, () -> parse("var a = 1 " + "x".repeat(500) + "\n")).getMessage();
    String near = message.substring(message.indexOf("near '") + 6, message.lastIndexOf("' at line"));
    assertTrue(near.codePointCount(0, near.length()) <= 80 && near.endsWith("\u2026"), message);
    assertTrue(assertThrows(ParseException.class, () -> parse("var a = 1 b\n")).getMessage().contains(" near 'b' at line 1"));
  }

  @Test void limitsNestingDepth() throws Exception {
    parse("var x = " + "(".repeat(255) + "1" + ")".repeat(255) + "\n");
    var error = assertThrows(ParseException.class, () -> parse("var x = " + "(".repeat(256) + "1" + ")".repeat(256) + "\n"));
    assertTrue(error.getMessage().startsWith("Nesting deeper than 256"), error.getMessage());
  }

  /** Same-line suites, type arguments and match patterns recurse outside expressions; they share the same limit. */
  @ParameterizedTest
  @CsvSource(delimiter = '|', value = {
      "func f(): | 'if 1: ' | pass | ''",
      "'' | 'class A: ' | pass | ''",
      "'var x: ' | A[ | int | ]",
      "'func f(x):\n\tmatch x:\n\t\t' | [ | '' | ]"})
  void limitsNestingDepthOutsideExpressions(String prefix, String open, String inner, String close) throws Exception {
    String tail = prefix.startsWith("func f(x)") ? ":\n\t\t\tpass\n" : "\n";
    parse(prefix + open.repeat(200) + inner + close.repeat(200) + tail);
    // Far past the limit: before the guard this overflowed the stack instead of failing to parse.
    var error = assertThrows(ParseException.class, () -> parse(prefix + open.repeat(50_000) + inner + close.repeat(50_000) + tail));
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
    assertEquals(1, count(script, SyntaxKind.MATCH));
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

  @Test void keepsParametersElseBranchesAndLastTokens() throws Exception {
    SyntaxNode function = parse("func f(a, b := 1):\n\tif a:\n\t\tpass\n\telse:\n\t\treturn b\nvar after = 1\n").children().get(0);
    assertEquals(List.of(SyntaxKind.PARAMETER, SyntaxKind.PARAMETER, SyntaxKind.IF, SyntaxKind.ELSE),
        function.children().stream().map(SyntaxNode::kind).toList());
    assertEquals(List.of("a", "b"), function.children().stream().limit(2).map(node -> node.token().text()).toList());
    assertEquals(List.of("b", 5), List.of(function.last().text(), function.last().line()));
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
