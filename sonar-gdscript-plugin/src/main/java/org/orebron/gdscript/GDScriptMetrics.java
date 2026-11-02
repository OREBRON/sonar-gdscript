package org.orebron.gdscript;

import java.util.List;
import org.sonar.api.measures.Metric;
import org.sonar.api.measures.Metrics;

/** Plugin metrics. The sensor writes file values; IncompleteFilesComputer sums them for directories and projects. */
public final class GDScriptMetrics implements Metrics {
  public static final Metric<Integer> INCOMPLETE_FILES = new Metric.Builder("gdscript_incomplete_files",
      "GDScript incomplete files", Metric.ValueType.INT)
      .setDescription("Main GDScript files that could not be read or tokenized; their size metrics are missing")
      .setDirection(Metric.DIRECTION_WORST).setQualitative(false).setDomain("GDScript").create();

  @SuppressWarnings("rawtypes")
  @Override public List<Metric> getMetrics() { return List.of(INCOMPLETE_FILES); }
}
