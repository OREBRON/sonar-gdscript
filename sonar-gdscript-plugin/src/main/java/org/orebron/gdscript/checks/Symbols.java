package org.orebron.gdscript.checks;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.orebron.gdscript.lexer.Keywords;
import org.orebron.gdscript.lexer.Token;
import org.orebron.gdscript.lexer.TokenKind;
import org.orebron.gdscript.parser.SyntaxKind;
import org.orebron.gdscript.parser.SyntaxNode;

/**
 * The names a file declares and how often each is used, resolved as Godot's analyzer does without types: a name
 * refers to the latest local declaration visible in an enclosing block, otherwise to a member of the enclosing class
 * or an outer class. Member access on other objects ({@code a.b}) and Lua-style dictionary keys are not uses;
 * {@code self.b} is. A signal is also used by its name in {@code emit_signal}, {@code connect}, {@code disconnect},
 * {@code is_connected}, {@code has_signal} or {@code Signal(self, ...)}.
 */
final class Symbols {
  enum Kind {
    LOCAL_VARIABLE, LOCAL_CONSTANT, PARAMETER, LOOP_VARIABLE, BIND,
    MEMBER_VARIABLE, MEMBER_CONSTANT, SIGNAL, FUNCTION, ENUM, ENUM_VALUE, CLASS;

    boolean isLocal() { return LOCALS.contains(this); }
  }

  private static final Set<Kind> LOCALS = EnumSet.of(Kind.LOCAL_VARIABLE, Kind.LOCAL_CONSTANT, Kind.PARAMETER,
      Kind.LOOP_VARIABLE, Kind.BIND);
  private static final Set<String> SIGNAL_CALLS = Set.of("emit_signal", "connect", "disconnect", "is_connected", "has_signal");

  /** A declared name; {@code scope} is the block it is visible in, from {@code visibleFrom} on. */
  static final class Declaration {
    final Kind kind;
    final Token name;
    final SyntaxNode scope;
    final int visibleFrom;
    /** The declaring function or lambda for locals and parameters, null for members. */
    final SyntaxNode function;
    final Token keyword;
    int uses;

    Declaration(Kind kind, Token name, SyntaxNode scope, int visibleFrom, SyntaxNode function, Token keyword) {
      this.kind = kind;
      this.name = name;
      this.scope = scope;
      this.visibleFrom = visibleFrom;
      this.function = function;
      this.keyword = keyword;
    }

    String text() { return name.text(); }
  }

  final Scopes scopes;
  final List<Declaration> declarations = new ArrayList<>();
  private final Map<String, List<Declaration>> byName = new HashMap<>();
  private final Set<Integer> declarationOffsets = new HashSet<>();

  private Symbols(Scopes scopes) { this.scopes = scopes; }

  /** Null when the file did not parse. */
  static Symbols of(SourceFile file) {
    if (file.tree() == null) return null;
    var symbols = new Symbols(new Scopes(file));
    symbols.declare();
    symbols.resolve();
    return symbols;
  }

  /** Members of the given class, by name. */
  List<Declaration> membersOf(SyntaxNode type) {
    return declarations.stream().filter(d -> !d.kind.isLocal() && d.scope == type).toList();
  }

