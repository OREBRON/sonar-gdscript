extends Node
"""
Class-level strings are comments.
"""

const TAU_HALF : = TAU / 2.0
var inferred : = 1
var trailing_dot := 123.
var negative_trailing_dot := -1_234.
var multi_line_text := "first
second"
var lua := {name = 1, "with spaces" = 2}


func names() -> int:
	var match = 1
	var PI = 3
	var when = match + PI
	return when


func flow(x: int, value: Variant) -> void:
	print("A");   ;; print("B");;
	if x == 0 \
			# A comment inside a continued condition.
			and x < 2:
		pass
	match value:
		@warning_ignore("unreachable_pattern")
		1:
			pass
	match x:
		pass


func shorthand_paths() -> void:
	print($%"Unique Name", %Hey/%Howdy)
