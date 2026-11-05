package org.orebron.gdscript.checks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.orebron.gdscript.checks.Symbols.Kind;
import org.orebron.gdscript.lexer.GDScriptLexer;
import org.orebron.gdscript.metrics.SizeMetrics;
import org.orebron.gdscript.parser.GDScriptParser;

class SymbolsTest {
  /** Redeclaring one name many times, each used once, took seconds when every use scanned every declaration. */
  @Test void manyDeclarationsOfOneNameResolveInLinearTime() throws Exception {
    int n = 10_000;
    var source = new StringBuilder("var x = 1\nfunc f():\n");
    for (int i = 0; i < n; i++) source.append("\tvar x = ").append(i).append("\n\tprint(x)\n");
    for (int i = 0; i < n; i++) source.append("\tif true:\n\t\tvar x = ").append(i).append("\n\t\tprint(x)\n");
    var file = parse(source.toString());
    List<Check> checks = List.of(new ShadowedVariableCheck(), new UnusedSymbolChecks.UnusedVariableCheck(),
        new UnusedSymbolChecks.UnusedLocalConstantCheck(), new UnusedSymbolChecks.UnusedParameterCheck(),
        new UnusedSymbolChecks.UnusedPrivateClassVariableCheck(), new UnusedSymbolChecks.UnusedSignalCheck());
    // Seconds before the fix (2 s timed out), well under 0.5 s after; the bound only has to separate the two.
    int issues = assertTimeoutPreemptively(Duration.ofSeconds(2),
        () -> checks.stream().mapToInt(check -> check.check(file, 0).size()).sum());
    assertEquals(2 * n, issues); // each local shadows the member, and none is unused
    file.symbols().declarations.forEach(d -> assertEquals(d.kind == Kind.LOCAL_VARIABLE ? 1 : 0, d.uses, d.kind + " at line " + d.name.line()));
  }

  @Test void scopesSymbolsAndCallsAreBuiltOncePerFile() throws Exception {
    var file = parse("var a = 1\nfunc f():\n\tOS.execute(a, [])\n");
    assertSame(file.scopes(), file.scopes());
    assertSame(file.symbols(), file.symbols());
    assertSame(file.scopes(), file.symbols().scopes);
    assertSame(file.calls(), file.calls());
    var unparsed = new SourceFile("func", GDScriptLexer.lex("func"), null, null);
    assertNull(unparsed.scopes());
    assertNull(unparsed.symbols());
    assertNull(unparsed.calls());
  }

  private static SourceFile parse(String source) throws Exception {
    var tokens = GDScriptLexer.lex(source);
    return new SourceFile(source, tokens, SizeMetrics.of(tokens), GDScriptParser.parse(tokens));
  }
}
