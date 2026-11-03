func f(used, unused, _ignored):  # Noncompliant
	var cb = func(lambda_unused): return used  # Noncompliant
	cb.call(1)
@warning_ignore("unused_parameter")
func g(silenced):
	pass
var setter_only:
	set(value):
		pass
