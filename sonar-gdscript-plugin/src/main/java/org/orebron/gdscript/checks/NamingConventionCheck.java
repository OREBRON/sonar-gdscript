package org.orebron.gdscript.checks;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;
import org.orebron.gdscript.lexer.Keywords;
import org.orebron.gdscript.lexer.Token;
import org.orebron.gdscript.lexer.TokenKind;
import org.orebron.gdscript.parser.SyntaxKind;

/**
 * Names that break the GDScript style guide: snake_case functions, signals, variables and parameters, PascalCase
 * classes and enums, CONSTANT_CASE enum members, and CONSTANT_CASE or PascalCase constants (PascalCase for preloaded
 * classes and scenes). Leading underscores, which mark private members and unused parameters, are ignored.
 */
public final class NamingConventionCheck implements Check {
  private enum Convention {
    SNAKE("snake_case", name -> name.codePoints().noneMatch(Character::isUpperCase)),
    PASCAL("PascalCase", name -> !name.isEmpty() && Character.isUpperCase(name.codePointAt(0)) && name.indexOf('_') < 0),
    CONSTANT("CONSTANT_CASE", name -> name.codePoints().noneMatch(Character::isLowerCase)),
    CONSTANT_OR_PASCAL("CONSTANT_CASE or PascalCase", name -> CONSTANT.accepts(name) || PASCAL.accepts(name));

    private final String label;
    private final Predicate<String> rule;

    Convention(String label, Predicate<String> rule) {
      this.label = label;
      this.rule = rule;
    }

    boolean accepts(String name) { return rule.test(name.replaceFirst("^_+", "")); }
  }

  @Override public String key() { return "naming-convention"; }
  @Override public String name() { return "Names should follow the GDScript style guide"; }
  @Override public String severity() { return "MINOR"; }
  @Override public List<String> tags() { return List.of("convention"); }

  @Override public String htmlDescription() {
    return "<p>The <a href=\"https://docs.godotengine.org/en/stable/tutorials/scripting/gdscript/gdscript_styleguide.html\">"
        + "GDScript style guide</a> names functions, signals, variables and parameters in <code>snake_case</code>, "
        + "classes and enums in <code>PascalCase</code>, and constants and enum members in <code>CONSTANT_CASE</code>. "
        + "Constants holding a preloaded class or scene may use <code>PascalCase</code>. Leading underscores are "
        + "ignored.</p>";
  }

  @Override public List<Violation> check(SourceFile file, int unused) {
    var violations = new ArrayList<Violation>();
    List<Token> tokens = TokenRanges.significant(file.tokens());
    for (int i = 0; i + 1 < tokens.size(); i++) {
      Token name = tokens.get(i + 1);
      if (name.kind() != TokenKind.IDENTIFIER || Keywords.isReserved(name.text())) continue;
      switch (tokens.get(i).kind() == TokenKind.IDENTIFIER ? tokens.get(i).text() : "") {
        case "func" -> verify(name, "function", Convention.SNAKE, violations);
        case "signal" -> verify(name, "signal", Convention.SNAKE, violations);
        case "var", "for" -> verify(name, "variable", Convention.SNAKE, violations);
        case "const" -> verify(name, "constant", Convention.CONSTANT_OR_PASCAL, violations);
        case "class", "class_name" -> verify(name, "class", Convention.PASCAL, violations);
        case "enum" -> verify(name, "enum", Convention.PASCAL, violations);
        default -> { }
      }
    }
    enumMembers(tokens, violations);
    if (file.tree() != null) file.tree().descendantsAndSelf().filter(node -> node.kind() == SyntaxKind.PARAMETER)
        .forEach(parameter -> verify(parameter.token(), "parameter", Convention.SNAKE, violations));
    violations.sort(Comparator.comparingInt(Violation::line).thenComparingInt(Violation::column));
    return violations;
  }

  /** The first name of each element in {@code enum [Name] { ... }}. */
  private void enumMembers(List<Token> tokens, List<Violation> violations) {
    for (int i = 0; i < tokens.size(); i++) {
      if (!TokenRanges.is(tokens.get(i), "enum")) continue;
      int open = i + 1 < tokens.size() && TokenRanges.is(tokens.get(i + 1), "{") ? i + 1 : i + 2;
      int depth = 0;
      boolean member = true;
      for (int j = open; j < tokens.size(); j++) {
        Token token = tokens.get(j);
        if (TokenRanges.is(token, "(") || TokenRanges.is(token, "[") || TokenRanges.is(token, "{")) depth++;
        else if (TokenRanges.is(token, ")") || TokenRanges.is(token, "]") || TokenRanges.is(token, "}")) {
          if (--depth == 0) break;
        } else if (depth == 1 && TokenRanges.is(token, ",")) member = true;
        else if (depth == 1 && member && token.kind() == TokenKind.IDENTIFIER) {
          verify(token, "enum member", Convention.CONSTANT, violations);
          member = false;
        } else member = false;
      }
    }
  }

  private void verify(Token name, String kind, Convention convention, List<Violation> violations) {
    if (!convention.accepts(name.text())) {
      violations.add(Violation.at(this, name, "Rename this " + kind + " \"" + name.text() + "\" to match the "
          + convention.label + " naming convention."));
    }
  }
}
