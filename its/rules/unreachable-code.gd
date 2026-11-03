func f(x):
	if x:
		return 1
		print("after return")  # Noncompliant
	if x:
		return 2
	elif not x:
		return 3
	print("reachable: there is no else")
	match x:
		1:
			return 4
		_:
			return 5
	print("unreachable")  # Noncompliant
	print("reported once")
var p:
	get:
		return 1
		print("accessors are left out")