  private void declare() {
    List<Token> tokens = scopes.tokens;
    for (int i = 0; i < tokens.size(); i++) {
      Token token = tokens.get(i);
      if (token.kind() != TokenKind.IDENTIFIER) continue;
      Token next = scopes.significantAfter(i);
      if (next == null || next.kind() != TokenKind.IDENTIFIER || Keywords.isReserved(next.text())) {
        if (token.text().equals("enum")) enumeration(i, null);
        continue;
      }
      SyntaxNode block = scopes.blockAt(i);
      SyntaxNode function = scopes.functionOf(block);
      SyntaxNode type = scopes.classOf(block);
      switch (token.text()) {
        case "var" -> {
          if (function == null) add(Kind.MEMBER_VARIABLE, next, type, 0, null, token);
          else if (isPattern(block, token)) add(Kind.BIND, next, block, next.offset(), function, token);
          else add(Kind.LOCAL_VARIABLE, next, block, visibleAfter(i), function, token);
        }
        case "const" -> {
          if (function == null) add(Kind.MEMBER_CONSTANT, next, type, 0, null, token);
          else add(Kind.LOCAL_CONSTANT, next, block, visibleAfter(i), function, token);
        }
        case "for" -> add(Kind.LOOP_VARIABLE, next, block, next.offset(), function, token);
        case "signal" -> add(Kind.SIGNAL, next, type, 0, null, token);
        case "func" -> {
          if (function == null) add(Kind.FUNCTION, next, type, 0, null, token);
        }
        case "class" -> add(Kind.CLASS, next, type, 0, null, token);
        case "enum" -> enumeration(i, next);
        default -> { }
      }
    }
    scopes.root.descendantsAndSelf().filter(node -> node.kind() == SyntaxKind.PARAMETER).forEach(parameter -> {
      SyntaxNode function = scopes.parent(parameter);
      add(Kind.PARAMETER, parameter.token(), function, function.token().offset(), function, null);
    });
  }

  /** {@code enum Name {A, B}} declares Name; {@code enum {A, B}} declares A and B in the class. */
  private void enumeration(int index, Token name) {
    SyntaxNode type = scopes.classOf(scopes.blockAt(index));
    if (name != null) {
      add(Kind.ENUM, name, type, 0, null, scopes.tokens.get(index));
      return;
    }
    int depth = 0;
    boolean member = true;
    for (int i = index + 1; i < scopes.tokens.size(); i++) {
      Token token = scopes.tokens.get(i);
      if (!TokenRanges.isSignificant(token)) continue;
      if (TokenRanges.is(token, "{") || TokenRanges.is(token, "(") || TokenRanges.is(token, "[")) depth++;
      else if (TokenRanges.is(token, "}") || TokenRanges.is(token, ")") || TokenRanges.is(token, "]")) {
        if (--depth == 0) return;
      } else if (depth == 1 && TokenRanges.is(token, ",")) member = true;
      else if (depth == 1 && member && token.kind() == TokenKind.IDENTIFIER) {
        add(Kind.ENUM_VALUE, token, type, 0, null, null);
        member = false;
      } else member = false;
    }
  }

  /** A {@code var} in a match branch before its ':' binds a pattern variable. */
  private boolean isPattern(SyntaxNode block, Token keyword) {
    if (block.kind() != SyntaxKind.MATCH_BRANCH && block.kind() != SyntaxKind.CATCH_ALL_BRANCH) return false;
    List<Token> body = TokenRanges.body(scopes.tokens, block);
    return body.isEmpty() || keyword.offset() < body.get(0).offset();
  }

  /** A local is visible after its declaration statement, so {@code var x = x} reads an outer x. */
  private int visibleAfter(int keyword) {
    int end = scopes.statementEnd(keyword);
    return end < scopes.tokens.size() ? scopes.tokens.get(end).offset() : Integer.MAX_VALUE;
  }

  private void add(Kind kind, Token name, SyntaxNode scope, int visibleFrom, SyntaxNode function, Token keyword) {
    var declaration = new Declaration(kind, name, scope, visibleFrom, function, keyword);
    declarations.add(declaration);
    byName.computeIfAbsent(name.text(), k -> new ArrayList<>()).add(declaration);
    declarationOffsets.add(name.offset());
  }

