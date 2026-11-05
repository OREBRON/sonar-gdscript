package org.orebron.gdscript.lexer;

/** Source that cannot be analyzed, located by a 1-based line and a 0-based UTF-16 column. */
public class SourceException extends Exception {
  private static final long serialVersionUID = 1L;
  private final int line;
  private final int column;

  protected SourceException(String message, int line, int column) {
    super(message + " at line " + line + ", column " + (column + 1));
    this.line = line;
    this.column = column;
  }

  public int line() { return line; }
  public int column() { return column; }

  /**
   * Source text made safe to quote in a log line or analysis error (CWE-117): line breaks and tabs become {@code \n},
   * {@code \r}, {@code \t}, other invisible code points become {@code U+XXXX}, and text longer than {@code limit}
   * code points is cut at a code point boundary and ends with an ellipsis.
   */
  public static String printable(String text, int limit) {
    var out = new StringBuilder();
    int count = 0;
    for (int i = 0; i < text.length(); ) {
      int c = text.codePointAt(i);
      i += Character.charCount(c);
      String part = switch (c) {
        case '\n' -> "\\n";
        case '\r' -> "\\r";
        case '\t' -> "\\t";
        default -> visible(c) ? Character.toString(c) : String.format("U+%04X", c);
      };
      int size = part.codePointCount(0, part.length());
      if (count + size > (i == text.length() ? limit : limit - 1)) return out.append('\u2026').toString(); // room for the ellipsis
      out.append(part);
      count += size;
    }
    return out.toString();
  }

  private static boolean visible(int c) {
    return switch (Character.getType(c)) {
      case Character.CONTROL, Character.FORMAT, Character.LINE_SEPARATOR, Character.PARAGRAPH_SEPARATOR, Character.SURROGATE,
          Character.PRIVATE_USE, Character.UNASSIGNED -> false;
      case Character.SPACE_SEPARATOR -> c == ' ';
      default -> true;
    };
  }
}
