package org.orebron.gdscript.checks;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.orebron.gdscript.checks.Symbols.Declaration;
import org.orebron.gdscript.checks.Symbols.Kind;

/** A local declaration with the name of a member of its own class; base classes and engine names need type data. */
public final class ShadowedVariableCheck extends GodotWarningCheck {
  private static final Map<Kind, String> LOCAL_KINDS = Map.of(Kind.LOCAL_VARIABLE, "variable", Kind.LOCAL_CONSTANT, "constant",
      Kind.PARAMETER, "function parameter", Kind.LOOP_VARIABLE, "\"for\" iterator variable", Kind.BIND, "pattern bind");
  private static final Map<Kind, String> MEMBER_KINDS = Map.of(Kind.MEMBER_VARIABLE, "variable", Kind.MEMBER_CONSTANT, "constant",
      Kind.SIGNAL, "signal", Kind.FUNCTION, "function", Kind.ENUM, "enum", Kind.ENUM_VALUE, "enum value", Kind.CLASS, "class");

  @Override public String key() { return "shadowed-variable"; }
  @Override public String name() { return "Local names should not shadow class members"; }
  @Override public String severity() { return "MAJOR"; }
  @Override String warning() { return "shadowed_variable"; }

  @Override public String htmlDescription() {
    return "<p>A local variable, constant, parameter, loop variable or pattern bind with the name of a member of the "
        + "same class hides that member: code that seems to use the member uses the local instead. Rename the local.</p>"
        + godotNote(warning());
  }

  @Override List<Violation> find(SourceFile file) {
    Symbols symbols = Symbols.of(file);
    var violations = new ArrayList<Violation>();
    if (symbols == null) return violations;
    for (Declaration local : symbols.declarations) {
      if (!LOCAL_KINDS.containsKey(local.kind)) continue;
      symbols.membersOf(symbols.scopes.classOf(local.scope)).stream()
          .filter(member -> member.text().equals(local.text()) && MEMBER_KINDS.containsKey(member.kind)).findFirst()
          .ifPresent(member -> violations.add(Violation.at(this, local.name, "The local " + LOCAL_KINDS.get(local.kind) + " \""
              + local.text() + "\" shadows the " + MEMBER_KINDS.get(member.kind) + " declared at line " + member.name.line() + "; rename it.")));
    }
    return violations;
  }
}
