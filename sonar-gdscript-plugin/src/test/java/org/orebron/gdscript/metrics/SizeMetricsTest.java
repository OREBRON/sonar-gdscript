package org.orebron.gdscript.metrics;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.orebron.gdscript.lexer.GDScriptLexer;
import org.orebron.gdscript.lexer.LexException;

class SizeMetricsTest {
  @Test void countsCodeLinesAndSignificantCommentLines() throws LexException {
    var size = SizeMetrics.of(GDScriptLexer.lex(SizeFixture.content()));
    assertEquals(SizeFixture.CODE_LINES, size.codeLines());
    assertEquals(SizeFixture.COMMENT_LINES, size.commentLines());
    assertEquals(SizeFixture.CODE_LINES.size(), size.ncloc());
    assertEquals(SizeFixture.COMMENT_LINES.size(), size.commentLineCount());
  }

  @Test void emptySourceHasNoLines() throws LexException {
    var size = SizeMetrics.of(GDScriptLexer.lex(""));
    assertEquals(0, size.ncloc());
    assertEquals(0, size.commentLineCount());
  }
}
