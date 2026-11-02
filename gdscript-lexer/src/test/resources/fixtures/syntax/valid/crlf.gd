extends RefCounted

# Windows line endings, no trailing newline.
func value() -> int:
	var text := """a
b"""
	return text.length()