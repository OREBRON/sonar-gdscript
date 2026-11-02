package org.orebron.gdscript;
import org.sonar.api.Plugin;
import org.sonar.api.PropertyType;
import org.sonar.api.config.PropertyDefinition;
public final class GDScriptPlugin implements Plugin {
  @Override public void define(Context context) {
    context.addExtensions(GDScriptLanguage.class, GDScriptRules.class, GDScriptProfile.class, GDScriptSensor.class,
        PropertyDefinition.builder(GDScriptSensor.MAX_LINE_LENGTH).name("Maximum GDScript line length")
            .description("Positive Unicode code-point limit. Tabs count as one character. Applies to main GDScript files.")
            .category("GDScript").type(PropertyType.INTEGER).defaultValue("120").onConfigScopes(PropertyDefinition.ConfigScope.PROJECT).build());
  }
}
