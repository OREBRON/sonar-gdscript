package org.orebron.gdscript.checks;

import java.util.List;

/** Every GDScript rule. The rules definition, the default profile and the sensor all read this list. */
public final class Checks {
  public static final List<Check> ALL = List.of(new LineLengthCheck(), new FunctionComplexityCheck(),
      new FunctionLengthCheck(), new ParameterCountCheck(), new NestingDepthCheck(), new TaskCommentCheck(),
      new NamingConventionCheck(), new EmptyBlockCheck(), new CommentedCodeCheck(), new DuplicateStringCheck(),
      new CollapsibleIfCheck(), new TooManyReturnsCheck(), new BooleanLiteralCheck(), new IdenticalBranchesCheck());

  private Checks() {}
}
