package org.orebron.gdscript.checks;

import java.util.ArrayList;
import java.util.List;
import org.orebron.gdscript.checks.Symbols.Declaration;
import org.orebron.gdscript.checks.Symbols.Kind;
import org.orebron.gdscript.lexer.Token;
import org.orebron.gdscript.lexer.TokenKind;

/** Class variables with their annotations and initializer tokens, for the rules about member declarations. */
record MemberVariables(Declaration declaration, List<String> annotations, boolean isStatic, List<Token> initializer) {
  static List<MemberVariables> of(Symbols symbols) {
    var variables = new ArrayList<MemberVariables>();
    for (Declaration declaration : symbols.declarations) {
      if (declaration.kind != Kind.MEMBER_VARIABLE) continue;
      int keyword = symbols.scopes.indexOf(declaration.keyword);
      var annotations = new ArrayList<String>();
      boolean isStatic = false;
      for (int i = keyword - 1; i >= 0; i--) {
        Token token = symbols.scopes.tokens.get(i);
        if (!TokenRanges.isSignificant(token)) continue;
        if (token.kind() == TokenKind.ANNOTATION) annotations.add(token.text());
        else if (TokenRanges.is(token, "static")) isStatic = true;
        else if (TokenRanges.is(token, ")")) i = openingParenthesis(symbols.scopes.tokens, i);
        else break;
      }
      variables.add(new MemberVariables(declaration, annotations, isStatic, initializer(symbols.scopes, keyword)));
    }
    return variables;
  }

  boolean hasAnnotation(String prefix) { return annotations.stream().anyMatch(name -> name.startsWith(prefix)); }

  /** The significant tokens after the first '=' or ':=' outside brackets, up to the end of the declaration. */
  private static List<Token> initializer(Scopes scopes, int keyword) {
    int end = scopes.statementEnd(keyword);
    int depth = 0;
    for (int i = keyword; i < end; i++) {
      Token token = scopes.tokens.get(i);
      if (TokenRanges.is(token, "(") || TokenRanges.is(token, "[") || TokenRanges.is(token, "{")) depth++;
      else if (TokenRanges.is(token, ")") || TokenRanges.is(token, "]") || TokenRanges.is(token, "}")) depth--;
      else if (depth == 0 && (TokenRanges.is(token, "=") || TokenRanges.is(token, ":="))) {
        List<Token> value = TokenRanges.significant(scopes.tokens.subList(i + 1, end));
        // An inline accessor list after the value starts with ':' outside brackets.
        int length = value.size();
        int nested = 0;
        for (int j = 0; j < value.size(); j++) {
          Token t = value.get(j);
          if (TokenRanges.is(t, "(") || TokenRanges.is(t, "[") || TokenRanges.is(t, "{")) nested++;
          else if (TokenRanges.is(t, ")") || TokenRanges.is(t, "]") || TokenRanges.is(t, "}")) nested--;
          else if (nested == 0 && TokenRanges.is(t, ":")) {
            length = j;
            break;
          }
        }
        return value.subList(0, length);
      }
    }
    return List.of();
  }

  private static int openingParenthesis(List<Token> tokens, int close) {
    int depth = 0;
    for (int i = close; i >= 0; i--) {
      if (TokenRanges.is(tokens.get(i), ")")) depth++;
      else if (TokenRanges.is(tokens.get(i), "(") && --depth == 0) return i;
    }
    return 0;
  }
}
