package org.orebron.gdscript.checks;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.orebron.gdscript.checks.Symbols.Declaration;
import org.orebron.gdscript.checks.Symbols.Kind;
import org.orebron.gdscript.lexer.Token;
import org.orebron.gdscript.lexer.TokenKind;

/**
 * Calls and references on engine singletons and classes, such as {@code OS.execute(...)}, as security rules match
 * them without types (security plan level L1): the receiver must be written out, must not follow a '.', and no
 * declaration visible at the call may hide it.
 */
final class Calls {
  /** A matched call; {@code arguments} holds each argument's significant tokens, empty without parentheses. */
  record Call(Token receiver, Token member, List<List<Token>> arguments) {
    Violation violation(Check check, String message) {
      return receiver.line() == member.line()
          ? new Violation(check.ruleKey(), receiver.line(), receiver.column(), member.endColumn(), message)
          : Violation.at(check, member, message);
    }

    List<Token> argument(int index) { return index < arguments.size() ? arguments.get(index) : List.of(); }
  }

  private final Symbols symbols;
  private final List<Token> tokens;

  Calls(Symbols symbols) {
    this.symbols = symbols;
    this.tokens = TokenRanges.significant(symbols.scopes.tokens);
  }

  /** {@code receiver.member} for one of the members, with its arguments when it is called. */
  List<Call> on(String receiver, Set<String> members) {
    var calls = new ArrayList<Call>();
    for (int i = 0; i + 2 < tokens.size(); i++) {
      if (!TokenRanges.is(tokens.get(i), receiver) || !TokenRanges.is(tokens.get(i + 1), ".")) continue;
      if ((i > 0 && TokenRanges.is(tokens.get(i - 1), ".")) || hidden(tokens.get(i))) continue;
      Token member = tokens.get(i + 2);
      if (members.contains(member.text())) calls.add(new Call(tokens.get(i), member, arguments(i + 3)));
    }
    return calls;
  }

  /** Calls of a global function, not a method on another object. */
  List<Call> global(String function) {
    var calls = new ArrayList<Call>();
    for (int i = 0; i + 1 < tokens.size(); i++) {
      if (!TokenRanges.is(tokens.get(i), function) || !TokenRanges.is(tokens.get(i + 1), "(")) continue;
      if ((i > 0 && TokenRanges.is(tokens.get(i - 1), ".")) || hidden(tokens.get(i))) continue;
      calls.add(new Call(tokens.get(i), tokens.get(i), arguments(i + 1)));
    }
    return calls;
  }

  /** A single string literal, or a name that refers to a constant of this file. */
  boolean isConstant(List<Token> argument) {
    if (argument.size() != 1) return false;
    Token token = argument.get(0);
    if (token.kind() == TokenKind.STRING) return true;
    if (token.kind() != TokenKind.IDENTIFIER) return false;
    Declaration declaration = symbols.declarationOf(token);
    return declaration != null && (declaration.kind == Kind.MEMBER_CONSTANT || declaration.kind == Kind.LOCAL_CONSTANT);
  }

  /** The value of a single plain string literal, or null. */
  static String stringValue(List<Token> argument) {
    if (argument.size() != 1 || argument.get(0).kind() != TokenKind.STRING) return null;
    String text = argument.get(0).text();
    if (text.startsWith("&") || text.startsWith("^")) return null;
    if (text.startsWith("r")) text = text.substring(1);
    int quote = text.startsWith("\"\"\"") || text.startsWith("'''") ? 3 : 1;
    return text.length() >= 2 * quote ? text.substring(quote, text.length() - quote) : null;
  }

  private boolean hidden(Token name) { return symbols.declarationOf(name) != null; }

  /** The arguments of the call whose '(' is at {@code open}; empty when there is none. */
  private List<List<Token>> arguments(int open) {
    var arguments = new ArrayList<List<Token>>();
    if (open >= tokens.size() || !TokenRanges.is(tokens.get(open), "(")) return arguments;
    var current = new ArrayList<Token>();
    int depth = 0;
    for (int i = open; i < tokens.size(); i++) {
      Token token = tokens.get(i);
      if (TokenRanges.is(token, "(") || TokenRanges.is(token, "[") || TokenRanges.is(token, "{")) {
        if (depth++ == 0) continue;
      } else if (TokenRanges.is(token, ")") || TokenRanges.is(token, "]") || TokenRanges.is(token, "}")) {
        if (--depth == 0) break;
      } else if (depth == 1 && TokenRanges.is(token, ",")) {
        arguments.add(current);
        current = new ArrayList<>();
        continue;
      }
      current.add(token);
    }
    if (!current.isEmpty()) arguments.add(current);
    return arguments;
  }

  /** The elements of an array literal argument, or null when the argument is something else. */
  static List<List<Token>> arrayElements(List<Token> argument) {
    if (argument.size() < 2 || !TokenRanges.is(argument.get(0), "[") || !TokenRanges.is(argument.get(argument.size() - 1), "]")) return null;
    var elements = new ArrayList<List<Token>>();
    var current = new ArrayList<Token>();
    int depth = 0;
    for (int i = 1; i < argument.size() - 1; i++) {
      Token token = argument.get(i);
      if (TokenRanges.is(token, "(") || TokenRanges.is(token, "[") || TokenRanges.is(token, "{")) depth++;
      else if (TokenRanges.is(token, ")") || TokenRanges.is(token, "]") || TokenRanges.is(token, "}")) depth--;
      else if (depth == 0 && TokenRanges.is(token, ",")) {
        elements.add(current);
        current = new ArrayList<>();
        continue;
      }
      current.add(token);
    }
    if (!current.isEmpty()) elements.add(current);
    return elements;
  }
}
