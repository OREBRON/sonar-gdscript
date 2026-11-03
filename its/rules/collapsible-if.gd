func f(a, b):
	if a:
		if b:  # Noncompliant
			print(a)
	if a:
		if b:
			print(a)
		else:
			print(b)
	if a:
		if b:
			print(a)
	else:
		print(b)
	if a:
		print(a)
		if b:
			print(b)
