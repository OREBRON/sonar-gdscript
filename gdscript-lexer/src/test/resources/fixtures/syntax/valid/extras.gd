@icon("res://icon.svg")
extends "res://statements.gd"


func membership(a: int) -> bool:
	return a not in [1, 2]
