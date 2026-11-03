func f(x):
	match x:
		var y when y > 0:
			pass
		_:
			pass
		1:  # Noncompliant
			pass
		2:  # Noncompliant
			pass
