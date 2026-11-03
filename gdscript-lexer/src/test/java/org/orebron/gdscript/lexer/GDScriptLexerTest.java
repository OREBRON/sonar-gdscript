package org.orebron.gdscript.lexer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.orebron.gdscript.lexer.TokenKind.ANNOTATION;
import static org.orebron.gdscript.lexer.TokenKind.COMMENT;
import static org.orebron.gdscript.lexer.TokenKind.CONTINUATION;
import static org.orebron.gdscript.lexer.TokenKind.EOF;
import static org.orebron.gdscript.lexer.TokenKind.IDENTIFIER;
import static org.orebron.gdscript.lexer.TokenKind.NEWLINE;
import static org.orebron.gdscript.lexer.TokenKind.NODE_PATH;
import static org.orebron.gdscript.lexer.TokenKind.NUMBER;
import static org.orebron.gdscript.lexer.TokenKind.OPERATOR;
import static org.orebron.gdscript.lexer.TokenKind.STRING;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

class GDScriptLexerTest {
  private static final Path CORPUS = corpus();

  @ParameterizedTest
  @MethodSource("tokenizableFiles")
  void tokenizesCorpusLosslessly(Path file) throws Exception {
    String source = Files.readString(file, StandardCharsets.UTF_8);
    assertLossless(source, GDScriptLexer.lex(source));
  }

  /** Parser-level errors such as an unclosed bracket are still valid token streams. */
  static Stream<Path> tokenizableFiles() {
    return Stream.of("valid", "engine-rejected", "invalid").flatMap(GDScriptLexerTest::files)
        .filter(file -> !file.getFileName().toString().matches("unterminated_string\\.gd|mixed_indentation\\.gd"));
  }

  @ParameterizedTest
  @CsvSource({"unterminated_string.gd, Unterminated string, 2, 8", "mixed_indentation.gd, Mixed tabs and spaces, 6, 0"})
  void reportsLexicalErrorsWithLocation(String name, String message, int line, int column) throws IOException {
    String source = Files.readString(CORPUS.resolve("invalid").resolve(name), StandardCharsets.UTF_8);
    var error = assertThrows(LexException.class, () -> GDScriptLexer.lex(source));
    assertTrue(error.getMessage().startsWith(message), error.getMessage());
    assertEquals(line, error.line());
    assertEquals(column, error.column());
  }

  @Test void classifiesLiteralForms() throws LexException {
    assertKinds("&\"name\" ^\"A/B\" r'raw\\'q' \"\"\"a\nb\"\"\" 'it\\'s'", STRING, STRING, STRING, STRING, STRING);
    assertKinds("0xFF_FF 0b1010 1_000 1.5e-3 2E+10 1e", NUMBER, NUMBER, NUMBER, NUMBER, NUMBER, NUMBER, IDENTIFIER);
    assertKinds("x = .5", IDENTIFIER, OPERATOR, NUMBER);
    assertKinds("$Child/Grand $\"Spaced Path\" @export_range café", NODE_PATH, NODE_PATH, ANNOTATION, IDENTIFIER);
  }

  @Test void acceptsLineBreaksInEveryStringKind() throws LexException {
    var strings = GDScriptLexer.lex("a = \"one\ntwo\"\nb = r'x\ny'\n").stream().filter(t -> t.kind() == STRING).toList();
    assertEquals(List.of(1, 2, 3, 4), List.of(strings.get(0).line(), strings.get(0).endLine(), strings.get(1).line(), strings.get(1).endLine()));
  }

  @Test void readsTrailingDotFloatsButKeepsMemberAccessOnIntegers() throws LexException {
    assertEquals(List.of("f", "(", "1.", ",", "-", "1_234.", ")"), texts("f(1., -1_234.)"));
    assertEquals(List.of("5", ".", "abs", "(", ")"), texts("5.abs()"));
  }

  @Test void readsQuotedUniqueNodePaths() throws LexException {
    assertKinds("f($%\"Unique Name\", %\"Other\", %Hey/%Howdy)", IDENTIFIER, OPERATOR, NODE_PATH, OPERATOR, NODE_PATH,
        OPERATOR, NODE_PATH, OPERATOR);
  }

  @Test void keepsALineContinuedAcrossCommentLines() throws LexException {
    var tokens = GDScriptLexer.lex("if a \\\n\t\t# note\n\t\tand b:\n\tpass\n");
    assertEquals(2, tokens.stream().filter(t -> t.kind() == NEWLINE).count());
  }

  @Test void tellsUniqueNodeShorthandFromModulo() throws LexException {
    assertKinds("x = %Unique", IDENTIFIER, OPERATOR, NODE_PATH);
    assertKinds("return %Unique", IDENTIFIER, NODE_PATH);
    assertKinds("7 %3", NUMBER, OPERATOR, NUMBER);
    assertKinds("(a) %b", OPERATOR, IDENTIFIER, OPERATOR, OPERATOR, IDENTIFIER);
  }

