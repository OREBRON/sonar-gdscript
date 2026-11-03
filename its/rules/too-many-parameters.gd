func eight(a, b, c, d, e, f, g, h):  # Noncompliant
	print(a, b, c, d, e, f, g, h)
func seven(a, b, c, d, e, f, g):
	print(a, b, c, d, e, f, g)
func rest_counts_as_one(a, b, c, d, e, f, ...rest):
	print(a, b, c, d, e, f, rest)
func lambdas_are_not_checked():
	var cb = func(a, b, c, d, e, f, g, h): return [a, b, c, d, e, f, g, h]
	cb.call(1, 2, 3, 4, 5, 6, 7, 8)
