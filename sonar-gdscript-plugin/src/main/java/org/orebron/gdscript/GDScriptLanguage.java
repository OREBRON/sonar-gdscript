package org.orebron.gdscript;
import org.sonar.api.resources.AbstractLanguage;
public final class GDScriptLanguage extends AbstractLanguage {
  public GDScriptLanguage() { super("gdscript", "GDScript"); }
  @Override public String[] getFileSuffixes() { return new String[]{".gd"}; }
}
