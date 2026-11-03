var _used
var _unused  # Noncompliant
var public_unused
@warning_ignore("unused_private_class_variable")
var _silenced
func f():
	print(self._used)
