package org.orebron.gdscript.checks;

import java.util.List;
import org.orebron.gdscript.lexer.Token;
import org.orebron.gdscript.metrics.SizeMetrics;
import org.orebron.gdscript.parser.SyntaxNode;

/** What checks may read. Tokens are empty and size is null when lexing failed; the tree is null when parsing failed. */
public record SourceFile(String content, List<Token> tokens, SizeMetrics size, SyntaxNode tree) {}
