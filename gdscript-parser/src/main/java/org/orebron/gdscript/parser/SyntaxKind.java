package org.orebron.gdscript.parser;

/**
 * Constructs the tree keeps. Everything else is parsed and checked but not kept; kinds are added when an analysis
 * needs them. CONDITIONAL_EXPRESSION, LOGICAL_OPERATOR and PARAMETER are leaves at their token.
 */
public enum SyntaxKind {
  SCRIPT, CLASS, FUNCTION, ACCESSOR, LAMBDA,
  /** A parameter name, kept as a child of its function, accessor or lambda. */
  PARAMETER,
  IF, ELIF, ELSE, WHILE, FOR,
  /** A match statement; its branches are its children. */
  MATCH, MATCH_BRANCH,
  /** A match branch without a guard whose pattern list contains {@code _} or a bare {@code var name}. */
  CATCH_ALL_BRANCH,
  CONDITIONAL_EXPRESSION, LOGICAL_OPERATOR
}
