extends Node
## Multi-line lambdas inside brackets that end before ')' or ',' instead of a dedent.

signal changed(value: int)


func closers() -> void:
	changed.connect(
		func(value: int) -> void:
			print(value))
	create_tween().tween_method(
			func(v: float) -> void:
				set_meta("progress", v), 0.0, 1.0, 0.5)
	changed.connect(
			func(value: int) -> void:
				print(value)
				)
	changed.connect(func(value: int) -> void:
		if value > 0:
			print(value))
