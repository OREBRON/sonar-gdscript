package org.orebron.gdscript.checks;

import java.util.List;
import java.util.Map;

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
      new OnreadyChecks.GetNodeWithoutOnreadyCheck(), new OnreadyChecks.OnreadyWithExportCheck(),
      new SecurityChecks.ProcessExecutionCheck(), new SecurityChecks.ShellCommandCheck(), new SecurityChecks.ShellOpenCheck(),
      new SecurityChecks.JavaScriptEvalCheck(), new SecurityChecks.ResourcePackLoadingCheck(),
      new SecurityChecks.ObjectDecodingCheck(), new SecurityChecks.RpcAnyPeerCheck(),
      new SecurityChecks.HardcodedCredentialCheck(), new SecurityChecks.WeakHashCheck(),
      new SecurityChecks.AesEcbReferenceCheck(), new SecurityChecks.TlsUnsafeOptionsCheck());

  /** A less specific rule and the more specific one that reports at the same place (security plan, "Paired rules"). */
  private static final Map<String, String> MORE_SPECIFIC = Map.of("process-execution", "shell-command");

  private Checks() {}

  /** Drops a less specific finding where the more specific rule reported at the same position. */
  public static List<Violation> withPrecedence(List<Violation> violations) {
    return violations.stream().filter(violation -> {
      String specific = MORE_SPECIFIC.get(violation.rule().rule());
      return specific == null || violations.stream().noneMatch(other -> other.rule().rule().equals(specific)
          && other.line() == violation.line() && other.column() == violation.column());
    }).toList();
  }
}
