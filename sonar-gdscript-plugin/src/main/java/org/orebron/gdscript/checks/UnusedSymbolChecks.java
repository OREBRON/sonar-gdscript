package org.orebron.gdscript.checks;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import org.orebron.gdscript.checks.Symbols.Declaration;
import org.orebron.gdscript.checks.Symbols.Kind;
import org.orebron.gdscript.lexer.Token;
import org.orebron.gdscript.lexer.TokenKind;
import org.orebron.gdscript.parser.SyntaxKind;
import org.orebron.gdscript.parser.SyntaxNode;

/** Godot's unused-name warnings: declarations that {@link Symbols} finds no use of. */
final class UnusedSymbolChecks {
  private UnusedSymbolChecks() {}

  /** A declaration rule: which kinds it reports, and its message. */
  abstract static class UnusedCheck extends GodotWarningCheck {
    abstract Set<Kind> kinds();

    /** Godot leaves names that start with an underscore alone. */
    boolean reports(Declaration declaration) { return !declaration.text().startsWith("_"); }

    abstract String message(Declaration declaration, Symbols symbols);

    @Override List<Violation> find(SourceFile file) {
      Symbols symbols = Symbols.of(file);
      if (symbols == null) return List.of();
      Predicate<Declaration> unused = declaration -> declaration.uses == 0 && kinds().contains(declaration.kind) && reports(declaration);
      return symbols.declarations.stream().filter(unused)
          .map(declaration -> Violation.at(this, declaration.name, message(declaration, symbols))).toList();
    }
  }

  static final class UnusedVariableCheck extends UnusedCheck {
    @Override public String key() { return "unused-variable"; }
    @Override public String name() { return "Unused local variables should be removed"; }
    @Override public String severity() { return "MINOR"; }
    @Override String warning() { return "unused_variable"; }
    @Override Set<Kind> kinds() { return EnumSet.of(Kind.LOCAL_VARIABLE, Kind.BIND); }

    @Override public String htmlDescription() {
      return "<p>A local variable or match pattern bind that is never read is dead code, or a sign that the wrong "
          + "variable is used somewhere. Remove it, or prefix its name with an underscore when it is unused on purpose.</p>"
          + godotNote(warning());
    }

    @Override String message(Declaration declaration, Symbols symbols) {
      return "Remove the unused local variable \"" + declaration.text() + "\", or rename it \"_" + declaration.text() + "\".";
    }
  }

  static final class UnusedLocalConstantCheck extends UnusedCheck {
    @Override public String key() { return "unused-local-constant"; }
    @Override public String name() { return "Unused local constants should be removed"; }
    @Override public String severity() { return "MINOR"; }
    @Override String warning() { return "unused_local_constant"; }
    @Override Set<Kind> kinds() { return EnumSet.of(Kind.LOCAL_CONSTANT); }

    @Override public String htmlDescription() {
      return "<p>A constant declared in a function and never used is dead code. Remove it, or prefix its name with an "
          + "underscore when it is unused on purpose.</p>" + godotNote(warning());
    }

    @Override String message(Declaration declaration, Symbols symbols) {
      return "Remove the unused local constant \"" + declaration.text() + "\", or rename it \"_" + declaration.text() + "\".";
    }
  }

  static final class UnusedParameterCheck extends UnusedCheck {
    @Override public String key() { return "unused-parameter"; }
    @Override public String name() { return "Unused function parameters should be removed"; }
    @Override public String severity() { return "MAJOR"; }
    @Override String warning() { return "unused_parameter"; }
    @Override Set<Kind> kinds() { return EnumSet.of(Kind.PARAMETER); }
    /** Godot does not report a setter's unused value parameter. */
    @Override boolean reports(Declaration declaration) {
      return super.reports(declaration) && declaration.function.kind() != SyntaxKind.ACCESSOR;
    }

    @Override public String htmlDescription() {
      return "<p>A parameter that the function never reads misleads callers. Remove it, or prefix it with an underscore "
          + "when the signature is fixed, for example by a signal or an overridden engine callback such as "
          + "<code>_process(_delta)</code>.</p>" + godotNote(warning());
    }

    @Override String message(Declaration declaration, Symbols symbols) {
      return "Remove the unused parameter \"" + declaration.text() + "\" of \"" + functionName(declaration.function, symbols)
          + "()\", or rename it \"_" + declaration.text() + "\".";
    }
  }

  static final class UnusedPrivateClassVariableCheck extends UnusedCheck {
    @Override public String key() { return "unused-private-class-variable"; }
    @Override public String name() { return "Unused private class variables should be removed"; }
    @Override public String severity() { return "MAJOR"; }
    @Override String warning() { return "unused_private_class_variable"; }
    @Override Set<Kind> kinds() { return EnumSet.of(Kind.MEMBER_VARIABLE); }
    @Override boolean reports(Declaration declaration) { return declaration.text().startsWith("_"); }

    @Override public String htmlDescription() {
      return "<p>A class variable whose name starts with an underscore is private by convention; when the class never "
          + "uses it, it is dead code. Remove it.</p>" + godotNote(warning());
    }

    @Override String message(Declaration declaration, Symbols symbols) {
      return "Remove the unused private class variable \"" + declaration.text() + "\".";
    }
  }

  static final class UnusedSignalCheck extends UnusedCheck {
    @Override public String key() { return "unused-signal"; }
    @Override public String name() { return "Unused signals should be removed"; }
    @Override public String severity() { return "MINOR"; }
    @Override String warning() { return "unused_signal"; }
    @Override Set<Kind> kinds() { return EnumSet.of(Kind.SIGNAL); }
    @Override boolean reports(Declaration declaration) { return true; }

    @Override public String htmlDescription() {
      return "<p>A signal that its class never emits, connects or refers to is either dead code or emitted only from "
          + "other scripts. Remove it, emit it from its own class, or silence the warning where another class emits it."
          + "</p>" + godotNote(warning());
    }

    @Override String message(Declaration declaration, Symbols symbols) {
      return "The signal \"" + declaration.text() + "\" is declared but never used in its class.";
    }
  }

  /** The name Godot gives the function in its messages. */
  static String functionName(SyntaxNode function, Symbols symbols) {
    if (function.kind() != SyntaxKind.LAMBDA) return function.token().text();
    Token next = symbols.scopes.significantAfter(symbols.scopes.indexOf(function.token()));
    return next != null && next.kind() == TokenKind.IDENTIFIER ? next.text() : "<anonymous lambda>";
  }
}
