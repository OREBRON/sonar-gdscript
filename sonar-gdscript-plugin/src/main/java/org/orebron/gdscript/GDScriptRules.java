package org.orebron.gdscript;
import org.orebron.gdscript.checks.Check;
import org.orebron.gdscript.checks.Checks;
import org.sonar.api.issue.impact.Severity;
import org.sonar.api.issue.impact.SoftwareQuality;
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
        .setSeverity(check.severity()).setType(check.type()).setTags(check.tags().toArray(String[]::new));
    if (check.type() == RuleType.VULNERABILITY) rule.addDefaultImpact(SoftwareQuality.SECURITY, impact(check.severity()));
    if (!check.cwe().isEmpty()) rule.addCwe(check.cwe().stream().mapToInt(Integer::intValue).toArray());
    if (!check.owasp2021().isEmpty()) rule.addOwaspTop10(OwaspTop10Version.Y2021, check.owasp2021().toArray(OwaspTop10[]::new));
    check.limit().ifPresent(limit -> rule.createParam(limit.key()).setType(RuleParamType.INTEGER)
        .setDefaultValue(String.valueOf(limit.defaultValue())).setDescription(limit.description()));
  }

  private static Severity impact(String severity) {
    return switch (severity) {
      case "BLOCKER" -> Severity.BLOCKER;
      case "CRITICAL" -> Severity.HIGH;
      case "MAJOR" -> Severity.MEDIUM;
      default -> Severity.LOW;
    };
  }
}
