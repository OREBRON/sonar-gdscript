extends Node

enum Kind { NONE, SMALL, LARGE }


func branches(value: int, flag: bool) -> String:
	if value > 10 and flag:
		return "big"
	elif value > 5 or not flag:
		return "medium"
	else:
		return "small"


func loops(items: Array[int]) -> int:
	var total := 0
	for item: int in items:
		if item < 0:
			continue
		if item > 100:
			break
		total += item
	var count := 3
	while count > 0:
		count -= 1
	for i in range(3): total += i
	for key in {"a": 1}:
		print(key)
	return total


func matching(input: Variant) -> String:
	match input:
		1, 2, 3:
			return "small"
		Kind.LARGE:
			return "large"
		"text":
			return "string"
		[1, var second, ..]:
			return "array %s" % second
		{"key": var val, ..}:
			return "dict %s" % val
		var other when other is int and other > 100:
			return "guarded"
		_:
			return "other"


func one_liners(x: int) -> int:
	if x == 0: return 0
	var a := 1; var b := 2
	if x < 0:
		x = -x; a += b
	else: pass
	return x + a


func misc() -> void:
	assert(1 + 1 == 2, "math works")
	await get_tree().process_frame
	breakpoint
	pass
