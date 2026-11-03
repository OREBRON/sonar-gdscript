var health = 1
func heal():
	pass
func f(health):  # Noncompliant
	var heal = 2  # Noncompliant
	for value in health:
		print(value, heal)
class Inner:
	func g():
		var health = 3
		print(health)
