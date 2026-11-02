package org.orebron.gdscript;
import org.sonar.api.resources.AbstractLanguage;
public final class GDScriptLanguage extends AbstractLanguage {
  public static final String KEY = "gdscript";
  public GDScriptLanguage() { super(KEY, "GDScript"); }
  @Override public String[] getFileSuffixes() { return new String[]{".gd"}; }
}
