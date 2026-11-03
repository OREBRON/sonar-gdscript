package org.orebron.gdscript.checks;

import java.util.List;
import java.util.Optional;
import org.sonar.api.batch.rule.ActiveRule;
import org.sonar.api.config.Configuration;
import org.sonar.api.rule.RuleKey;

/**
 * One GDScript rule: the metadata that the rules definition and the profile read, and the analysis that finds
 * violations. Adding a rule means adding a Check to {@link Checks#ALL}, its specification and its tests.
 */
public interface Check {
  String REPOSITORY = "gdscript";

  String key();
  String name();
  String htmlDescription();

  /** Legacy severity: INFO, MINOR, MAJOR, CRITICAL or BLOCKER. */
  String severity();

  default List<String> tags() { return List.of(); }

  /** The rule's integer parameter, if it has one. */
  default Optional<Limit> limit() { return Optional.empty(); }

  /** The limit an analysis uses: the active rule's parameter, validated, or its default; 0 without a parameter. */
  default int configuredLimit(ActiveRule rule, Configuration configuration) {
    return limit().map(limit -> Limit.parse(key() + "." + limit.key(), rule.param(limit.key()), limit.defaultValue())).orElse(0);
  }

  List<Violation> check(SourceFile file, int limit);

  default RuleKey ruleKey() { return RuleKey.of(REPOSITORY, key()); }
}
