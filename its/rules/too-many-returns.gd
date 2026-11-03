func four(x):  # Noncompliant
	if x == 1:
		return 1
	if x == 2:
		return 2
	var cb = func(): return 9
	if x == 3:
		return cb.call()
	return 4
func three(x):
	if x == 1:
		return 1
	if x == 2:
		return 2
	return 3