  private void resolve() {
    List<Token> tokens = scopes.tokens;
    Deque<String> brackets = new ArrayDeque<>();
    for (int i = 0; i < tokens.size(); i++) {
      Token token = tokens.get(i);
      if (token.kind() == TokenKind.OPERATOR) {
        switch (token.text()) {
          case "(", "[", "{" -> brackets.push(token.text());
          case ")", "]", "}" -> brackets.poll();
          default -> { }
        }
        continue;
      }
      if (token.kind() == TokenKind.STRING) {
        signalName(i);
        continue;
      }
      if (token.kind() != TokenKind.IDENTIFIER || declarationOffsets.contains(token.offset())) continue;
      if (Keywords.isReserved(token.text()) || !byName.containsKey(token.text())) continue;
      Token before = scopes.significantBefore(i);
      boolean memberOnly = false;
      if (before != null && TokenRanges.is(before, ".")) {
        Token owner = scopes.significantBefore(scopes.indexOf(before));
        if (owner == null || !TokenRanges.is(owner, "self")) continue;
        memberOnly = true;
      }
      if ("{".equals(brackets.peek()) && before != null && (TokenRanges.is(before, "{") || TokenRanges.is(before, ","))) {
        Token after = scopes.significantAfter(i);
        if (after != null && TokenRanges.is(after, "=")) continue; // a Lua-style key: {name = value}
      }
      Declaration target = memberOnly ? null : local(token, i);
      if (target == null) target = member(token, i);
      if (target != null) target.uses++;
    }
  }

  /** The declaration an identifier refers to, or null for an engine name or a name from another file. */
  Declaration declarationOf(Token token) {
    if (!byName.containsKey(token.text())) return null;
    int index = scopes.indexOf(token);
    Declaration local = local(token, index);
    return local != null ? local : member(token, index);
  }

  /** The latest local declaration of the name visible at the token. */
  private Declaration local(Token token, int index) {
    Declaration found = null;
    for (Declaration declaration : byName.get(token.text())) {
      if (!declaration.kind.isLocal() || token.offset() < declaration.visibleFrom) continue;
      if (!scopes.contains(declaration.scope, token)) continue;
      if (found == null || declaration.name.offset() > found.name.offset()) found = declaration;
    }
    return found;
  }

  /** A member of the enclosing class, or of an outer class. */
  private Declaration member(Token token, int index) {
    for (SyntaxNode type = scopes.classOf(scopes.blockAt(index)); type != null; type = outer(type)) {
      for (Declaration declaration : byName.get(token.text())) {
        if (!declaration.kind.isLocal() && declaration.scope == type) return declaration;
      }
    }
    return null;
  }

  private SyntaxNode outer(SyntaxNode type) {
    SyntaxNode parent = scopes.parent(type);
    return parent == null ? null : scopes.classOf(parent);
  }

  /** {@code emit_signal("name")} and the like use the signal of that name. */
  private void signalName(int index) {
    Token literal = scopes.tokens.get(index);
    String text = literal.text();
    if (text.length() < 2 || !(text.startsWith("\"") || text.startsWith("'"))) return;
    List<Declaration> named = byName.get(text.substring(1, text.length() - 1));
    if (named == null) return;
    Token before = scopes.significantBefore(index);
    if (before == null) return;
    boolean used = false;
    if (TokenRanges.is(before, "(")) {
      Token call = scopes.significantBefore(scopes.indexOf(before));
      used = call != null && SIGNAL_CALLS.contains(call.text());
    } else if (TokenRanges.is(before, ",")) {
      Token self = scopes.significantBefore(scopes.indexOf(before));
      Token open = self == null ? null : scopes.significantBefore(scopes.indexOf(self));
      Token call = open == null ? null : scopes.significantBefore(scopes.indexOf(open));
      used = self != null && TokenRanges.is(self, "self") && open != null && TokenRanges.is(open, "(") && call != null && call.text().equals("Signal");
    }
    if (!used) return;
    SyntaxNode type = scopes.classOf(scopes.blockAt(index));
    named.stream().filter(d -> d.kind == Kind.SIGNAL && d.scope == type).forEach(d -> d.uses++);
  }
}
