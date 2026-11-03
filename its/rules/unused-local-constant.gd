func f():
	const UNUSED = 1  # Noncompliant
	const _IGNORED = 2
	const USED = 3
	print(USED)
