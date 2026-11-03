package org.orebron.gdscript;
import org.orebron.gdscript.checks.Check;
import org.orebron.gdscript.checks.Checks;
import org.sonar.api.server.profile.BuiltInQualityProfilesDefinition;
/** The default profile activates every rule that is active by default. */
public final class GDScriptProfile implements BuiltInQualityProfilesDefinition {
  @Override public void define(Context context) {
    var profile = context.createBuiltInQualityProfile("GDScript way", GDScriptLanguage.KEY);
    profile.setDefault(true);
    Checks.ALL.stream().filter(Check::activeByDefault).forEach(check -> profile.activateRule(Check.REPOSITORY, check.key()));
    profile.done();
  }
}
