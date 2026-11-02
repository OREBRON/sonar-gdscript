package org.orebron.gdscript;
import org.sonar.api.server.rule.RulesDefinition;
import org.sonar.api.rules.RuleType;
public final class GDScriptRules implements RulesDefinition {
  @Override public void define(Context context) {
    var repository = context.createRepository("gdscript", GDScriptLanguage.KEY).setName("GDScript");
    repository.createRule("line-length").setName("Lines should not be too long")
        .setHtmlDescription("<p>Keep lines within the configured sonar.gdscript.maxLineLength limit (default 120).</p>"
            + "<p>This text-only check counts Unicode code points, including whitespace; tabs count as one. "
            + "Comments and strings are not exempt. No syntax or security analysis is performed.</p>")
        .setSeverity("MINOR").setType(RuleType.CODE_SMELL).setTags("convention");
    repository.done();
  }
}
