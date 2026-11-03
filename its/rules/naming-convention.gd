class_name my_fixture  # Noncompliant
signal HealthChanged  # Noncompliant
signal health_changed
const MAX_SPEED = 1
const Bullet = preload("res://its_bullet.gd")
const lower_constant = 2  # Noncompliant
enum State {IDLE, Running = 2}  # Noncompliant
enum lower_enum {A}  # Noncompliant
var _private_value
var camelCase  # Noncompliant
func _on_Button_pressed(otherValue, _unused):  # Noncompliant 2
	for Index in 3:  # Noncompliant
		print(otherValue, Index)
func good_name(good_parameter):
	print(good_parameter)
class inner_class:  # Noncompliant
	pass