  @Test void prefersTheLongestOperator() throws LexException {
    assertEquals(List.of("a", "**=", "b", "...", "c", "->", "d"), texts("a **= b ... c -> d"));
  }

  @Test void treatsCommentsAndContinuationsAsTrivia() throws LexException {
    assertEquals(List.of(IDENTIFIER, OPERATOR, NUMBER, OPERATOR, CONTINUATION, NUMBER, COMMENT, NEWLINE, EOF),
        kinds(GDScriptLexer.lex("a = 1 + \\\n\t2  # note\n")));
  }

  @Test void recordsIndentationOnNewlinesAndTokens() throws LexException {
    var tokens = GDScriptLexer.lex("func f():\n\tpass\n\n  # comment\nvar x\n");
    assertEquals(List.of(1, 0, 0), tokens.stream().filter(t -> t.kind() == NEWLINE).map(Token::nextIndent).toList());
    assertEquals(1, tokens.stream().filter(t -> t.text().equals("pass")).findFirst().orElseThrow().lineIndent());
  }

  @Test void checksIndentationMixingOutsideBracketsOnly() throws LexException {
    assertEquals(3, GDScriptLexer.lex("f(\n\t1,\n  2)\n").stream().filter(t -> t.kind() == NEWLINE).count());
    assertThrows(LexException.class, () -> GDScriptLexer.lex("if a:\n\tb\nif c:\n  d\n"));
  }

  @Test void countsCrLfLfAndLoneCrAsLineBreaks() throws LexException {
    var lines = GDScriptLexer.lex("a\r\nb\rc\nd").stream().filter(t -> t.kind() == IDENTIFIER).map(Token::line).toList();
    assertEquals(List.of(1, 2, 3, 4), lines);
  }

  @Test void spansMultiLineStrings() throws LexException {
    var string = GDScriptLexer.lex("x\ny\n    \"\"\"a\nbc\"\"\"\n").stream().filter(t -> t.kind() == STRING).findFirst().orElseThrow();
    assertEquals(List.of(3, 4, 4, 5), List.of(string.line(), string.column(), string.endLine(), string.endColumn()));
  }

  @Test void ignoresByteOrderMark() throws LexException {
    Token first = GDScriptLexer.lex("\uFEFFvar x").get(0);
    assertEquals(List.of(1, 0), List.of(first.offset(), first.column()));
  }

  @Test void rejectsCharactersOutsideTheLanguage() {
    var error = assertThrows(LexException.class, () -> GDScriptLexer.lex("a ? b"));
    assertTrue(error.getMessage().startsWith("Unexpected character '?'"));
  }

  @Test void stopsWhenCancelled() throws LexException {
    String source = "a\n".repeat(10_000);
    assertThrows(CancellationException.class, () -> new GDScriptLexer(source, () -> true).tokenize());
    assertEquals(20_001, new GDScriptLexer(source, () -> false).tokenize().size());
  }

  private static void assertKinds(String source, TokenKind... expected) throws LexException {
    var kinds = kinds(GDScriptLexer.lex(source)).stream().filter(k -> k != NEWLINE && k != EOF).toList();
    assertEquals(List.of(expected), kinds, source);
  }

  private static List<TokenKind> kinds(List<Token> tokens) { return tokens.stream().map(Token::kind).toList(); }

  private static List<String> texts(String source) throws LexException {
    return GDScriptLexer.lex(source).stream().filter(t -> t.kind() != NEWLINE && t.kind() != EOF).map(Token::text).toList();
  }

  /** Tokens are ordered, never overlap, match the source and cover every non-whitespace character. */
  private static void assertLossless(String source, List<Token> tokens) {
    boolean[] covered = new boolean[source.length()];
    int previousEnd = 0;
    for (Token token : tokens) {
      assertTrue(token.offset() >= previousEnd, () -> "overlapping token " + token);
      assertEquals(token.text(), source.substring(token.offset(), token.endOffset()));
      for (int i = token.offset(); i < token.endOffset(); i++) covered[i] = true;
      previousEnd = token.endOffset();
    }
    for (int i = 0; i < source.length(); i++) {
      int offset = i;
      assertTrue(covered[i] || Character.isWhitespace(source.charAt(i)), () -> "uncovered offset " + offset);
    }
  }

  private static Stream<Path> files(String directory) {
    try (var listing = Files.list(CORPUS.resolve(directory))) {
      return listing.sorted().toList().stream();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static Path corpus() {
    try {
      return Path.of(GDScriptLexerTest.class.getResource("/fixtures/syntax").toURI());
    } catch (URISyntaxException e) {
      throw new IllegalStateException(e);
    }
  }
}
