func f(x):
	if x == 1:
		print(x)
		return
	elif x == 2:  # Noncompliant
		print(x)
		return
	else:
		print(x)
	match x:
		1:
			print(x)
			return
		2:  # Noncompliant
			print(x)
			return
		3:
			pass
		4:
			pass
