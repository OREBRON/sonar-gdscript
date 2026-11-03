package org.orebron.gdscript.checks;

import java.util.List;
import org.orebron.gdscript.lexer.Token;
import org.orebron.gdscript.lexer.TokenKind;

/** Godot's warnings about when class variables get their values: before or after the node enters the tree. */
final class OnreadyChecks {
  private OnreadyChecks() {}

  /** A non-static class variable initialized with $, % or get_node() but without @onready. */
  static final class GetNodeWithoutOnreadyCheck extends GodotWarningCheck {
    @Override public String key() { return "get-node-without-onready"; }
    @Override public String name() { return "Class variables initialized with get_node() should use @onready"; }
    @Override public String severity() { return "MAJOR"; }
    @Override String warning() { return "get_node_default_without_onready"; }

    @Override public String htmlDescription() {
      return "<p>A class variable's default value is computed when the object is created, before the node enters the "
          + "scene tree, so <code>$Child</code>, <code>%Unique</code> or <code>get_node()</code> there finds nothing. "
          + "Add <code>@onready</code> so the value is set just before <code>_ready()</code>.</p>" + godotNote(warning());
    }

    @Override List<Violation> find(SourceFile file) {
      Symbols symbols = Symbols.of(file);
      if (symbols == null) return List.of();
      return MemberVariables.of(symbols).stream().filter(v -> !v.isStatic() && !v.hasAnnotation("@onready"))
          .filter(v -> syntax(v.initializer()) != null)
          .map(v -> Violation.at(this, v.declaration().name, "Add \"@onready\": the default value uses \"" + syntax(v.initializer())
              + "\", which finds no node before \"_ready()\"."))
          .toList();
    }

    /** "$", "%" or "get_node()" when the whole value, before an optional cast, gets a node; null otherwise. */
    private static String syntax(List<Token> value) {
      List<Token> operand = value;
      for (int i = 0; i < value.size(); i++) {
        if (TokenRanges.is(value.get(i), "as")) {
          operand = value.subList(0, i);
          break;
        }
      }
      if (operand.size() == 1 && operand.get(0).kind() == TokenKind.NODE_PATH) return operand.get(0).text().startsWith("%") ? "%" : "$";
      int call = operand.size() >= 3 && TokenRanges.is(operand.get(0), "self") && TokenRanges.is(operand.get(1), ".") ? 2 : 0;
      boolean getNode = operand.size() >= call + 3 && TokenRanges.is(operand.get(call), "get_node")
          && TokenRanges.is(operand.get(call + 1), "(") && TokenRanges.is(operand.get(operand.size() - 1), ")")
          && closes(operand, call + 1);
      return getNode ? "get_node()" : null;
    }

    /** Whether the '(' at {@code open} closes at the last token, so the value is the call itself. */
    private static boolean closes(List<Token> tokens, int open) {
      int depth = 0;
      for (int i = open; i < tokens.size(); i++) {
        if (TokenRanges.is(tokens.get(i), "(")) depth++;
        else if (TokenRanges.is(tokens.get(i), ")") && --depth == 0) return i == tokens.size() - 1;
      }
      return false;
    }
  }

  /** A class variable with both @onready and an @export annotation: the scene's exported value gets overwritten. */
  static final class OnreadyWithExportCheck extends GodotWarningCheck {
    @Override public String key() { return "onready-with-export"; }
    @Override public String name() { return "Exported variables should not use @onready"; }
    @Override public String severity() { return "MAJOR"; }
    @Override String warning() { return "onready_with_export"; }

    @Override public String htmlDescription() {
      return "<p><code>@onready</code> assigns the default value just before <code>_ready()</code>, after the scene "
          + "has set the <code>@export</code> value, so the value chosen in the editor is lost. Use one annotation or "
          + "the other.</p>" + godotNote(warning());
    }

    @Override List<Violation> find(SourceFile file) {
      Symbols symbols = Symbols.of(file);
      if (symbols == null) return List.of();
      return MemberVariables.of(symbols).stream().filter(v -> v.hasAnnotation("@onready") && v.hasAnnotation("@export"))
          .map(v -> Violation.at(this, v.declaration().name, "Remove \"@onready\" or \"@export\": \"@onready\" overwrites the exported value."))
          .toList();
    }
  }
}
