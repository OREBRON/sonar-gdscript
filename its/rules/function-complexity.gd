func eleven(a):  # Noncompliant
	if a == 0:
		pass
	if a == 1:
		pass
	if a == 2:
		pass
	if a == 3:
		pass
	if a == 4:
		pass
	if a == 5:
		pass
	if a == 6:
		pass
	if a == 7:
		pass
	if a == 8:
		pass
	if a == 9:
		pass
func ten(a):
	if a == 0:
		pass
	if a == 1:
		pass
	if a == 2:
		pass
	if a == 3:
		pass
	if a == 4:
		pass
	if a == 5:
		pass
	if a == 6:
		pass
	if a == 7:
		pass
	if a == 8:
		pass
func lambdas_count_alone(a):
	var cb = func(): return a if a else (a and a or a)
	cb.call()
