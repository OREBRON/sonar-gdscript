extends Node
var a = $A  # Noncompliant
var b := %B as Node2D  # Noncompliant
var c = get_node("C")  # Noncompliant
var d = self.get_node("D")  # Noncompliant
var e = $E.position
@onready var f = $F
static var g = $G
