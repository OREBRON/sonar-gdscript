package org.orebron.gdscript;

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
  @Test void registersLanguageRuleProfileAndSensor() {
    assertEquals("gdscript", new GDScriptLanguage().getKey());
    assertArrayEquals(new String[]{".gd"}, new GDScriptLanguage().getFileSuffixes());
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
    for (Class<?> extension : java.util.List.of(GDScriptLanguage.class,
        GDScriptRules.class, GDScriptProfile.class, GDScriptSensor.class)) {
      assertTrue(context.getExtensions().contains(extension));
    }
    org.sonar.api.config.PropertyDefinition property = null;
    for (Object extension : context.getExtensions()) {
      if (extension instanceof org.sonar.api.config.PropertyDefinition definition) property = definition;
    }
    assertNotNull(property);
    assertEquals("sonar.gdscript.maxLineLength", property.key());
    assertEquals("120", property.defaultValue());
    assertEquals(5, context.getExtensions().size());
  }
}
