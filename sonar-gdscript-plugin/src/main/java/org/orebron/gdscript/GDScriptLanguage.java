package org.orebron.gdscript;
import java.util.Arrays;
import org.sonar.api.config.Configuration;
import org.sonar.api.resources.AbstractLanguage;
public final class GDScriptLanguage extends AbstractLanguage {
  public static final String KEY = "gdscript";
  /** The scanner detects a language only through this setting, so GDScriptPlugin must define it with a default. */
  public static final String FILE_SUFFIXES_KEY = "sonar.gdscript.file.suffixes";
  static final String DEFAULT_FILE_SUFFIXES = ".gd";
  private final Configuration configuration;

  public GDScriptLanguage(Configuration configuration) {
    super(KEY, "GDScript");
    this.configuration = configuration;
  }

  @Override public String[] getFileSuffixes() {
    String[] suffixes = Arrays.stream(configuration.getStringArray(FILE_SUFFIXES_KEY)).map(String::trim)
        .filter(suffix -> !suffix.isEmpty()).toArray(String[]::new);
    return suffixes.length > 0 ? suffixes : new String[]{DEFAULT_FILE_SUFFIXES};
  }
}
