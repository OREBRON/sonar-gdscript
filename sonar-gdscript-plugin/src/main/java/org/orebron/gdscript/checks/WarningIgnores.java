package org.orebron.gdscript.checks;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.orebron.gdscript.lexer.Token;
import org.orebron.gdscript.lexer.TokenKind;

/**
 * Lines on which Godot's {@code @warning_ignore} annotations silence a warning, so the matching rules stay silent too.
 * As in Godot 4.7, {@code @warning_ignore("name")} covers its own line through the end of the next declaration's or
 * statement's header (a function's parameters, an if's condition, a variable's initializer), and
 * {@code @warning_ignore_start("name")} covers every line up to {@code @warning_ignore_restore("name")} or the end of
 * the file. A warning is silenced when the line it is reported on is covered.
 */
final class WarningIgnores {
  /** Per warning, the covered lines as [first, last] ranges: sorted and disjoint once {@link #of} returns. */
  private final Map<String, List<int[]>> ranges = new HashMap<>();
  private final Map<String, Integer> openRegions = new HashMap<>();
  /** Regions without a restore: ignored from this line to the end of the file. */
  private final Map<String, Integer> ignoredFrom = new HashMap<>();

  private WarningIgnores() {}

  static WarningIgnores of(List<Token> tokens) {
    var ignores = new WarningIgnores();
    for (int i = 0; i < tokens.size(); i++) {
      Token token = tokens.get(i);
      if (token.kind() != TokenKind.ANNOTATION) continue;
      switch (token.text()) {
        case "@warning_ignore" -> ignores.target(tokens, i);
        case "@warning_ignore_start" -> names(tokens, i).forEach(name -> ignores.openRegions.putIfAbsent(name, token.line()));
        case "@warning_ignore_restore" -> names(tokens, i).forEach(name -> {
          Integer start = ignores.openRegions.remove(name);
          if (start != null) ignores.cover(name, start, token.line());
        });
        default -> { }
      }
    }
    ignores.ignoredFrom.putAll(ignores.openRegions);
    ignores.ranges.values().forEach(WarningIgnores::merge);
    return ignores;
  }

  boolean ignores(String warning, int line) {
    Integer from = ignoredFrom.get(warning);
    if (from != null && from <= line) return true;
    List<int[]> covered = ranges.getOrDefault(warning, List.of());
    int found = Collections.binarySearch(covered, new int[] {line}, BY_FIRST);
    int index = found >= 0 ? found : -found - 2;
    return index >= 0 && covered.get(index)[1] >= line;
  }

  /** The number of stored ranges, which grows with the annotations, not with the lines they cover. */
  int rangeCount() { return ranges.values().stream().mapToInt(List::size).sum(); }

  private void target(List<Token> tokens, int annotation) {
    List<String> names = names(tokens, annotation);
    int start = tokens.get(annotation).line();
    int i = afterArguments(tokens, annotation);
    while (i < tokens.size() && (!TokenRanges.isSignificant(tokens.get(i)) || tokens.get(i).kind() == TokenKind.ANNOTATION)) {
      i = tokens.get(i).kind() == TokenKind.ANNOTATION ? afterArguments(tokens, i) : i + 1;
    }
    int end = headerEnd(tokens, i);
    names.forEach(name -> cover(name, start, end));
  }

  /** The last line of a declaration's or statement's header: up to the first ':' or line break outside brackets. */
  private static int headerEnd(List<Token> tokens, int start) {
    int depth = 0;
    int line = start < tokens.size() ? tokens.get(start).line() : 0;
    for (int i = start; i < tokens.size(); i++) {
      Token token = tokens.get(i);
      if (token.kind() == TokenKind.EOF || (depth == 0 && token.kind() == TokenKind.NEWLINE)) return line;
      if (TokenRanges.isSignificant(token)) line = token.endLine();
      if (token.kind() != TokenKind.OPERATOR) continue;
      switch (token.text()) {
        case "(", "[", "{" -> depth++;
        case ")", "]", "}" -> depth = Math.max(0, depth - 1);
        case ":" -> {
          if (depth == 0 && isBlockHeader(tokens.get(start))) return line;
        }
        default -> { }
      }
    }
    return line;
  }

  private static boolean isBlockHeader(Token first) {
    return switch (first.text()) {
      case "func", "static", "if", "elif", "else", "for", "while", "match", "class" -> true;
      default -> false;
    };
  }

  private static final Comparator<int[]> BY_FIRST = Comparator.comparingInt(range -> range[0]);

  private void cover(String name, int start, int end) {
    if (start <= end) ranges.computeIfAbsent(name, k -> new ArrayList<>()).add(new int[] {start, end});
  }

  /** Sorts the ranges and joins overlapping ones, so a lookup is one binary search. */
  private static void merge(List<int[]> ranges) {
    ranges.sort(BY_FIRST);
    var merged = new ArrayList<int[]>();
    for (int[] range : ranges) {
      int[] last = merged.isEmpty() ? null : merged.get(merged.size() - 1);
      if (last != null && range[0] <= last[1]) last[1] = Math.max(last[1], range[1]);
      else merged.add(range);
    }
    ranges.clear();
    ranges.addAll(merged);
  }

  /** The string arguments of the annotation at {@code index}. */
  private static List<String> names(List<Token> tokens, int index) {
    var names = new ArrayList<String>();
    int end = afterArguments(tokens, index);
    for (int i = index + 1; i < end; i++) {
      Token token = tokens.get(i);
      if (token.kind() == TokenKind.STRING && token.text().length() >= 2) names.add(token.text().substring(1, token.text().length() - 1).toLowerCase());
    }
    return names;
  }

  /** The index after the annotation's argument list, or after the annotation when it has none. */
  private static int afterArguments(List<Token> tokens, int index) {
    int i = index + 1;
    if (i >= tokens.size() || !TokenRanges.is(tokens.get(i), "(")) return i;
    int depth = 0;
    for (; i < tokens.size(); i++) {
      if (TokenRanges.is(tokens.get(i), "(")) depth++;
      else if (TokenRanges.is(tokens.get(i), ")") && --depth == 0) return i + 1;
    }
    return i;
  }
}
