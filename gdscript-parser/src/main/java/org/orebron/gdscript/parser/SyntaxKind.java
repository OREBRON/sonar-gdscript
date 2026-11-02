package org.orebron.gdscript.parser;

/**
 * Constructs the tree keeps. Everything else is parsed and checked but not kept; kinds are added when an analysis
 * needs them. CONDITIONAL_EXPRESSION and LOGICAL_OPERATOR are leaves at their operator token.
 */
public enum SyntaxKind {
  SCRIPT, CLASS, FUNCTION, ACCESSOR, LAMBDA, IF, ELIF, WHILE, FOR, MATCH_BRANCH,
  /** A match branch without a guard whose pattern list contains {@code _} or a bare {@code var name}. */
  CATCH_ALL_BRANCH,
  CONDITIONAL_EXPRESSION, LOGICAL_OPERATOR
}
