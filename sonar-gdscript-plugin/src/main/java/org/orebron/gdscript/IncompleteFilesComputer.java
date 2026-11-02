package org.orebron.gdscript;

import org.sonar.api.ce.measure.Component;
import org.sonar.api.ce.measure.Measure;
import org.sonar.api.ce.measure.MeasureComputer;

/**
 * Sums gdscript_incomplete_files for directories and projects. Files are skipped: the scanner already wrote their
 * value and a second addMeasure would throw. Components without any GDScript file get no value.
 */
public final class IncompleteFilesComputer implements MeasureComputer {
  private static final String KEY = GDScriptMetrics.INCOMPLETE_FILES.key();

  @Override public MeasureComputerDefinition define(MeasureComputerDefinitionContext context) {
    return context.newDefinitionBuilder().setOutputMetrics(KEY).build();
  }

  @Override public void compute(MeasureComputerContext context) {
    if (context.getComponent().getType() == Component.Type.FILE) return;
    int sum = 0;
    boolean measured = false;
    for (Measure child : context.getChildrenMeasures(KEY)) {
      sum += child.getIntValue();
      measured = true;
    }
    if (measured) context.addMeasure(KEY, sum);
  }
}
