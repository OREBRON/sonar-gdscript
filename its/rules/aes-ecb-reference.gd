func f():
	var a = AESContext.MODE_ECB_ENCRYPT  # Noncompliant
	var b = AESContext.MODE_ECB_DECRYPT  # Noncompliant
	var c = AESContext.MODE_CBC_ENCRYPT
	print(a, b, c)
