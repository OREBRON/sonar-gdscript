package org.orebron.gdscript;
import org.sonar.api.Plugin;
import org.sonar.api.PropertyType;
import org.sonar.api.config.PropertyDefinition;
public final class GDScriptPlugin implements Plugin {
  @Override public void define(Context context) {
    context.addExtensions(GDScriptLanguage.class, GDScriptRules.class, GDScriptProfile.class, GDScriptSensor.class,
        GDScriptMetrics.class, IncompleteFilesComputer.class,
        projectProperty(LineLengthCheck.MAX_LINE_LENGTH).name("Maximum GDScript line length")
            .description("Positive Unicode code-point limit. Tabs count as one character. Applies to main GDScript files.")
            .type(PropertyType.INTEGER).defaultValue(String.valueOf(LineLengthCheck.DEFAULT_MAXIMUM)).build(),
        projectProperty(GDScriptSensor.FAIL_ON_INCOMPLETE).name("Fail on incomplete GDScript analysis")
            .description("Fail the analysis when a main GDScript file cannot be read or tokenized, instead of only reporting it.")
            .type(PropertyType.BOOLEAN).defaultValue("false").build());
  }

  private static PropertyDefinition.Builder projectProperty(String key) {
    return PropertyDefinition.builder(key).category("GDScript").onConfigScopes(PropertyDefinition.ConfigScope.PROJECT);
  }
}
