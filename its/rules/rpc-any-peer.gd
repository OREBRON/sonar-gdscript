@rpc("any_peer", "call_local")
func hit(damage):  # Noncompliant
	print(damage)
@rpc("authority")
func sync_state():
	pass
@rpc
func default_mode():
	pass
