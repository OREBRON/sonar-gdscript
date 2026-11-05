package org.orebron.gdscript.checks;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

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

  /** Drops a less specific finding where the more specific rule reported at the same position, keeping the order. */
  public static List<Violation> withPrecedence(List<Violation> violations) {
    record Position(String rule, int line, int column) {}
    // Collected once so the filter is linear, not one scan of every finding per less specific one.
    Set<Position> specificFindings = violations.stream().filter(v -> MORE_SPECIFIC.containsValue(v.rule().rule()))
        .map(v -> new Position(v.rule().rule(), v.line(), v.column())).collect(Collectors.toSet());
    return violations.stream().filter(violation -> {
      String specific = MORE_SPECIFIC.get(violation.rule().rule());
      return specific == null || !specificFindings.contains(new Position(specific, violation.line(), violation.column()));
    }).toList();
  }
}
