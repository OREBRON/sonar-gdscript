@tool
class_name SyntaxDeclarations
extends Node
## Declarations, annotations and properties.

signal health_changed(old_value: int, new_value: int)
signal died

enum State { IDLE, RUNNING = 5, JUMPING, }
enum { ANON_A, ANON_B }

const SPEED: float = 10.0
const NAMES := ["a", "b"]

@export var health: int = 100
@export_range(1, 100, 1, "or_greater") var level: int = 1
@export_group("Movement")
@export var acceleration := 2.0
@onready var label: Label = $Label

static var instances: int = 0

var typed_array: Array[int] = [1, 2, 3]
var typed_dict: Dictionary[String, int] = {"a": 1}
var vector_dict: Dictionary[Vector2i, State] = {}

var shield: int = 0:
	set(value):
		health_changed.emit(shield, value)
		shield = clampi(value, 0, 100)
	get:
		return shield

var armor: int: get = get_armor, set = set_armor
var _armor := 0

var mana: int:
	get = get_mana
var _mana := 0


static func _static_init() -> void:
	instances = 0


func _init() -> void:
	instances += 1


func get_armor() -> int:
	return _armor


func set_armor(value: int) -> void:
	_armor = value


func get_mana() -> int:
	return _mana


func sum(first: int, second := 0, ...rest: Array) -> int:
	var total := first + second
	for value in rest:
		total += value
	return total


@warning_ignore("unused_parameter")
func unused(param: int) -> void:
	pass


class Inner extends RefCounted:
	var value := 1

	func double() -> int:
		return value * 2


@abstract class Shape extends RefCounted:
	@abstract func area() -> float

	func describe() -> String:
		return "area %f" % area()


class Square extends Shape:
	var side := 2.0

	func area() -> float:
		return side * side
