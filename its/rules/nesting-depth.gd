func deep(items):
	if items:
		for item in items:
			while item:
				if item > 1:  # Noncompliant
					if item > 2:
						pass
				item -= 1
	else:
		match items:
			1:
				if items:
					pass
func lambdas_start_again(items):
	if items:
		for item in items:
			var cb = func():
				if item:
					if item:
						pass
			cb.call()
