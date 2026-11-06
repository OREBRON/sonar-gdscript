# The parser stops at the stray parenthesis, so the rules that need a syntax tree,
# the security rules among them, do not run on this file: OS.execute is not reported.
func f(path):
	OS.execute(path, [])
	var a = )  # Noncompliant
