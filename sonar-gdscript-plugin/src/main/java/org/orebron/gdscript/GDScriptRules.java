package org.orebron.gdscript;
import org.orebron.gdscript.checks.Check;
import org.orebron.gdscript.checks.Checks;
import org.sonar.api.rules.RuleType;
import org.sonar.api.server.rule.RuleParamType;
import org.sonar.api.server.rule.RulesDefinition;
/** One rule per check in {@link Checks#ALL}; the metadata lives in each check. */
public final class GDScriptRules implements RulesDefinition {
  @Override public void define(Context context) {
    var repository = context.createRepository(Check.REPOSITORY, GDScriptLanguage.KEY).setName("GDScript");
    Checks.ALL.forEach(check -> define(repository, check));
    repository.done();
  }

  private static void define(NewRepository repository, Check check) {
    var rule = repository.createRule(check.key()).setName(check.name()).setHtmlDescription(check.htmlDescription())
        .setSeverity(check.severity()).setType(RuleType.CODE_SMELL).setTags(check.tags().toArray(String[]::new));
    check.limit().ifPresent(limit -> rule.createParam(limit.key()).setType(RuleParamType.INTEGER)
        .setDefaultValue(String.valueOf(limit.defaultValue())).setDescription(limit.description()));
  }
}
