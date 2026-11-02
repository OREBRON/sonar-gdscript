package org.orebron.gdscript.lexer;

/** Token categories. NEWLINE ends a physical line that has code; COMMENT and CONTINUATION are trivia. */
public enum TokenKind { IDENTIFIER, NUMBER, STRING, NODE_PATH, ANNOTATION, OPERATOR, COMMENT, CONTINUATION, NEWLINE, EOF }
