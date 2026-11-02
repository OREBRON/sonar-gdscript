extends Node

signal pinged(value: int)

var counter := 0


func operators(a: int, b: int) -> Array:
	var results := []
	results.append(a + b - a * b / 2 % 3)
	results.append(a ** 2)
	results.append(a << 1 | b >> 1 & 0xF ^ ~a)
	results.append(a < b and a <= b or a > b && a >= b || a == b and a != b)
	results.append(not (a in [1, 2]) and !(b in [3]))
	results.append(a if a > b else b)
	results.append("x" if a > 0 else "y" if b > 0 else "z")
	var node: Node = self
	results.append(node is Node and node is not Node2D)
	results.append(node as Node)
	results.append(-a + +b)
	return results


func collections() -> Dictionary:
	var list := [
		1,
		2,
		3,
	]
	var python_style := {"a": 1, "b": [list, {}],}
	var lua_style := {
		a = 1,
		b = 2,
	}
	var nested := {"inner": {"deep": [1, [2, [3]]]}}
	return {"list": list, "py": python_style, "lua": lua_style, "nested": nested}


func lambdas() -> void:
	var double := func(x: int) -> int: return x * 2
	var named := func helper(x): return x + 1
	var multi := func(x: int) -> int:
		var y := x * 3
		return y + 1
	pinged.connect(func(value: int) -> void:
		counter += value
		print(counter)
	)
	pinged.connect(func(value): print(value))
	print(double.call(2), named.call(3), multi.call(4))
	var sorted := [3, 1, 2]
	sorted.sort_custom(func(left, right): return left > right)


func calls_and_access() -> void:
	var script := preload("res://statements.gd")
	var vector := Vector2(1, 2)
	print(vector.x, vector.normalized().length(), script)
	var long_sum := 1 + \
		2 + \
		3
	var chained := (
		long_sum
		+ 4
	)
	print(long_sum, chained, super.get_class())


func _ready() -> void:
	pass
