func f(used):
	var used_local = used
	var unused_local = 1  # Noncompliant
	var _ignored = 2
	match used:
		[var bound]:  # Noncompliant
			pass
		{"key": var read}:
			print(read)
	print(used_local)
	@warning_ignore("unused_variable")
	var silenced = 4
