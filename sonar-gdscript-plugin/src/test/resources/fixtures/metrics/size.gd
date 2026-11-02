# Licence header: a comment line
## Doc comment
extends Node

#
# ----
#region Setup
var text := """first

last"""  # trailing comment
var total := 1 + \
	2

func f() -> void:
	"""
	Block string: code, not a comment.
	"""
	pass
#endregion
