package org.orebron.gdscript.checks;

import java.util.List;

/** Every GDScript rule. The rules definition, the default profile and the sensor all read this list. */
public final class Checks {
  public static final List<Check> ALL = List.of(new LineLengthCheck(), new FunctionComplexityCheck(),
      new FunctionLengthCheck(), new ParameterCountCheck(), new NestingDepthCheck(), new TaskCommentCheck(),
      new NamingConventionCheck(), new EmptyBlockCheck(), new CommentedCodeCheck(), new DuplicateStringCheck(),
      new CollapsibleIfCheck(), new TooManyReturnsCheck(), new BooleanLiteralCheck(), new IdenticalBranchesCheck(),
      new UnusedSymbolChecks.UnusedVariableCheck(), new UnusedSymbolChecks.UnusedLocalConstantCheck(),
      new UnusedSymbolChecks.UnusedParameterCheck(), new UnusedSymbolChecks.UnusedPrivateClassVariableCheck(),
      new UnusedSymbolChecks.UnusedSignalCheck(), new ShadowedVariableCheck(), new UnreachableCodeCheck(),
      new UnreachablePatternCheck(), new StandaloneExpressionCheck(), new EmptyFileCheck(),
      new OnreadyChecks.GetNodeWithoutOnreadyCheck(), new OnreadyChecks.OnreadyWithExportCheck());

  private Checks() {}
}
