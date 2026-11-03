func f(data):
	var a = bytes_to_var_with_objects(data)  # Noncompliant
	var b = Marshalls.base64_to_variant(data, true)  # Noncompliant
	var c = Marshalls.base64_to_variant(data)
	var d = bytes_to_var(data)
	print(a, b, c, d)
