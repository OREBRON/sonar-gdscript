package org.orebron.gdscript;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.sonar.api.ce.measure.Component;
import org.sonar.api.ce.measure.Issue;
import org.sonar.api.ce.measure.Measure;
import org.sonar.api.ce.measure.MeasureComputer.MeasureComputerContext;
import org.sonar.api.ce.measure.Settings;

class IncompleteFilesComputerTest {
  @Test void sumsChildrenOfDirectoriesAndProjects() {
    for (var type : List.of(Component.Type.DIRECTORY, Component.Type.PROJECT)) {
      var context = new FakeContext(type, List.of(0, 1, 1));
      new IncompleteFilesComputer().compute(context);
      assertEquals(2, context.added);
    }
  }

  @Test void leavesFileValuesToTheScanner() {
    var context = new FakeContext(Component.Type.FILE, List.of(1));
    new IncompleteFilesComputer().compute(context);
    assertNull(context.added);
  }

  @Test void addsNothingWithoutGDScriptChildren() {
    var context = new FakeContext(Component.Type.DIRECTORY, List.of());
    new IncompleteFilesComputer().compute(context);
    assertNull(context.added);
  }

  /** Only what the computer may use; any other call is a test failure. */
  private static final class FakeContext implements MeasureComputerContext {
    private final Component.Type type;
    private final List<Integer> children;
    private Integer added;

    FakeContext(Component.Type type, List<Integer> children) {
      this.type = type;
      this.children = children;
    }

    @Override public Component getComponent() {
      return new Component() {
        @Override public Type getType() { return type; }
        @Override public String getKey() { return "component"; }
        @Override public FileAttributes getFileAttributes() { return null; }
      };
    }

    @Override public Iterable<Measure> getChildrenMeasures(String metric) {
      assertEquals(GDScriptMetrics.INCOMPLETE_FILES.key(), metric);
      return children.stream().map(FakeContext::measure).toList();
    }

    @Override public void addMeasure(String metric, int value) {
      assertEquals(GDScriptMetrics.INCOMPLETE_FILES.key(), metric);
      assertNull(added, "a component gets at most one value");
      added = value;
    }

    @Override public Settings getSettings() { throw unexpected(); }
    @Override public Measure getMeasure(String metric) { throw unexpected(); }
    @Override public void addMeasure(String metric, double value) { throw unexpected(); }
    @Override public void addMeasure(String metric, long value) { throw unexpected(); }
    @Override public void addMeasure(String metric, String value) { throw unexpected(); }
    @Override public void addMeasure(String metric, boolean value) { throw unexpected(); }
    @Override public List<? extends Issue> getIssues() { throw unexpected(); }

    private static AssertionError unexpected() { return new AssertionError("unexpected call"); }

    private static Measure measure(int value) {
      return new Measure() {
        @Override public int getIntValue() { return value; }
        @Override public long getLongValue() { throw unexpected(); }
        @Override public double getDoubleValue() { throw unexpected(); }
        @Override public String getStringValue() { throw unexpected(); }
        @Override public boolean getBooleanValue() { throw unexpected(); }
      };
    }
  }
}
