extends Node
## Literal forms accepted by the GDScript tokenizer.

const HEX := 0xFF_FF
const BIN := 0b1010_0101
const BIG := 1_000_000
const SCI := 1.5e-3
const SCI_UPPER := 2E+10
const LEADING_DOT := .5
const NEGATIVE := -42

var double_quoted := "a \"quoted\" # not a comment"
var single_quoted := 'it\'s'
var triple_double := """first line
second line with "quotes" and # hash
"""
var triple_single := '''x
y'''
var raw := r"C:\path\to\file"
var raw_quote := r"\tchar=\"\\t\""
var raw_triple := r"""raw \n
multi"""
var string_name := &"action_name"
var node_path := ^"Node/Label"
var unicode := "çğıöşü 😀"
var escapes := "\t\n\\\u00e7"


func node_shorthands() -> void:
	var child = $Child
	var grandchild = $Child/GrandChild
	var spaced = $"Path With Spaces/Inner"
	var unique = %UniqueChild
	var modulo = 7 % 3
	var tight_modulo = 7 %3
	var typed_modulo: int = modulo % tight_modulo
	print(child, grandchild, spaced, unique, modulo, tight_modulo, typed_modulo)
