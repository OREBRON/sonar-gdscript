package org.orebron.gdscript.checks;

import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.orebron.gdscript.lexer.Token;
import org.orebron.gdscript.lexer.TokenKind;

/** TODO and FIXME tags in comments, uppercase as the Godot editor highlights them. */
public final class TaskCommentCheck implements Check {
  private static final Pattern TAG = Pattern.compile("(?<![\\w])(TODO|FIXME)(?![\\w])");

  @Override public String key() { return "task-comment"; }
  @Override public String name() { return "Track uses of TODO and FIXME tags"; }
  @Override public String severity() { return "INFO"; }

  @Override public String htmlDescription() {
    return "<p>Comments with an uppercase <code>TODO</code> or <code>FIXME</code> tag mark unfinished work. "
        + "This rule lists them so that they are completed or tracked elsewhere.</p>";
  }

  @Override public List<Violation> check(SourceFile file, int unused) {
    return file.tokens().stream().filter(token -> token.kind() == TokenKind.COMMENT).flatMap(this::violation).toList();
  }

  private Stream<Violation> violation(Token comment) {
    var matcher = TAG.matcher(comment.text());
    if (!matcher.find()) return Stream.empty();
    String message = matcher.group(1).equals("TODO")
        ? "Complete the task associated with this TODO comment."
        : "Take the required action to fix the issue indicated by this FIXME comment.";
    return Stream.of(Violation.at(this, comment, message));
  }
}
