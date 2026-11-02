package org.orebron.gdscript.metrics;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Set;

/** fixtures/metrics/size.gd (accepted by Godot 4.7.2) and its hand-checked line sets. */
public final class SizeFixture {
  public static final Set<Integer> CODE_LINES = Set.of(3, 8, 9, 10, 11, 12, 14, 15, 16, 17, 18);
  public static final Set<Integer> COMMENT_LINES = Set.of(1, 2, 7, 10, 19);

  private SizeFixture() {}

  public static String content() {
    try (var stream = SizeFixture.class.getResourceAsStream("/fixtures/metrics/size.gd")) {
      return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
