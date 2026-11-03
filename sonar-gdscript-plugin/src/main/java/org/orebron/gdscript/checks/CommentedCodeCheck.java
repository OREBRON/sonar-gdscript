package org.orebron.gdscript.checks;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.orebron.gdscript.lexer.GDScriptLexer;
import org.orebron.gdscript.lexer.Keywords;
import org.orebron.gdscript.lexer.SourceException;
import org.orebron.gdscript.lexer.Token;
import org.orebron.gdscript.lexer.TokenKind;
import org.orebron.gdscript.parser.GDScriptParser;

/**
 * Comment lines that are GDScript code. A line counts as code when it parses as a class member or a statement and also
 * looks like code: a declaration or block header, an assignment, a call or subscript with no space before its
 * bracket, an annotation, or a lone
 * {@code pass}, {@code break}, {@code continue} or {@code return}, so that prose such as "return early" is not code.
 * Only whole-line {@code #} comments are read; {@code ##} documentation comments may hold examples. Consecutive
 * code lines give one issue.
 */
public final class CommentedCodeCheck implements Check {
  private static final Set<String> HEADERS = Set.of("var", "const", "func", "static", "signal", "extends", "class_name",
      "class", "enum", "if", "elif", "else", "for", "while", "match");
  private static final Set<String> JUMPS = Set.of("pass", "break", "continue", "return");
  private static final Set<String> ASSIGNMENTS = Set.of("=", ":=", "+=", "-=", "*=", "/=", "%=", "**=", "<<=", ">>=", "&=", "|=", "^=");

  @Override public String key() { return "commented-code"; }
  @Override public String name() { return "Sections of code should not be commented out"; }
  @Override public String severity() { return "MAJOR"; }
  @Override public List<String> tags() { return List.of("unused"); }

  @Override public String htmlDescription() {
    return "<p>Commented-out code distracts from the code that runs and goes stale quickly. Delete it: version "
        + "control keeps the history. <code>##</code> documentation comments are not checked, so they may contain "
        + "examples.</p>";
  }

  @Override public List<Violation> check(SourceFile file, int unused) {
    var violations = new ArrayList<Violation>();
    int previousCodeLine = -2;
    for (Token comment : wholeLineComments(file.tokens())) {
      if (!isCode(comment.text().substring(1).strip())) continue;
      if (comment.line() != previousCodeLine + 1) violations.add(Violation.at(this, comment, "Remove this commented out code."));
      previousCodeLine = comment.line();
    }
    return violations;
  }

  /** {@code #} comments with nothing but indentation before them; {@code ##} comments are left out. */
  private static List<Token> wholeLineComments(List<Token> tokens) {
    var comments = new ArrayList<Token>();
    int codeLine = 0;
    for (Token token : tokens) {
      if (token.kind() == TokenKind.COMMENT && token.line() != codeLine && !token.text().startsWith("##")) comments.add(token);
      if (TokenRanges.isSignificant(token)) codeLine = token.endLine();
    }
    return comments;
  }

  static boolean isCode(String text) {
    List<Token> tokens;
    try {
      tokens = TokenRanges.significant(GDScriptLexer.lex(text));
    } catch (SourceException e) {
      return false;
    }
    return !tokens.isEmpty() && looksLikeCode(tokens) && (parses(text) || parses("func __f():\n\t" + statement(text)));
  }

  private static boolean looksLikeCode(List<Token> tokens) {
    Token first = tokens.get(0);
    if (first.kind() == TokenKind.ANNOTATION || HEADERS.contains(first.text())) return true;
    if (tokens.size() == 1) return JUMPS.contains(first.text());
    for (int i = 1; i < tokens.size(); i++) {
      Token token = tokens.get(i);
      Token before = tokens.get(i - 1);
      boolean operand = before.kind() == TokenKind.NODE_PATH || TokenRanges.is(before, ")") || TokenRanges.is(before, "]")
          || (before.kind() == TokenKind.IDENTIFIER && (!Keywords.isReserved(before.text()) || before.text().equals("preload")));
      if (token.kind() == TokenKind.OPERATOR && ASSIGNMENTS.contains(token.text())) return true;
      // Code writes calls and subscripts without a space; prose such as "iOS-Specific (OpenIAP)" does not.
      boolean adjacent = token.offset() == before.endOffset();
      if ((TokenRanges.is(token, "(") || TokenRanges.is(token, "[")) && operand && adjacent) return true;
    }
    return false;
  }

  /** A block header gets a body so that it parses alone; elif and else get the if they continue. */
  private static String statement(String text) {
    String body = text.endsWith(":") ? text + " pass" : text;
    return text.startsWith("elif") || text.startsWith("else") ? "if true: pass\n\t" + body : body;
  }

  private static boolean parses(String source) {
    try {
      GDScriptParser.parse(GDScriptLexer.lex(source.endsWith(":") ? source + " pass" : source));
      return true;
    } catch (SourceException e) {
      return false;
    }
  }
}
