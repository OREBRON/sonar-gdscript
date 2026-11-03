func f(done, d):
	var a = done == true  # Noncompliant
	var b = false != done  # Noncompliant
	var c = d.get("k", false) == true
	var e = is_ready() == false  # Noncompliant
	print(a, b, c, e)
func is_ready():
	return true
