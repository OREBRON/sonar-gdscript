signal used_by_name
signal used_directly
signal used_with_self
signal unused  # Noncompliant
@warning_ignore("unused_signal")
signal silenced
func f():
	emit_signal("used_by_name")
	used_directly.emit()
	self.used_with_self.connect(f)
