func f(a, b):
	a + b  # Noncompliant
	a == b  # Noncompliant
	a.b  # Noncompliant
	print(a)
	((print(b)))
	a.b = 1
	await a.done
	"""A string comment."""
	match a:
		1:
			pass
	a if b else 1  # Noncompliant
	@warning_ignore("standalone_expression")
	-a
	a(); b  # Noncompliant
