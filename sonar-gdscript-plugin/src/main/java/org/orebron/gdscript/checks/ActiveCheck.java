package org.orebron.gdscript.checks;

import java.util.List;

/** A check whose rule is active, with the limit configured for this analysis. */
public record ActiveCheck(Check check, int limit) {
  public List<Violation> run(SourceFile file) { return check.check(file, limit); }
}
