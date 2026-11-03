package org.orebron.gdscript;

import com.sonarsource.scanner.engine.sensor.test.fixtures.TestConfigurationImpl;
import org.junit.jupiter.api.Test;
import org.sonar.api.Plugin;
import org.sonar.api.SonarRuntime;
import org.sonar.api.SonarQubeSide;
import org.sonar.api.SonarProduct;
import org.sonar.api.utils.Version;
import org.sonar.api.server.rule.RulesDefinition;
import org.sonar.api.server.profile.BuiltInQualityProfilesDefinition;
import static org.junit.jupiter.api.Assertions.*;

class GDScriptRegistrationTest {
  @Test void languageSuffixesComeFromTheSettingTheScannerReads() {
    var configuration = TestConfigurationImpl.build();
    var language = new GDScriptLanguage(configuration);
    assertEquals("gdscript", language.getKey());
    assertArrayEquals(new String[]{".gd"}, language.getFileSuffixes());
    configuration.setProperty("sonar.gdscript.file.suffixes", ".gd, .gdscript ,");
    assertArrayEquals(new String[]{".gd", ".gdscript"}, language.getFileSuffixes());
  }

  @Test void registersLanguageRuleProfileAndSensor() {
    var rules = new RulesDefinition.Context();
    new GDScriptRules().define(rules);
    var repository = rules.repository("gdscript");
    assertEquals("gdscript", repository.language());
    var keys = new java.util.HashSet<>(java.util.Set.of("line-length", "function-complexity", "function-length",
        "too-many-parameters", "nesting-depth", "task-comment", "naming-convention", "empty-block", "commented-code",
        "duplicate-string", "collapsible-if", "too-many-returns", "boolean-literal-comparison", "identical-branches",
        "unused-variable", "unused-local-constant", "unused-parameter", "unused-private-class-variable", "unused-signal",
        "shadowed-variable", "unreachable-code", "unreachable-pattern", "standalone-expression", "empty-file",
        "get-node-without-onready", "onready-with-export", "process-execution", "shell-command", "shell-open-dynamic",
        "javascript-eval", "resource-pack-loading", "object-decoding", "rpc-any-peer", "hardcoded-credential", "weak-hash",
        "aes-ecb-reference", "tls-unsafe-options"));
    var shell = repository.rule("shell-command");
    assertEquals(org.sonar.api.rules.RuleType.VULNERABILITY, shell.type());
    assertEquals(java.util.Set.of("cwe:78", "owaspTop10-2021:a3"), shell.securityStandards());
    assertEquals(keys, repository.rules().stream().map(RulesDefinition.Rule::key).collect(java.util.stream.Collectors.toSet()));
    var parameters = new java.util.HashMap<String, String>();
    for (var rule : repository.rules()) {
      rule.params().forEach(param -> parameters.put(rule.key() + "." + param.key(), param.defaultValue()));
    }
    assertEquals(java.util.Map.of("function-complexity.max", "10", "function-length.max", "75", "too-many-parameters.max", "7",
        "nesting-depth.max", "3", "too-many-returns.max", "3", "duplicate-string.threshold", "3"), parameters);
    keys.remove("too-many-returns");
    var profiles = new BuiltInQualityProfilesDefinition.Context();
    new GDScriptProfile().define(profiles);
    var profile = profiles.profile("gdscript", "GDScript way");
    assertTrue(profile.isDefault());
    assertEquals(keys, profile.rules().stream().map(BuiltInQualityProfilesDefinition.BuiltInActiveRule::ruleKey)
        .collect(java.util.stream.Collectors.toSet()));
    SonarRuntime runtime = new SonarRuntime() {
      public org.sonar.api.SonarEdition getEdition() { return org.sonar.api.SonarEdition.COMMUNITY; }
      public Version getApiVersion() { return Version.parse("13.8"); }
      public SonarProduct getProduct() { return SonarProduct.SONARQUBE; }
      public SonarQubeSide getSonarQubeSide() { return SonarQubeSide.SERVER; }
    };
    var context = new Plugin.Context(runtime);
    new GDScriptPlugin().define(context);
    for (Class<?> extension : java.util.List.of(GDScriptLanguage.class, GDScriptRules.class, GDScriptProfile.class,
        GDScriptSensor.class, GDScriptMetrics.class, IncompleteFilesComputer.class)) {
      assertTrue(context.getExtensions().contains(extension));
    }
    var defaults = new java.util.HashMap<String, String>();
    for (Object extension : context.getExtensions()) {
      if (extension instanceof org.sonar.api.config.PropertyDefinition definition) defaults.put(definition.key(), definition.defaultValue());
    }
    assertEquals(java.util.Map.of("sonar.gdscript.file.suffixes", ".gd", "sonar.gdscript.maxLineLength", "120",
        "sonar.gdscript.failOnIncompleteAnalysis", "false"), defaults);
    assertEquals(9, context.getExtensions().size());
    assertEquals(java.util.List.of("gdscript_incomplete_files"),
        new GDScriptMetrics().getMetrics().stream().map(metric -> metric.key()).toList());
  }
}
