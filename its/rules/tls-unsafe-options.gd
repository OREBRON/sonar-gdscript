func f():
	var unsafe = TLSOptions.client_unsafe()  # Noncompliant
	var safe = TLSOptions.client()
	print(unsafe, safe)
