package org.orebron.gdscript.lexer;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class SourceExceptionTest {
  @Test void printableEscapesInvisibleCodePoints() {
    assertEquals("a\\r\\n\\tbU+001BU+0085U+2028U+200EU+00A0 é😀U+D800", SourceException.printable("a\r\n\tb\u001b\u0085\u2028\u200e\u00a0 é😀\ud800", 80));
  }

  @Test void printableCutsAtCodePointsAndNeverSplitsASurrogatePair() {
    assertEquals("abc", SourceException.printable("abc", 3));
    assertEquals("ab…", SourceException.printable("abcd", 3));
    assertEquals("😀😀…", SourceException.printable("😀".repeat(5), 3));
    assertEquals("a…", SourceException.printable("a\n", 2)); // an escape is never cut in half
  }
}
