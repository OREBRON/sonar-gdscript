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
    assertEquals(1, repository.rules().size());
    assertNotNull(repository.rule("line-length"));
    var profiles = new BuiltInQualityProfilesDefinition.Context();
    new GDScriptProfile().define(profiles);
    var profile = profiles.profile("gdscript", "GDScript way");
    assertTrue(profile.isDefault());
    assertEquals("line-length", profile.rules().get(0).ruleKey());
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
