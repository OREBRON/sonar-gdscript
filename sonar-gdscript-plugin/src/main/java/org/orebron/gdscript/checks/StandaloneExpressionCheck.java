package org.orebron.gdscript.checks;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.orebron.gdscript.lexer.Keywords;
import org.orebron.gdscript.lexer.Token;
import org.orebron.gdscript.lexer.TokenKind;
import org.orebron.gdscript.parser.SyntaxKind;
import org.orebron.gdscript.parser.SyntaxNode;

/**
 * Expression statements whose value is thrown away and that do nothing else: anything but a call, an assignment, an
 * await or a string used as a comment. A conditional expression gets Godot's separate standalone-ternary message.
 */
public final class StandaloneExpressionCheck extends GodotWarningCheck {
  private static final Set<SyntaxKind> STATEMENT_BLOCKS = EnumSet.of(SyntaxKind.FUNCTION, SyntaxKind.ACCESSOR, SyntaxKind.LAMBDA,
      SyntaxKind.IF, SyntaxKind.ELIF, SyntaxKind.ELSE, SyntaxKind.FOR, SyntaxKind.WHILE, SyntaxKind.MATCH_BRANCH,
      SyntaxKind.CATCH_ALL_BRANCH);
  private static final Set<String> ASSIGNMENTS = Set.of("=", "+=", "-=", "*=", "/=", "%=", "**=", "<<=", ">>=", "&=", "|=", "^=");
  /** Operators that do not end the expression's last call: member access and brackets. */
  private static final Set<String> POSTFIX = Set.of(".", "(", ")", "[", "]", "{", "}", ",", ":");
  private static final String TERNARY = "Remove this conditional expression";

  @Override public String key() { return "standalone-expression"; }
  @Override public String name() { return "Expressions should not be used as statements without effect"; }
  @Override public String severity() { return "MAJOR"; }
  @Override String warning() { return "standalone_expression"; }
  @Override String warningOf(Violation violation) { return violation.message().startsWith(TERNARY) ? "standalone_ternary" : warning(); }

  @Override public String htmlDescription() {
    return "<p>A statement that only computes a value, such as <code>x + 1</code>, <code>a == b</code> or "
        + "<code>node.visible</code>, has no effect; it is usually a typo for an assignment or a call. Calls, "
        + "assignments, <code>await</code> and strings used as comments are fine.</p>" + godotNote(warning());
  }

  @Override List<Violation> find(SourceFile file) {
    var violations = new ArrayList<Violation>();
    if (file.tree() == null) return violations;
    var scopes = new Scopes(file);
    file.tree().descendantsAndSelf().filter(block -> STATEMENT_BLOCKS.contains(block.kind())).forEach(block -> {
      List<Integer> starts = scopes.statements(block);
      for (int s = 0; s < starts.size(); s++) {
        int start = starts.get(s);
        int end = Math.min(scopes.statementEnd(start), s + 1 < starts.size() ? starts.get(s + 1) : scopes.indexOf(block.last()) + 1);
        String message = classify(scopes, block, start, end);
        if (message != null) violations.add(Violation.at(this, scopes.tokens.get(start), message));
      }
    });
    return violations;
  }

  /** Null when the statement is not a standalone expression. */
  private static String classify(Scopes scopes, SyntaxNode block, int start, int end) {
    Token first = scopes.tokens.get(start);
    // match is not reserved, so the tree tells a match statement from an expression named match.
    if (block.children().stream().anyMatch(child -> child.token().equals(first))) return null;
    if (first.kind() == TokenKind.IDENTIFIER && Keywords.isReserved(first.text()) && !Keywords.isOperand(first.text())
        && !first.text().equals("not")) {
      return null; // a statement keyword, await, preload, or a lambda (an error of its own)
    }
    List<Token> tokens = TokenRanges.significant(scopes.tokens.subList(start, Math.min(end, scopes.tokens.size())));
    // Strings may stand alone as comments; StringName and NodePath literals may not.
    if (tokens.size() == 1 && first.kind() == TokenKind.STRING && !first.text().startsWith("&") && !first.text().startsWith("^")) return null;
    int depth = 0;
    boolean ternary = false;
    boolean operator = false;
    for (Token token : tokens) {
      if (token.kind() == TokenKind.OPERATOR && ASSIGNMENTS.contains(token.text()) && depth == 0) return null;
      if (TokenRanges.is(token, "(") || TokenRanges.is(token, "[") || TokenRanges.is(token, "{")) depth++;
      else if (TokenRanges.is(token, ")") || TokenRanges.is(token, "]") || TokenRanges.is(token, "}")) depth--;
      else if (depth == 0 && TokenRanges.is(token, "if")) ternary = true;
      else if (depth == 0 && isOperator(token)) operator = true;
    }
    if (ternary) return TERNARY + ", or use its value; on its own it has no effect.";
    if (!operator && endsWithCall(tokens)) return null;
    return "Remove this expression, or use its value; on its own it has no effect.";
  }

  private static boolean isOperator(Token token) {
    if (token.kind() == TokenKind.OPERATOR) return !POSTFIX.contains(token.text());
    return token.kind() == TokenKind.IDENTIFIER && switch (token.text()) {
      case "and", "or", "not", "in", "is", "as" -> true;
      default -> false;
    };
  }

  /** The expression's last operation is a call: it ends with ')' whose '(' follows an operand; outer parentheses do not count. */
  private static boolean endsWithCall(List<Token> expression) {
    List<Token> tokens = expression;
    while (tokens.size() > 2 && TokenRanges.is(tokens.get(0), "(") && closingOf(tokens, 0) == tokens.size() - 1) {
      tokens = tokens.subList(1, tokens.size() - 1);
    }
    if (tokens.isEmpty() || !TokenRanges.is(tokens.get(tokens.size() - 1), ")")) return false;
    int depth = 0;
    for (int i = tokens.size() - 1; i > 0; i--) {
      if (TokenRanges.is(tokens.get(i), ")")) depth++;
      else if (TokenRanges.is(tokens.get(i), "(") && --depth == 0) {
        Token callee = tokens.get(i - 1);
        return callee.kind() == TokenKind.IDENTIFIER || callee.kind() == TokenKind.NODE_PATH
            || TokenRanges.is(callee, ")") || TokenRanges.is(callee, "]");
      }
    }
    return false;
  }

  private static int closingOf(List<Token> tokens, int open) {
    int depth = 0;
    for (int i = open; i < tokens.size(); i++) {
      if (TokenRanges.is(tokens.get(i), "(")) depth++;
      else if (TokenRanges.is(tokens.get(i), ")") && --depth == 0) return i;
    }
    return -1;
  }
}
