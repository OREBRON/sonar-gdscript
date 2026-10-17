package org.orebron.gdscript;
import org.sonar.api.server.profile.BuiltInQualityProfilesDefinition;
public final class GDScriptProfile implements BuiltInQualityProfilesDefinition {
  @Override public void define(Context context) {
    var profile = context.createBuiltInQualityProfile("GDScript way", "gdscript");
    profile.setDefault(true);
    profile.activateRule("gdscript", "line-length");
    profile.done();
  }
}
