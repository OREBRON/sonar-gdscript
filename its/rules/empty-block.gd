# Noncompliant@+1
func stub():
	pass
func explained():
	pass  # Overridden by subclasses.
func documented():
	# Nothing to clean up.
	pass
func blocks(x):
	# Noncompliant@+1
	if x:
		pass
	else:
		print(x)
	# Noncompliant@+1
	while x:
		pass
	match x:
		1:
			pass
	var cb = func(): pass
	cb.call()
